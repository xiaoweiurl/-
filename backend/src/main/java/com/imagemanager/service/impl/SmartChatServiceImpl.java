package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.MemorySearchResult;
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
    public SseEmitter smartChat(String message, String sessionId, String userId) {
        SseEmitter emitter = new SseEmitter(180000L); // 3分钟超时

        new Thread(() -> {
            try {
                // 1. 加载历史对话
                List<Map<String, Object>> history = loadChatHistory(sessionId);

                // 2. 双库检索
                // 2a. 记忆库检索(PostgreSQL向量)
                List<MemorySearchResult> memoryResults = Collections.emptyList();
                try {
                    memoryResults = memoryService.search(message, null, 0.3, 5, userId);
                    log.info("记忆库检索到 {} 条结果", memoryResults.size());
                } catch (Exception e) {
                    log.warn("记忆库检索异常: {}", e.getMessage());
                }

                // 2b. 知识库检索(Coze SDK via Next.js)
                List<Map<String, Object>> knowledgeResults = Collections.emptyList();
                try {
                    knowledgeResults = searchKnowledgeBase(message, userId);
                    log.info("知识库检索到 {} 条结果", knowledgeResults.size());
                } catch (Exception e) {
                    log.warn("知识库检索异常: {}", e.getMessage());
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

                // 4. 构建知识上下文
                StringBuilder knowledgeContext = new StringBuilder();

                if (!memoryResults.isEmpty()) {
                    knowledgeContext.append("## 记忆库相关知识卡片：\n");
                    for (int i = 0; i < memoryResults.size(); i++) {
                        MemorySearchResult r = memoryResults.get(i);
                        knowledgeContext.append(String.format("### 卡片%d [%s] %s\n%s\n置信度: %s | 来源: %s\n\n",
                                i + 1, r.getDomainName(), r.getTitle(), r.getContent(),
                                r.getConfidence(), r.getSource() != null ? r.getSource() : "未知"));
                    }
                }

                if (!knowledgeResults.isEmpty()) {
                    knowledgeContext.append("## 知识库相关文档片段：\n");
                    for (int i = 0; i < knowledgeResults.size(); i++) {
                        Map<String, Object> r = knowledgeResults.get(i);
                        double score = ((Number) r.getOrDefault("score", 0)).doubleValue();
                        knowledgeContext.append(String.format("### 片段%d (相关度: %.1f%%)\n%s\n\n",
                                i + 1, score * 100, r.getOrDefault("content", "")));
                    }
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
                int startIdx = Math.max(0, history.size() - 20);
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
                saveChatMessage(sessionId, userId, "user", message);

                // 7. 流式调用MiniMax
                StringBuilder fullResponse = new StringBuilder();
                streamChat(emitter, messages, fullResponse);

                // 8. 保存AI回复
                saveChatMessage(sessionId, userId, "assistant", fullResponse.toString());

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
    public List<Map<String, Object>> getChatHistory(String sessionId, String userId) {
        String sql = "SELECT role, content, created_at FROM smart_chat_history " +
                "WHERE session_id = ?::uuid AND user_id = ? ORDER BY created_at ASC";
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", rs.getString("role"));
                    msg.put("content", rs.getString("content"));
                    msg.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    return msg;
                },
                sessionId, userId
        );
    }

    @Override
    @Transactional
    public void clearChatHistory(String sessionId, String userId) {
        jdbcTemplate.update(
                "DELETE FROM smart_chat_history WHERE session_id = ?::uuid AND user_id = ?",
                sessionId, userId
        );
    }

    // ========== 私有方法 ==========

    /**
     * 知识库检索 - 直接查PostgreSQL向量(与记忆库共用knowledge_cards表)
     * 不再调用Next.js的Coze SDK接口，统一使用数据库向量搜索
     */
    private List<Map<String, Object>> searchKnowledgeBase(String query, String userId) {
        try {
            // 使用与记忆库相同的搜索逻辑，但不限定domainCode
            List<MemorySearchResult> allResults = memoryService.search(query, null, 0.25, 10, userId);
            
            List<Map<String, Object>> results = new ArrayList<>();
            for (MemorySearchResult r : allResults) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("content", r.getContent() != null ? r.getContent() : "");
                item.put("score", r.getScore() != null ? r.getScore() : 0);
                item.put("cardId", r.getId().toString());
                item.put("title", r.getTitle() != null ? r.getTitle() : "");
                item.put("domain", r.getDomainName() != null ? r.getDomainName() : "");
                item.put("source", "knowledge");
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
    private List<Map<String, Object>> loadChatHistory(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return Collections.emptyList();
        try {
            return getChatHistory(sessionId, "system");
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 保存对话消息
     */
    private void saveChatMessage(String sessionId, String userId, String role, String content) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO smart_chat_history (id, session_id, role, content, user_id, created_at) " +
                            "VALUES (gen_random_uuid(), ?::uuid, ?, ?, ?, NOW())",
                    sessionId, role, content, userId
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
            body.put("max_tokens", 4096);
            body.put("stream", true);
            body.put("system", "你是盈云产品智能中台的AI助手，专注于供应链、工厂管理和产品知识领域。" +
                    "请基于提供的记忆库知识卡片和知识库文档片段回答用户问题。" +
                    "回答时标注引用来源(记忆库/知识库)。" +
                    "如果参考资料中没有相关信息，请明确说明，不要编造。" +
                    "回答使用中文。保持对话连贯性，参考上下文历史。");
            body.put("messages", messages);

            HttpURLConnection conn = (HttpURLConnection) URI.create(minimaxBaseUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(180000);

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
}
