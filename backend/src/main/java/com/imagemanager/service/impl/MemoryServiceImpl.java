package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.entity.KnowledgeCard;
import com.imagemanager.entity.KnowledgeDomain;
import com.imagemanager.repository.KnowledgeCardRepository;
import com.imagemanager.repository.KnowledgeDomainRepository;
import com.imagemanager.repository.KnowledgeEmbeddingRepository;
import com.imagemanager.service.MemoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    public List<KnowledgeDomain> getAllDomains() {
        return domainRepository.findAll();
    }

    @Override
    public KnowledgeDomain getDomainByCode(String code) {
        return domainRepository.findByCode(code).orElse(null);
    }

    @Override
    @Transactional
    public KnowledgeCard createCard(KnowledgeCard card, String userId) {
        // 绑定用户
        card.setUserId(userId);
        if (card.getStatus() == null) card.setStatus("published");
        if (card.getReviewStatus() == null) card.setReviewStatus("pending");
        if (card.getConfidence() == null) card.setConfidence("medium");

        // 1. 保存卡片
        card = cardRepository.save(card);

        // 2. 生成向量嵌入
        try {
            String text = card.getTitle() + "\n\n" + card.getContent();
            float[] embedding = getEmbedding(text);
            if (embedding != null && embedding.length > 0) {
                String vectorStr = arrayToVectorString(embedding);
                jdbcTemplate.update(
                        "INSERT INTO knowledge_embeddings (id, card_id, embedding, embedding_model, chunk_text, chunk_index, created_at) " +
                                "VALUES (gen_random_uuid(), ?::uuid, ?::vector, ?, ?, 0, NOW())",
                        card.getId().toString(), vectorStr, "text-embedding-v3", text
                );
                log.info("知识卡片向量化成功, cardId={}", card.getId());
            }
        } catch (Exception e) {
            log.error("知识卡片向量化失败, cardId={}: {}", card.getId(), e.getMessage());
        }

        return card;
    }

    @Override
    public List<KnowledgeCard> getCardsByDomain(String domainCode, String userId) {
        return cardRepository.findByDomainCodeAndUserId(domainCode, userId);
    }

    @Override
    @Transactional
    public void deleteCard(String cardId, String userId) {
        // 先验证卡片属于当前用户
        Optional<KnowledgeCard> cardOpt = cardRepository.findById(UUID.fromString(cardId));
        if (cardOpt.isEmpty()) {
            throw new RuntimeException("卡片不存在");
        }
        if (!userId.equals(cardOpt.get().getUserId())) {
            throw new RuntimeException("无权删除此卡片");
        }
        // 删除向量
        jdbcTemplate.update("DELETE FROM knowledge_embeddings WHERE card_id = ?::uuid", cardId);
        // 删除卡片
        cardRepository.deleteById(UUID.fromString(cardId));
        log.info("删除知识卡片, cardId={}", cardId);
    }

    @Override
    public List<MemorySearchResult> search(String query, String domainCode, double minScore, int limit, String userId) {
        try {
            float[] queryEmbedding = getEmbedding(query);
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                return Collections.emptyList();
            }

            String vectorStr = arrayToVectorString(queryEmbedding);

            // 按用户隔离的向量检索
            String sql = """
                SELECT c.id, c.title, c.content, c.domain_code, c.tags, c.product_code,
                       c.source, c.confidence, c.created_by, c.user_id, c.created_at, e.chunk_text,
                       1 - (e.embedding <=> ?::vector) AS score
                FROM knowledge_embeddings e
                JOIN knowledge_cards c ON e.card_id = c.id
                WHERE c.status = 'published'
                AND c.user_id = ?
                AND (? IS NULL OR c.domain_code = ?)
                AND 1 - (e.embedding <=> ?::vector) >= ?
                ORDER BY e.embedding <=> ?::vector
                LIMIT ?
                """;

            return jdbcTemplate.query(sql,
                    (rs, rowNum) -> {
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
                    },
                    vectorStr, userId, domainCode, domainCode, vectorStr, minScore, vectorStr, limit
            );
        } catch (Exception e) {
            log.error("语义检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public SseEmitter chat(String message, String sessionId, String userId) {
        SseEmitter emitter = new SseEmitter(120000L);

        executorService.execute(() -> {
            try {
                // 1. 语义检索(用户隔离)
                List<MemorySearchResult> searchResults = search(message, null, 0.3, 5, userId);

                // 2. 发送来源
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

                // 3. 构建提示词
                StringBuilder context = new StringBuilder();
                context.append("你是盈云产品智能中台的AI助手。请基于以下知识卡片回答用户问题。\n\n");
                context.append("## 相关知识卡片：\n");
                for (int i = 0; i < searchResults.size(); i++) {
                    MemorySearchResult r = searchResults.get(i);
                    context.append(String.format("### 卡片%d [%s] %s\n%s\n置信度: %s | 来源: %s\n\n",
                            i + 1, r.getDomainName(), r.getTitle(), r.getContent(),
                            r.getConfidence(), r.getSource() != null ? r.getSource() : "未知"));
                }
                context.append("\n请基于以上知识回答用户问题。如果知识卡片中没有相关信息，请说明。回答时标注引用来源。\n");
                context.append("用户问题: ").append(message);

                // 4. 流式调用MiniMax LLM
                streamChat(emitter, context.toString());

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

    // ========== Embedding API (Coze) ==========

    private float[] getEmbedding(String text) {
        try {
            String apiKey = System.getenv("COZE_API_TOKEN");
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = aiApiKey;
            }
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("未配置AI API密钥, 跳过向量化");
                return null;
            }

            String url = aiBaseUrl + "/v3/embeddings";
            Map<String, Object> body = new HashMap<>();
            body.put("model", "text-embedding-v3");
            body.put("input", Map.of("text", text));
            body.put("dimensions", 1024);

            String response = doPost(url, objectMapper.writeValueAsString(body), apiKey);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                JsonNode embeddingNode = root.get("data").get(0).get("embedding");
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }
                return embedding;
            }

            log.warn("Embedding API返回异常: {}", response);
            return null;
        } catch (Exception e) {
            log.error("获取Embedding失败: {}", e.getMessage());
            return null;
        }
    }

    // ========== LLM流式对话 (MiniMax Anthropic API) ==========

    private void streamChat(SseEmitter emitter, String prompt) {
        try {
            String apiKey = minimaxApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("MINIMAX_API_KEY");
            }
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("未配置MiniMax API密钥");
            }

            // Anthropic兼容格式请求体
            Map<String, Object> body = new HashMap<>();
            body.put("model", minimaxModel);
            body.put("max_tokens", 4096);
            body.put("stream", true);
            body.put("system", "你是盈云产品智能中台的AI助手。请基于提供的知识卡片回答用户问题，标注引用来源。如果知识卡片中没有相关信息，请明确说明。回答使用中文。");
            body.put("messages", List.of(Map.of(
                    "role", "user",
                    "content", prompt
            )));

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

    // ========== 工具方法 ==========

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
