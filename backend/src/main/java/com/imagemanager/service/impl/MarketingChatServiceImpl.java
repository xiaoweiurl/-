package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.service.MarketingChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
public class MarketingChatServiceImpl implements MarketingChatService {

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.chat-model:qwen3.6:35b}")
    private String ollamaChatModel;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** AI 调用日志（可选注入——Bean 不存在时不影响主流程） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.imagemanager.service.AiCallLogService aiCallLogService;

    public MarketingChatServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SseEmitter chat(String message, String userId, String company) {
        SseEmitter emitter = new SseEmitter(300000L);

        new Thread(() -> {
            try {
                // 1. 加载历史对话（最近10轮）
                List<Map<String, Object>> history = getChatHistory(userId, company);

                // 2. 构建消息列表
                List<Map<String, String>> messages = new ArrayList<>();

                // 系统提示词 - 无缝针织行业市场营销专家
                Map<String, String> systemMsg = new LinkedHashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("name", "针织营销顾问");
                systemMsg.put("content",
                    "你是「盈云·针织营销顾问」，一位专注于无缝针织行业的资深市场营销专家。\n" +
                    "你的核心能力：\n" +
                    "1. 无缝针织行业市场分析与趋势洞察\n" +
                    "2. 内衣、运动服饰、泳装等细分领域营销策略\n" +
                    "3. 品牌定位与差异化竞争策略\n" +
                    "4. 供应链管理与成本优化建议\n" +
                    "5. 数字化营销与全渠道推广方案\n" +
                    "6. 国内外针织品牌案例解读\n\n" +
                    "回答要求：\n" +
                    "- 结合无缝针织行业特点，给出专业、可落地的建议\n" +
                    "- 使用中文回答，语言专业但不晦涩\n" +
                    "- 如果用户问题超出专业范围，礼貌说明并尝试给出通用建议\n" +
                    "- 重要：请确保每句话都完整说完，不要在中途停止或截断\n" +
                    "- 输出格式规范：使用Markdown格式，用表格展示数据（表头加粗），用列表展示要点，用加粗强调关键数据，不要使用特殊符号(如※★●◆等)做装饰，不要使用过多分隔线，保持版面简洁清晰"
                );
                messages.add(systemMsg);

                // 添加历史消息
                for (Map<String, Object> msg : history) {
                    Map<String, String> histMsg = new LinkedHashMap<>();
                    histMsg.put("role", (String) msg.get("role"));
                    histMsg.put("content", (String) msg.get("content"));
                    messages.add(histMsg);
                }

                // 添加当前用户消息
                Map<String, String> userMsg = new LinkedHashMap<>();
                userMsg.put("role", "user");
                userMsg.put("name", "用户");
                userMsg.put("content", message);
                messages.add(userMsg);

                // 3. 保存用户消息
                saveChatMessage(userId, "user", message, company);

                // 4. 调用 Ollama 流式接口（记录真实调用日志）
                StringBuilder fullResponse = new StringBuilder();
                long chatCallStart = System.currentTimeMillis();
                try {
                    streamChatV2(emitter, messages, fullResponse);
                    if (aiCallLogService != null) {
                        aiCallLogService.record(com.imagemanager.service.AiCallLogService.CAP_MARKETING_CHAT,
                                ollamaChatModel, true,
                                System.currentTimeMillis() - chatCallStart, null, "marketing-chat userId=" + userId);
                    }
                } catch (Exception chatEx) {
                    if (aiCallLogService != null) {
                        aiCallLogService.record(com.imagemanager.service.AiCallLogService.CAP_MARKETING_CHAT,
                                ollamaChatModel, false,
                                System.currentTimeMillis() - chatCallStart, null, "marketing-chat userId=" + userId);
                    }
                    throw chatEx;
                }

                // 5. 保存AI回复
                saveChatMessage(userId, "assistant", fullResponse.toString(), company);

                emitter.complete();
            } catch (Exception e) {
                log.error("市场营销对话失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("message").data(
                        objectMapper.writeValueAsString(Map.of("type", "error", "content", "对话失败: " + e.getMessage()))
                    ));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private void streamChatV2(SseEmitter emitter, List<Map<String, String>> messages, StringBuilder fullResponse) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", ollamaChatModel);
            body.put("messages", messages);
            body.put("stream", true);
            Map<String, Object> options = new HashMap<>();
            options.put("temperature", 0.7);
            options.put("num_predict", -1); // -1=无限输出
            body.put("options", options);

            String endpointUrl = ollamaBaseUrl + "/api/chat";
            HttpURLConnection conn = (HttpURLConnection) URI.create(endpointUrl).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(30000);
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
                throw new RuntimeException("Ollama API返回错误 " + responseCode + ": " + errorBody);
            }

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;

                    try {
                        JsonNode node = objectMapper.readTree(line);
                        boolean done = node.has("done") && node.get("done").asBoolean(false);

                        if (node.has("message")) {
                            JsonNode messageNode = node.get("message");
                            if (messageNode.has("content") && messageNode.get("content").isTextual()) {
                                String content = messageNode.get("content").asText();
                                if (!content.isEmpty()) {
                                    fullResponse.append(content);
                                    emitter.send(SseEmitter.event().name("message").data(
                                        objectMapper.writeValueAsString(Map.of("type", "content", "content", content))
                                    ));
                                }
                            }
                        }

                        if (done) {
                            emitter.send(SseEmitter.event().name("message").data(
                                objectMapper.writeValueAsString(Map.of("type", "done"))
                            ));
                            return;
                        }
                    } catch (Exception parseEx) {
                        log.debug("解析Ollama流式数据行失败: {}", line.substring(0, Math.min(line.length(), 200)));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Ollama流式对话失败: {}", e.getMessage());
            throw new RuntimeException("流式对话失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getChatHistory(String userId, String company) {
        String sql = "SELECT role, content, created_at FROM marketing_chat_history " +
            "WHERE user_id = ? AND (company = ? OR company IS NULL OR ? IS NULL) " +
            "ORDER BY created_at DESC LIMIT 20";
        List<Map<String, Object>> results = jdbcTemplate.query(sql,
            (rs, rowNum) -> {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", rs.getString("role"));
                msg.put("content", rs.getString("content"));
                msg.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                return msg;
            },
            userId, company, company
        );
        Collections.reverse(results);
        return results;
    }

    @Override
    public void clearChatHistory(String userId, String company) {
        jdbcTemplate.update(
            "DELETE FROM marketing_chat_history WHERE user_id = ? AND (company = ? OR company IS NULL OR ? IS NULL)",
            userId, company, company
        );
    }

    private void saveChatMessage(String userId, String role, String content, String company) {
        jdbcTemplate.update(
            "INSERT INTO marketing_chat_history (id, user_id, company, role, content, created_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW())",
            UUID.randomUUID().toString(), userId, company, role, content
        );
    }
}
