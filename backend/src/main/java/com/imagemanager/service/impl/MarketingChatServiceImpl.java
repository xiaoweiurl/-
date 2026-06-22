package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.service.MarketingChatService;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
public class MarketingChatServiceImpl implements MarketingChatService {

    @Value("${app.minimax.api-key:}")
    private String apiKey;

    private static final String MINIMAX_V2_URL = "https://api.minimax.chat/v1/text/chatcompletion_v2";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                    "- 重要：请确保每句话都完整说完，不要在中途停止或截断"
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

                // 4. 调用 MiniMax V2 流式接口
                StringBuilder fullResponse = new StringBuilder();
                streamChatV2(emitter, messages, fullResponse);

                // 5. 保存AI回复
                saveChatMessage(userId, "assistant", fullResponse.toString(), company);

                emitter.complete();
            } catch (Exception e) {
                log.error("市场营销对话失败: {}", e.getMessage());
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
            if (apiKey == null || apiKey.isEmpty()) {
                throw new RuntimeException("未配置 MINIMAX_API_KEY");
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "MiniMax-Text-01");
            body.put("messages", messages);
            body.put("stream", true);

            HttpURLConnection conn = (HttpURLConnection) URI.create(MINIMAX_V2_URL).toURL().openConnection();
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
                throw new RuntimeException("MiniMax V2 API返回错误 " + responseCode + ": " + errorBody);
            }

            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (data.isEmpty()) continue;
                        if ("[DONE]".equals(data)) {
                            emitter.send(SseEmitter.event().name("message").data(
                                objectMapper.writeValueAsString(Map.of("type", "done"))
                            ));
                            return;
                        }

                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode choices = node.path("choices");
                            if (choices.isArray() && choices.size() > 0) {
                                JsonNode choice = choices.get(0);
                                JsonNode delta = choice.path("delta");
                                String content = delta.path("content").asText("");
                                if (!content.isEmpty()) {
                                    fullResponse.append(content);
                                    emitter.send(SseEmitter.event().name("message").data(
                                        objectMapper.writeValueAsString(Map.of("type", "content", "content", content))
                                    ));
                                }
                                // 检查是否结束
                                String finishReason = choice.path("finish_reason").asText("");
                                if ("stop".equals(finishReason)) {
                                    emitter.send(SseEmitter.event().name("message").data(
                                        objectMapper.writeValueAsString(Map.of("type", "done"))
                                    ));
                                    return;
                                }
                            }
                        } catch (Exception parseEx) {
                            if (parseEx instanceof RuntimeException) throw parseEx;
                            log.debug("解析V2 SSE行失败: {}", data.substring(0, Math.min(data.length(), 200)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("MiniMax V2流式对话失败: {}", e.getMessage());
            throw new RuntimeException("流式对话失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getChatHistory(String userId, String company) {
        String sql = "SELECT role, content, created_at FROM smart_chat_history " +
            "WHERE user_id = ?::uuid AND (company = ? OR company IS NULL) " +
            "AND session_id = ?::uuid " +
            "ORDER BY created_at DESC LIMIT 20";
        // 使用固定的session_id标识营销对话（与智能对话区分）
        String marketingSessionId = UUID.nameUUIDFromBytes(("marketing_" + userId + "_" + company).getBytes()).toString();
        List<Map<String, Object>> results = jdbcTemplate.query(sql,
            (rs, rowNum) -> {
                Map<String, Object> msg = new LinkedHashMap<>();
                msg.put("role", rs.getString("role"));
                msg.put("content", rs.getString("content"));
                msg.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                return msg;
            },
            userId, company, marketingSessionId
        );
        Collections.reverse(results);
        return results;
    }

    @Override
    public void clearChatHistory(String userId, String company) {
        String marketingSessionId = UUID.nameUUIDFromBytes(("marketing_" + userId + "_" + company).getBytes()).toString();
        jdbcTemplate.update(
            "DELETE FROM smart_chat_history WHERE user_id = ?::uuid AND (company = ? OR company IS NULL) AND session_id = ?::uuid",
            userId, company, marketingSessionId
        );
    }

    private void saveChatMessage(String userId, String role, String content, String company) {
        String marketingSessionId = UUID.nameUUIDFromBytes(("marketing_" + userId + "_" + company).getBytes()).toString();
        jdbcTemplate.update(
            "INSERT INTO smart_chat_history (id, user_id, session_id, role, content, company, created_at) " +
                "VALUES (?, ?::uuid, ?::uuid, ?, ?, ?, NOW())",
            UUID.randomUUID().toString(), userId, marketingSessionId, role, content, company
        );
    }
}
