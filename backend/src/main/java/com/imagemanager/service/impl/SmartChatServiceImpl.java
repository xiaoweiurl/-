package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.service.KnowledgeBaseService;
import com.imagemanager.service.MemoryService;
import com.imagemanager.service.SmartChatService;
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

/**
 * 智能对话服务实现 - 双库检索(知识库+记忆库) + MiniMax流式对话
 *
 * 检索流程:
 * 1. 记忆库检索: PostgreSQL向量搜索(MemoryService.search)
 * 2. 知识库检索: 同样使用PostgreSQL向量搜索(MemoryService.search，不限定domain)
 * 3. 合并去重结果作为上下文
 * 4. 调MiniMax API流式对话
 */
@Slf4j
@Service
public class SmartChatServiceImpl implements SmartChatService {

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.minimax.api-key:}")
    private String minimaxApiKey;

    @Value("${app.minimax.base-url:https://api.minimaxi.com/anthropic/v1/messages}")
    private String minimaxBaseUrl;

    @Value("${app.minimax.model:MiniMax-M3}")
    private String minimaxModel;

    @Override
    public SseEmitter smartChat(String message, String userId, String company) {
        log.info("智能对话: message='{}', userId='{}', company='{}'", message, userId, company);
        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时

        new Thread(() -> {
            try {
                // 1. 加载历史对话（按userId+company绑定）
                List<Map<String, Object>> history = getChatHistory(userId, company);

                // 2. 双库检索
                // 2a. 记忆库检索(PostgreSQL向量)
                List<MemorySearchResult> memoryResults = Collections.emptyList();
                try {
                    memoryResults = memoryService.search(message, null, 0.2, 5, company, userId);
                    log.info("记忆库检索到 {} 条结果", memoryResults.size());
                } catch (Exception e) {
                    log.warn("记忆库检索异常: {}", e.getMessage());
                }

                // 2b. 知识库检索(Coze SDK via Next.js)
                List<Map<String, Object>> knowledgeResults = Collections.emptyList();
                try {
                    knowledgeResults = searchKnowledgeBase(message, userId, company);
                    log.info("知识库检索到 {} 条结果", knowledgeResults.size());
                } catch (Exception e) {
                    log.warn("知识库检索异常: {}", e.getMessage());
                }

                // 2c. 图片搜索(当用户意图涉及找图时)
                List<Map<String, Object>> imageResults = Collections.emptyList();
                if (isImageSearchIntent(message)) {
                    try {
                        imageResults = searchImages(message, userId);
                        log.info("图片搜索匹配到 {} 条结果", imageResults.size());
                    } catch (Exception e) {
                        log.warn("图片搜索异常: {}", e.getMessage());
                    }
                }

                // 3. 发送来源信息
                List<Map<String, Object>> sources = new ArrayList<>();

                // 记忆库来源
                for (MemorySearchResult r : memoryResults) {
                    sources.add(Map.of(
                            "source", "memory",
                            "id", r.getId().toString(),
                            "title", r.getTitle() != null ? r.getTitle() : "",
                            "domain", r.getDomainName() != null ? r.getDomainName() : "",
                            "score", r.getScore() != null ? r.getScore() : 0
                    ));
                }

                // 知识库来源
                for (Map<String, Object> r : knowledgeResults) {
                    sources.add(Map.of(
                            "source", "knowledge",
                            "content", r.getOrDefault("content", "").toString(),
                            "score", r.getOrDefault("score", 0)
                    ));
                }

                emitter.send(SseEmitter.event().name("message").data(
                        objectMapper.writeValueAsString(Map.of("type", "sources", "sources", sources))
                ));

                // 3b. 发送图片结果(如果有)
                if (!imageResults.isEmpty()) {
                    emitter.send(SseEmitter.event().name("message").data(
                            objectMapper.writeValueAsString(Map.of("type", "images", "images", imageResults))
                    ));
                }

                // 4. 构建知识上下文
                StringBuilder knowledgeContext = new StringBuilder();

                if (!memoryResults.isEmpty()) {
                    knowledgeContext.append("## 记忆库相关知识卡片：\n");
                    for (int i = 0; i < memoryResults.size(); i++) {
                        MemorySearchResult r = memoryResults.get(i);
                        String content = r.getContent();
                        if (content != null && content.length() > 300) content = content.substring(0, 300) + "...";
                        knowledgeContext.append(String.format("### 卡片%d [%s] %s\n%s\n置信度: %s | 来源: %s\n\n",
                                i + 1, r.getDomainName(), r.getTitle(), content,
                                r.getConfidence(), r.getSource() != null ? r.getSource() : "未知"));
                    }
                }

                if (!knowledgeResults.isEmpty()) {
                    knowledgeContext.append("## 知识库相关文档片段：\n");
                    for (int i = 0; i < knowledgeResults.size(); i++) {
                        Map<String, Object> r = knowledgeResults.get(i);
                        double score = ((Number) r.getOrDefault("score", 0)).doubleValue();
                        String content = r.getOrDefault("content", "").toString();
                        if (content.length() > 300) content = content.substring(0, 300) + "...";
                        knowledgeContext.append(String.format("### 片段%d (相关度: %.1f%%)\n%s\n\n",
                                i + 1, score * 100, content));
                    }
                }

                if (!imageResults.isEmpty()) {
                    knowledgeContext.append("## 图片库搜索结果：\n");
                    for (int i = 0; i < imageResults.size(); i++) {
                        Map<String, Object> product = imageResults.get(i);
                        String productName = product.getOrDefault("productName", "").toString();
                        String albumName = product.getOrDefault("albumName", "").toString();
                        knowledgeContext.append(String.format("产品%d: %s (相册: %s)\n", i + 1, productName, albumName));

                        @SuppressWarnings("unchecked")
                        Map<String, Object> mainImage = (Map<String, Object>) product.get("mainImage");
                        if (mainImage != null) {
                            knowledgeContext.append(String.format("  [主图] %s (URL: %s)\n",
                                    mainImage.getOrDefault("title", ""), mainImage.getOrDefault("url", "")));
                        }

                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> detailImages = (List<Map<String, Object>>) product.get("detailImages");
                        if (detailImages != null && !detailImages.isEmpty()) {
                            knowledgeContext.append(String.format("  [详情图 %d张] ", detailImages.size()));
                            for (int j = 0; j < detailImages.size() && j < 5; j++) {
                                Map<String, Object> di = detailImages.get(j);
                                knowledgeContext.append(String.format("%s ", di.getOrDefault("title", "")));
                            }
                            knowledgeContext.append("\n");
                        }
                    }
                    knowledgeContext.append("\n用户请求查找图片，请基于以上图片列表组织回答，简要说明找到了哪些产品及其图片。\n");
                }

                // 5. 构建messages(含历史上下文)
                List<Map<String, Object>> messages = new ArrayList<>();

                // System prompt: 定义AI角色和行为
                messages.add(Map.of("role", "system", "content",
                        "你是盈云产品智能中台的AI助手。你拥有企业知识库（记忆库和知识库）的访问权限。" +
                        "回答问题时优先基于检索到的知识库内容，并标注引用来源。" +
                        "如果知识库中没有相关信息，你可以基于自身知识回答，但要说明信息来源。" +
                        "保持专业、简洁、有帮助的回答风格。"));

                // 加入历史对话(最近10轮)
                int startIdx = Math.max(0, history.size() - 5);
                for (int i = startIdx; i < history.size(); i++) {
                    messages.add(history.get(i));
                }

                // 当前用户消息(带知识上下文)
                String userContent = message;
                if (!knowledgeContext.isEmpty()) {
                    userContent = knowledgeContext.toString() + "\n---\n用户问题: " + message +
                            "\n\n请基于以上知识回答用户问题，并标注引用来源和出处(记忆库/知识库)。";
                } else {
                    userContent = "用户问题: " + message +
                            "\n\n(知识库中未检索到相关内容，请基于自身知识回答。)";
                }
                messages.add(Map.of("role", "user", "content", userContent));

                // 6. 保存用户消息
                saveChatMessage(userId, userId, "user", message, company);

                // 7. 流式调用MiniMax
                StringBuilder fullResponse = new StringBuilder();
                streamChat(emitter, messages, fullResponse);

                // 8. 保存AI回复
                saveChatMessage(userId, userId, "assistant", fullResponse.toString(), company);

                emitter.complete();
            } catch (Exception e) {
                log.error("智能对话失败: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event().name("message").data(
                            objectMapper.writeValueAsString(Map.of("type", "error", "content", "AI对话失败: " + e.getMessage()))
                    ));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    @Override
    public List<Map<String, Object>> getChatHistory(String userId, String company) {
        // 按userId+company查询最近10轮对话（20条消息：10个user + 10个assistant）
        String sql = "SELECT role, content, created_at FROM smart_chat_history " +
                "WHERE user_id = ? AND (company = ? OR company IS NULL) " +
                "ORDER BY created_at DESC LIMIT 20";
        List<Map<String, Object>> results = jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", rs.getString("role"));
                    msg.put("content", rs.getString("content"));
                    msg.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    return msg;
                },
                userId, company
        );
        // 反转回时间正序
        Collections.reverse(results);
        return results;
    }

    @Override
    @Transactional
    public void clearChatHistory(String userId, String company) {
        jdbcTemplate.update(
                "DELETE FROM smart_chat_history WHERE user_id = ? AND (company = ? OR company IS NULL)",
                userId, company
        );
    }

    // ========== 私有方法 ==========

    /**
     * 判断用户意图是否为图片搜索
     */
    private boolean isImageSearchIntent(String message) {
        String lower = message.toLowerCase();
        // 精确匹配：仅当用户明确表达查找图片意图时才触发
        String[] strongPatterns = {
            "找图", "搜图", "查图", "看图",
            "找图片", "搜图片", "查图片",
            "图片搜索", "图片查询",
            "找照片", "搜照片",
            "主图", "详情图", "效果图",
            "产品图", "商品图",
            "图片库", "图片列表"
        };
        for (String kw : strongPatterns) {
            if (lower.contains(kw)) return true;
        }
        // 弱匹配：需要同时包含动作词+图片词
        String[] actionWords = {"找", "搜", "查", "看", "推荐", "展示", "显示"};
        String[] imageWords = {"图片", "照片", "相册"};
        boolean hasAction = false;
        boolean hasImage = false;
        for (String a : actionWords) { if (lower.contains(a)) { hasAction = true; break; } }
        for (String i : imageWords) { if (lower.contains(i)) { hasImage = true; break; } }
        return hasAction && hasImage;
    }

    /**
     * 图片搜索 - 按标题/描述/标签模糊匹配，按产品分组返回(主图+详情图)
     */
    private List<Map<String, Object>> searchImages(String query, String userId) {
        try {
            // 提取查询关键词(简单分词，取2-6字的关键词)
            String[] terms = query.split("[\\s,，。！？?]+");
            List<String> keywords = new ArrayList<>();
            for (String term : terms) {
                String t = term.trim();
                if (t.length() >= 2 && t.length() <= 10 && !isStopWord(t)) {
                    keywords.add(t);
                }
            }
            if (keywords.isEmpty()) {
                keywords.add(query.trim());
            }

            // 第一步: 先搜索匹配的图片(最多50张，确保每个产品都有图)
            // 只用 images 表现有字段搜索，避免依赖可能不存在的 image_tags/image_ai_tags 表
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT id, title, url, thumbnail_url, is_main_image, file_type, ");
            sql.append("width, height, product_id, album_name, created_at ");
            sql.append("FROM images WHERE deleted = false AND user_id = ? ");
            sql.append("AND (");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(COALESCE(title, '') ILIKE ? OR COALESCE(description, '') ILIKE ? OR COALESCE(album_name, '') ILIKE ?)");
            }
            sql.append(") ");
            sql.append("ORDER BY is_main_image DESC, created_at DESC ");
            sql.append("LIMIT 50");

            List<Object> params = new ArrayList<>();
            params.add(userId);
            for (String kw : keywords) {
                String pattern = "%" + kw + "%";
                params.add(pattern);
                params.add(pattern);
                params.add(pattern);
            }

            log.info("图片搜索SQL: {}", sql.toString());
            log.info("图片搜索参数: userId={}, keywords={}", userId, keywords);

            List<Map<String, Object>> rawImages = jdbcTemplate.query(sql.toString(),
                    (rs, rowNum) -> {
                        Map<String, Object> img = new LinkedHashMap<>();
                        img.put("id", rs.getString("id"));
                        img.put("title", rs.getString("title"));
                        img.put("url", rs.getString("url"));
                        img.put("thumbnailUrl", rs.getString("thumbnail_url"));
                        img.put("isMainImage", rs.getBoolean("is_main_image"));
                        img.put("fileType", rs.getString("file_type"));
                        img.put("width", rs.getInt("width"));
                        img.put("height", rs.getInt("height"));
                        img.put("productId", rs.getString("product_id"));
                        img.put("albumName", rs.getString("album_name"));
                        img.put("createdAt", rs.getTimestamp("created_at") != null
                                ? rs.getTimestamp("created_at").toLocalDateTime().toString() : null);
                        return img;
                    },
                    params.toArray()
            );

            log.info("图片搜索关键词匹配到 {} 条原始记录", rawImages.size());

            // 如果关键词搜索不到，兜底返回用户最新的20张图片
            if (rawImages.isEmpty()) {
                log.info("关键词未匹配到图片，兜底返回用户最新图片");
                String fallbackSql = "SELECT id, title, url, thumbnail_url, is_main_image, file_type, " +
                        "width, height, product_id, album_name, created_at " +
                        "FROM images WHERE deleted = false AND user_id = ? " +
                        "ORDER BY created_at DESC LIMIT 20";
                rawImages = jdbcTemplate.query(fallbackSql,
                        (rs, rowNum) -> {
                            Map<String, Object> img = new LinkedHashMap<>();
                            img.put("id", rs.getString("id"));
                            img.put("title", rs.getString("title"));
                            img.put("url", rs.getString("url"));
                            img.put("thumbnailUrl", rs.getString("thumbnail_url"));
                            img.put("isMainImage", rs.getBoolean("is_main_image"));
                            img.put("fileType", rs.getString("file_type"));
                            img.put("width", rs.getInt("width"));
                            img.put("height", rs.getInt("height"));
                            img.put("productId", rs.getString("product_id"));
                            img.put("albumName", rs.getString("album_name"));
                            img.put("createdAt", rs.getTimestamp("created_at") != null
                                    ? rs.getTimestamp("created_at").toLocalDateTime().toString() : null);
                            return img;
                        },
                        userId
                );
                log.info("兜底查询返回 {} 条图片", rawImages.size());
            }

            // 第二步: 按 product_id 分组，每个产品保留主图+详情图
            Map<String, List<Map<String, Object>>> productGroups = new LinkedHashMap<>();
            for (Map<String, Object> img : rawImages) {
                String pid = img.getOrDefault("productId", "").toString();
                if (pid == null || pid.isEmpty()) {
                    pid = "no_product_" + img.get("id");
                }
                productGroups.computeIfAbsent(pid, k -> new ArrayList<>()).add(img);
            }

            // 第三步: 构建按产品分组的结果(最多10个产品)
            List<Map<String, Object>> products = new ArrayList<>();
            int productCount = 0;
            for (Map.Entry<String, List<Map<String, Object>>> entry : productGroups.entrySet()) {
                if (productCount >= 10) break;
                List<Map<String, Object>> imgs = entry.getValue();
                if (imgs.isEmpty()) continue;

                Map<String, Object> mainImage = null;
                List<Map<String, Object>> detailImages = new ArrayList<>();
                for (Map<String, Object> img : imgs) {
                    if (Boolean.TRUE.equals(img.get("isMainImage"))) {
                        mainImage = img;
                    } else {
                        detailImages.add(img);
                    }
                }
                // 如果没有主图，用第一张作为主图
                if (mainImage == null && !detailImages.isEmpty()) {
                    mainImage = detailImages.remove(0);
                }

                Map<String, Object> product = new LinkedHashMap<>();
                product.put("productId", entry.getKey());
                product.put("productName", mainImage != null ? mainImage.getOrDefault("title", "") : "");
                product.put("mainImage", mainImage);
                product.put("detailImages", detailImages);
                product.put("albumName", mainImage != null ? mainImage.getOrDefault("albumName", "") : "");
                products.add(product);
                productCount++;
            }

            log.info("图片搜索最终返回 {} 个产品", products.size());
            return products;
        } catch (Exception e) {
            log.error("图片搜索失败", e);
            return Collections.emptyList();
        }
    }

    private boolean isStopWord(String word) {
        String[] stops = {"一下", "一下", "什么", "怎么", "这个", "那个", "可以", "帮我", "请问"};
        for (String s : stops) {
            if (word.equals(s)) return true;
        }
        return false;
    }

    /**
     * 知识库检索 - 查询知识库独立的向量表(knowledge_embeddings, source_type='KNOWLEDGE_BASE')
     */
    private List<Map<String, Object>> searchKnowledgeBase(String query, String userId, String company) {
        try {
            List<MemorySearchResult> allResults = knowledgeBaseService.search(query, 0.15, 8, company, userId);

            List<Map<String, Object>> results = new ArrayList<>();
            for (MemorySearchResult r : allResults) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("content", r.getContent() != null ? r.getContent() : "");
                item.put("score", r.getScore() != null ? r.getScore() : 0);
                item.put("cardId", r.getId().toString());
                item.put("title", r.getTitle() != null ? r.getTitle() : "");
                item.put("domain", r.getDomainName() != null ? r.getDomainName() : "知识库");
                item.put("source", "knowledge_base");
                results.add(item);
            }
            return results;
        } catch (Exception e) {
            log.warn("知识库检索异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 加载对话历史
     */

    /**
     * 保存对话消息（按userId+company绑定，session_id存储为基于userId生成的确定性UUID）
     */
    private void saveChatMessage(String userId, String _unused, String role, String content, String company) {
        try {
            // 用 userId 生成确定性 UUID 作为 session_id（同一用户始终相同）
            String sessionId = UUID.nameUUIDFromBytes(("chat-" + userId).getBytes()).toString();
            jdbcTemplate.update(
                    "INSERT INTO smart_chat_history (id, session_id, role, content, user_id, company, created_at) " +
                            "VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, ?, NOW())",
                    sessionId, role, content, userId, company
            );
        } catch (Exception e) {
            log.warn("保存对话消息失败: {}", e.getMessage());
        }
    }

    /**
     * 流式调用MiniMax API (Anthropic兼容接口)
     */
    private void streamChat(SseEmitter emitter, List<Map<String, Object>> messages,
                            StringBuilder fullResponse) {
        try {
            String apiKey = minimaxApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("MINIMAX_API_KEY");
            }
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("未配置MiniMax API密钥, 请设置环境变量 MINIMAX_API_KEY");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", minimaxModel);
            body.put("max_tokens", 8192);
            body.put("stream", true);
            body.put("temperature", 0.7);
            body.put("system", "你是盈云产品智能中台的AI助手，专注于供应链、工厂管理和产品知识领域。" +
                    "请基于提供的记忆库知识卡片和知识库文档片段回答用户问题。" +
                    "回答时标注引用来源(记忆库/知识库)。" +
                    "如果参考资料中没有相关信息，请明确说明，不要编造。" +
                    "回答使用中文。保持对话连贯性，参考上下文历史。" +
                    "重要：请确保每句话都完整说完，不要在中途停止或截断。即使回答较长，也要把所有内容完整输出到自然结束。");
            body.put("messages", messages);

            String endpointUrl = minimaxBaseUrl.endsWith("/") ? minimaxBaseUrl.substring(0, minimaxBaseUrl.length() - 1) : minimaxBaseUrl;
            if (!endpointUrl.endsWith("/messages")) {
                endpointUrl = endpointUrl + "/anthropic/v1/messages";
            }
            HttpURLConnection conn = (HttpURLConnection) URI.create(endpointUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);
            conn.setConnectTimeout(60000);
            conn.setReadTimeout(600000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(objectMapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                BufferedReader errorReader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
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
                                case "content_block_start":
                                    JsonNode cbStart = node.path("content_block");
                                    String cbType = cbStart.path("type").asText("");
                                    log.debug("content_block_start 类型: {}", cbType);
                                    if ("text".equals(cbType)) {
                                        String text = cbStart.path("text").asText("");
                                        if (!text.isEmpty()) {
                                            fullResponse.append(text);
                                            emitter.send(SseEmitter.event().name("message").data(
                                                    objectMapper.writeValueAsString(Map.of("type", "content", "content", text))
                                            ));
                                            log.info("content_block_start text: 长度={}, 内容前50字={}", text.length(), text.substring(0, Math.min(text.length(), 50)));
                                        }
                                    }
                                    break;
                                case "content_block_delta":
                                    JsonNode delta = node.path("delta");
                                    String deltaType = delta.path("type").asText("");
                                    // MiniMax兼容: 支持 text_delta 和 text 两种类型
                                    if ("text_delta".equals(deltaType) || "text".equals(deltaType)) {
                                        String text = delta.has("text") ? delta.path("text").asText("")
                                                : delta.has("content") ? delta.path("content").asText("")
                                                : delta.path("partial_json").asText("");
                                        if (!text.isEmpty()) {
                                            fullResponse.append(text);
                                            emitter.send(SseEmitter.event().name("message").data(
                                                    objectMapper.writeValueAsString(Map.of("type", "content", "content", text))
                                            ));
                                            log.debug("text_delta: 长度={}", text.length());
                                        }
                                    } else if (!deltaType.isEmpty()) {
                                        log.info("未处理的delta类型: {}, 数据: {}", deltaType, delta.toString().substring(0, Math.min(delta.toString().length(), 200)));
                                    }
                                    break;
                                case "content_block_stop":
                                    int cbIndex = node.path("index").asInt(-1);
                                    log.debug("content_block_stop index: {}", cbIndex);
                                    break;
                                case "message_delta":
                                    JsonNode msgDelta = node.path("delta");
                                    String stopReason = msgDelta.path("stop_reason").asText("");
                                    if (!stopReason.isEmpty()) {
                                        log.info("MiniMax消息停止原因: {}, 累计输出字符数: {}", stopReason, fullResponse.length());
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
                                default:
                                    // message_start, ping 等事件是正常协议事件，无需处理
                                    if (!eventType.isEmpty() && !"message_start".equals(eventType) && !"ping".equals(eventType)) {
                                        log.debug("未处理的SSE事件类型: {}, data: {}", eventType, data.substring(0, Math.min(data.length(), 200)));
                                    }
                                    break;
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
}
