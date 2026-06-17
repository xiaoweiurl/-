package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.entity.KnowledgeCard;
import com.imagemanager.entity.KnowledgeDomain;
import com.imagemanager.entity.KnowledgeDocument;
import com.imagemanager.repository.KnowledgeCardRepository;
import com.imagemanager.repository.KnowledgeDocumentRepository;
import com.imagemanager.repository.KnowledgeDomainRepository;
import com.imagemanager.repository.KnowledgeEmbeddingRepository;
import com.imagemanager.service.DocumentParserService;
import com.imagemanager.service.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class MemoryServiceImpl implements MemoryService {

    @Autowired
    private KnowledgeDomainRepository domainRepository;

    @Autowired
    private KnowledgeCardRepository cardRepository;

    @Autowired
    private KnowledgeEmbeddingRepository embeddingRepository;

    @Autowired
    private KnowledgeDocumentRepository documentRepository;

    @Autowired
    private DocumentParserService documentParserService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.ai.api-key:}")
    private String aiApiKey;

    @Value("${app.ai.base-url:https://api.coze.cn}")
    private String aiBaseUrl;

    @Value("${app.minimax.api-key:}")
    private String minimaxApiKey;

    @Value("${app.minimax.base-url:https://api.minimaxi.com/anthropic/v1/messages}")
    private String minimaxBaseUrl;

    @Value("${app.minimax.model:MiniMax-M3}")
    private String minimaxModel;

    @Value("${app.minimax.embedding.base-url:https://api.minimaxi.com/v1/embeddings}")
    private String minimaxEmbeddingUrl;

    @Value("${app.minimax.embedding.model:embo-01}")
    private String minimaxEmbeddingModel;

    @Value("${app.minimax.embedding.group-id:}")
    private String minimaxGroupId;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // ========== 知识域 ==========

    @Override
    public List<KnowledgeDomain> getAllDomains(String company) {
        return domainRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    @Override
    public KnowledgeDomain getDomainByCode(String code, String company) {
        return domainRepository.findByCodeAndCompany(code, company).orElse(null);
    }

    // ========== 知识卡片 ==========

    @Override
    @Transactional
    public KnowledgeCard createCard(KnowledgeCard card, String userId, String company) {
        card.setUserId(userId);
        card.setCreatedBy(userId);
        card.setCompany(company);
        if (card.getStatus() == null) card.setStatus("published");
        if (card.getReviewStatus() == null) card.setReviewStatus("pending");
        if (card.getConfidence() == null) card.setConfidence("medium");

        card = cardRepository.save(card);

        try {
            String text = card.getTitle() + "\n\n" + card.getContent();
            float[] embedding = getEmbedding(text);
            if (embedding != null && embedding.length > 0) {
                String vectorStr = arrayToVectorString(embedding);
                jdbcTemplate.update(
                        "INSERT INTO knowledge_embeddings (id, card_id, embedding, embedding_model, chunk_text, chunk_index, company, created_at) " +
                                "VALUES (gen_random_uuid(), ?::uuid, CAST(? AS vector), ?, ?, 0, ?, NOW())",
                        card.getId().toString(), vectorStr, "embo-01", text, company
                );
                log.info("知识卡片向量化成功, cardId={}", card.getId());
            }
        } catch (Exception e) {
            log.error("知识卡片向量化失败, cardId={}: {}", card.getId(), e.getMessage());
        }

        return card;
    }

    @Override
    public List<KnowledgeCard> getCardsByDomain(String domainCode, String company, String userId) {
        return cardRepository.findByCompanyAndDomainCodeAndUserId(company, domainCode, userId);
    }

    @Override
    public List<KnowledgeCard> searchCards(String keyword, String company, String userId) {
        return cardRepository.searchByKeywordAndCompany(company, userId, keyword);
    }

    @Override
    @Transactional
    public void deleteCard(String cardId, String company, String userId) {
        Optional<KnowledgeCard> cardOpt = cardRepository.findById(UUID.fromString(cardId));
        if (cardOpt.isEmpty()) {
            throw new RuntimeException("卡片不存在");
        }
        KnowledgeCard card = cardOpt.get();
        if (!company.equals(card.getCompany())) {
            throw new RuntimeException("无权删除此卡片");
        }
        if (!userId.equals(card.getUserId())) {
            throw new RuntimeException("无权删除此卡片");
        }
        jdbcTemplate.update("DELETE FROM knowledge_embeddings WHERE card_id = ?::uuid", cardId);
        cardRepository.deleteById(UUID.fromString(cardId));
        log.info("删除知识卡片, cardId={}", cardId);
    }

    // ========== 文档上传 ==========

    @Override
    @Transactional
    public KnowledgeDocument uploadDocument(MultipartFile file, String domainCode, String userId, String company) {
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename != null ?
                originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase() : "";

        // 1. 保存文档记录
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setUserId(userId);
        doc.setFileName(originalFilename);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setDomainCode(domainCode);
        doc.setStatus("processing");
        doc = documentRepository.save(doc);

        // 2. 异步处理：解析→切片→向量化
        KnowledgeDocument finalDoc = doc;
        executorService.execute(() -> {
            try {
                // 解析文档
                String fullText = documentParserService.parseDocument(file);

                // 切片 (每片500字，重叠100字)
                List<String> chunks = documentParserService.chunkText(fullText, 500, 100);

                if (chunks.isEmpty()) {
                    finalDoc.setStatus("empty");
                    finalDoc.setErrorMessage("文档内容为空");
                    documentRepository.save(finalDoc);
                    return;
                }

                // 逐片创建卡片和向量
                int successCount = 0;
                for (int i = 0; i < chunks.size(); i++) {
                    String chunk = chunks.get(i);
                    try {
                        // 创建知识卡片
                        KnowledgeCard card = new KnowledgeCard();
                        card.setDomainCode(domainCode);
                        card.setTitle(originalFilename + " (片段" + (i + 1) + "/" + chunks.size() + ")");
                        card.setContent(chunk);
                        card.setUserId(userId);
                        card.setCreatedBy(userId);
                        card.setCompany(company);
                        card.setSource(originalFilename);
                        card.setStatus("published");
                        card.setReviewStatus("approved");
                        card.setConfidence("medium");
                        card.setTags(new String[]{ext, "上传文档"});
                        card = cardRepository.save(card);

                        // 向量化
                        float[] embedding = getEmbedding(chunk);
                        if (embedding != null && embedding.length > 0) {
                            String vectorStr = arrayToVectorString(embedding);
                            jdbcTemplate.update(
                                    "INSERT INTO knowledge_embeddings (id, card_id, embedding, embedding_model, chunk_text, chunk_index, company, created_at) " +
                                            "VALUES (gen_random_uuid(), ?::uuid, CAST(? AS vector), ?, ?, ?, ?, NOW())",
                                    card.getId().toString(), vectorStr, "embo-01", chunk, i, company
                            );
                            successCount++;
                        }
                    } catch (Exception e) {
                        log.error("文档切片{}向量化失败: {}", i, e.getMessage());
                    }
                }

                finalDoc.setStatus("completed");
                finalDoc.setChunkCount(successCount);
                documentRepository.save(finalDoc);
                log.info("文档上传处理完成: {}, 成功切片: {}/{}", originalFilename, successCount, chunks.size());

            } catch (Exception e) {
                log.error("文档处理失败: {}", e.getMessage());
                finalDoc.setStatus("failed");
                finalDoc.setErrorMessage(e.getMessage());
                documentRepository.save(finalDoc);
            }
        });

        return doc;
    }

    @Override
    public List<KnowledgeDocument> getDocuments(String company, String userId) {
        return documentRepository.findByCompanyAndUserIdOrderByCreatedAtDesc(company, userId);
    }

    @Override
    @Transactional
    public void deleteDocument(String docId, String company, String userId) {
        Optional<KnowledgeDocument> docOpt = documentRepository.findById(UUID.fromString(docId));
        if (docOpt.isEmpty()) {
            throw new RuntimeException("文档不存在");
        }
        KnowledgeDocument doc = docOpt.get();
        if (!company.equals(doc.getCompany())) {
            throw new RuntimeException("无权删除此文档");
        }
        if (!userId.equals(doc.getUserId())) {
            throw new RuntimeException("无权删除此文档");
        }

        String fileName = docOpt.get().getFileName();

        // 删除该文档产生的所有卡片和向量
        List<KnowledgeCard> cards = cardRepository.findByUserIdAndSource(userId, fileName);
        for (KnowledgeCard card : cards) {
            jdbcTemplate.update("DELETE FROM knowledge_embeddings WHERE card_id = ?::uuid", card.getId().toString());
            cardRepository.delete(card);
        }

        documentRepository.deleteById(UUID.fromString(docId));
        log.info("删除文档及相关卡片: {}, 卡片数: {}", fileName, cards.size());
    }

    // ========== 语义检索 ==========

    @Override
    public List<MemorySearchResult> search(String query, String domainCode, double minScore, int limit, String company, String userId) {
        try {
            float[] queryEmbedding = getEmbedding(query);
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                return Collections.emptyList();
            }

            String vectorStr = arrayToVectorString(queryEmbedding);

            // 向量直接拼接到 SQL（内部生成，无注入风险），普通参数用 PreparedStatement 绑定
            String sql = "SELECT c.id, c.title, c.content, c.domain_code, c.tags, c.product_code, " +
                    "c.source, c.confidence, c.created_by, c.user_id, c.created_at, e.chunk_text, " +
                    "1 - (e.embedding <=> '" + vectorStr + "'::vector) AS score " +
                    "FROM knowledge_embeddings e " +
                    "JOIN knowledge_cards c ON e.card_id = c.id " +
                    "WHERE c.status = 'published' " +
                    "AND (e.company = ? OR e.company IS NULL) " +
                    "AND (c.company = ? OR c.company IS NULL) " +
                    "AND (? IS NULL OR c.domain_code = ?) " +
                    "AND 1 - (e.embedding <=> '" + vectorStr + "'::vector) >= ? " +
                    "ORDER BY e.embedding <=> '" + vectorStr + "'::vector " +
                    "LIMIT ?";

            return jdbcTemplate.query(sql, (PreparedStatement ps) -> {
                ps.setString(1, company);
                ps.setString(2, company);
                if (domainCode == null) {
                    ps.setNull(3, java.sql.Types.VARCHAR);
                    ps.setNull(4, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(3, domainCode);
                    ps.setString(4, domainCode);
                }
                ps.setDouble(5, minScore);
                ps.setInt(6, limit);
            }, (rs, rowNum) -> {
                KnowledgeDomain domain = domainRepository.findByCode(rs.getString("domain_code")).orElse(null);
                return MemorySearchResult.builder()
                        .id(UUID.fromString(rs.getString("id")))
                        .title(rs.getString("title"))
                        .content(rs.getString("content"))
                        .domainCode(rs.getString("domain_code"))
                        .domainName(domain != null ? domain.getName() : "")
                        .domainIcon(domain != null ? domain.getIcon() : "")
                        .domainColor(domain != null ? domain.getColor() : "")
                        .productCode(rs.getString("product_code"))
                        .source(rs.getString("source"))
                        .confidence(rs.getString("confidence"))
                        .createdBy(rs.getString("created_by"))
                        .createdAt(rs.getTimestamp("created_at") != null ?
                                rs.getTimestamp("created_at").toLocalDateTime() : null)
                        .chunkText(rs.getString("chunk_text"))
                        .score(rs.getDouble("score"))
                        .build();
            });
        } catch (Exception e) {
            log.error("语义检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ========== AI对话(含上下文) ==========

    @Override
    public SseEmitter chat(String message, String sessionId, String company, String userId) {
        SseEmitter emitter = new SseEmitter(600000L);

        executorService.execute(() -> {
            try {
                // 1. 加载上下文历史
                List<Map<String, Object>> history = loadChatHistory(sessionId);

                // 2. 语义检索(用户隔离)
                List<MemorySearchResult> searchResults = search(message, null, 0.3, 5, company, userId);

                // 3. 发送来源
                List<Map<String, Object>> sources = new ArrayList<>();
                for (MemorySearchResult r : searchResults) {
                    sources.add(Map.of(
                            "id", r.getId().toString(),
                            "title", r.getTitle() != null ? r.getTitle() : "",
                            "domain", r.getDomainName() != null ? r.getDomainName() : "",
                            "score", r.getScore() != null ? r.getScore() : 0
                    ));
                }
                emitter.send(SseEmitter.event().name("message").data(
                        objectMapper.writeValueAsString(Map.of("type", "sources", "sources", sources))
                ));

                // 4. 构建知识上下文
                StringBuilder knowledgeContext = new StringBuilder();
                if (!searchResults.isEmpty()) {
                    knowledgeContext.append("## 相关知识卡片：\n");
                    for (int i = 0; i < searchResults.size(); i++) {
                        MemorySearchResult r = searchResults.get(i);
                        knowledgeContext.append(String.format("### 卡片%d [%s] %s\n%s\n置信度: %s | 来源: %s\n\n",
                                i + 1, r.getDomainName(), r.getTitle(), r.getContent(),
                                r.getConfidence(), r.getSource() != null ? r.getSource() : "未知"));
                    }
                }

                // 5. 构建messages（含历史上下文）
                List<Map<String, Object>> messages = new ArrayList<>();

                // System prompt
                messages.add(Map.of("role", "system", "content",
                        "你是盈云产品智能中台的AI助手，拥有企业记忆库知识卡片的访问权限。" +
                        "回答问题时优先基于检索到的知识卡片内容，并标注引用来源。" +
                        "如果知识库中没有相关信息，你可以基于自身知识回答，但要说明信息来源。"));

                // 加入历史对话（最近10轮）
                int startIdx = Math.max(0, history.size() - 20);
                for (int i = startIdx; i < history.size(); i++) {
                    messages.add(history.get(i));
                }

                // 当前用户消息（带知识上下文）
                String userContent = message;
                if (!knowledgeContext.isEmpty()) {
                    userContent = knowledgeContext.toString() + "\n---\n用户问题: " + message +
                            "\n\n请基于以上知识回答用户问题，并标注引用来源。";
                } else {
                    userContent = "用户问题: " + message +
                            "\n\n(记忆库中未检索到相关内容，请基于自身知识回答。)";
                }
                messages.add(Map.of("role", "user", "content", userContent));

                // 6. 保存用户消息
                saveChatMessage(sessionId, userId, "user", message);

                // 7. 流式调用MiniMax LLM（含上下文）
                StringBuilder fullResponse = new StringBuilder();
                streamChatWithContext(emitter, messages, fullResponse);

                // 8. 保存AI回复
                saveChatMessage(sessionId, userId, "assistant", fullResponse.toString());

                emitter.complete();
            } catch (Exception e) {
                log.error("AI对话失败: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("message").data(
                            objectMapper.writeValueAsString(Map.of("type", "error", "content", "AI对话失败: " + e.getMessage()))
                    ));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    public List<Map<String, Object>> getChatHistory(String sessionId, String company, String userId) {
        String sql = "SELECT role, content, created_at FROM knowledge_chat_history " +
                "WHERE session_id = ?::uuid AND company = ? AND user_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", rs.getString("role"));
                    msg.put("content", rs.getString("content"));
                    msg.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    return msg;
                },
                sessionId, company, userId
        );
    }

    @Override
    @Transactional
    public void clearChatHistory(String sessionId, String company, String userId) {
        jdbcTemplate.update(
                "DELETE FROM knowledge_chat_history WHERE session_id = ?::uuid AND company = ? AND user_id = ?",
                sessionId, company, userId
        );
    }

    // ========== 私有方法 ==========

    private List<Map<String, Object>> loadChatHistory(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return Collections.emptyList();

        try {
            String sql = "SELECT role, content FROM knowledge_chat_history " +
                    "WHERE session_id = ?::uuid ORDER BY created_at ASC LIMIT 40";
            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("role", rs.getString("role"));
                        map.put("content", rs.getString("content"));
                        return map;
                    },
                    sessionId
            );
        } catch (Exception e) {
            log.warn("加载对话历史失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void saveChatMessage(String sessionId, String userId, String role, String content) {
        if (sessionId == null || sessionId.isEmpty()) return;
        try {
            jdbcTemplate.update(
                    "INSERT INTO knowledge_chat_history (id, session_id, role, content, user_id, created_at) " +
                            "VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, NOW())",
                    sessionId, role, content, userId
            );
        } catch (Exception e) {
            log.warn("保存对话消息失败: {}", e.getMessage());
        }
    }

    private void streamChatWithContext(SseEmitter emitter, List<Map<String, Object>> messages,
                                        StringBuilder fullResponse) {
        try {
            String apiKey = minimaxApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("MINIMAX_API_KEY");
            }
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("未配置MiniMax API密钥");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", minimaxModel);
            body.put("max_tokens", 4096);
            body.put("stream", true);
            body.put("system", "你是盈云产品智能中台的AI助手。请基于提供的知识卡片回答用户问题，标注引用来源。如果知识卡片中没有相关信息，请明确说明。回答使用中文。保持对话连贯性，参考上下文历史。");
            body.put("messages", messages);

            HttpURLConnection conn = (HttpURLConnection) URI.create(minimaxBaseUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(120000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
                StringBuilder errorBody = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorBody.append(line);
                }
                errorReader.close();
                throw new RuntimeException("MiniMax API返回错误 " + responseCode + ": " + errorBody);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (data.isEmpty()) continue;

                        try {
                            JsonNode node = objectMapper.readTree(data);
                            String eventType = node.path("type").asText("");

                            switch (eventType) {
                                case "content_block_delta":
                                    JsonNode delta = node.path("delta");
                                    String deltaType = delta.path("type").asText("");
                                    if ("text_delta".equals(deltaType)) {
                                        String text = delta.path("text").asText("");
                                        if (!text.isEmpty()) {
                                            fullResponse.append(text);
                                            emitter.send(SseEmitter.event().name("message").data(
                                                    objectMapper.writeValueAsString(Map.of("type", "content", "content", text))
                                            ));
                                        }
                                    }
                                    break;
                                case "message_stop":
                                    emitter.send(SseEmitter.event().name("message").data(
                                            objectMapper.writeValueAsString(Map.of("type", "done"))
                                    ));
                                    return;
                                case "error":
                                    String errorMsg = node.path("error").path("message").asText("未知错误");
                                    throw new RuntimeException("MiniMax流式错误: " + errorMsg);
                            }
                        } catch (Exception parseEx) {
                            if (parseEx instanceof RuntimeException) throw parseEx;
                            log.debug("解析SSE行失败: {}", data);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("MiniMax流式对话失败: {}", e.getMessage());
            throw new RuntimeException("流式对话失败: " + e.getMessage());
        }
    }

    // ========== Embedding API (MiniMax) ==========

    private float[] getEmbedding(String text) {
        try {
            String apiKey = minimaxApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("MINIMAX_API_KEY");
            }
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("未配置MiniMax API密钥, 跳过向量化");
                return null;
            }

            String url = minimaxEmbeddingUrl;
            Map<String, Object> body = new HashMap<>();
            body.put("model", minimaxEmbeddingModel);

            String response;
            JsonNode root;

            // 优先尝试 MiniMax 原生格式 (Token Plan Key 实际走此格式)
            body.put("texts", new String[]{text});
            body.put("type", "db");
            String jsonBody = objectMapper.writeValueAsString(body);
            log.info("MiniMax Embedding请求体: {}", jsonBody);
            response = doPost(url, jsonBody, apiKey);
            log.info("MiniMax Embedding响应: {}", response);
            root = objectMapper.readTree(response);

            // 原生格式: vectors[0]
            if (root.has("vectors") && root.get("vectors").isArray() && root.get("vectors").size() > 0) {
                JsonNode embeddingNode = root.get("vectors").get(0);
                if (embeddingNode != null && embeddingNode.isArray()) {
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    log.info("MiniMax Embedding成功 (原生格式), 维度: {}", embedding.length);
                    return embedding;
                }
            }

            // 兜底尝试 OpenAI 兼容格式
            body.remove("texts");
            body.put("input", text);
            response = doPost(url, objectMapper.writeValueAsString(body), apiKey);
            root = objectMapper.readTree(response);
            if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                JsonNode embeddingNode = root.get("data").get(0).get("embedding");
                if (embeddingNode != null && embeddingNode.isArray()) {
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    log.info("MiniMax Embedding成功 (OpenAI兼容格式), 维度: {}", embedding.length);
                    return embedding;
                }
            }

            log.warn("MiniMax Embedding返回异常: {}", response);
            return null;
        } catch (Exception e) {
            log.error("获取Embedding失败: {}", e.getMessage());
            return null;
        }
    }

    private String doPost(String url, String jsonBody, String apiKey) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private String arrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
