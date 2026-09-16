package com.imagemanager.enhance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询增强器：调用大模型将用户原始问题重写为3-5个变体查询，
 * 提升向量检索召回率。基于PDF教程中的QueryEnhancer实现。
 */
@Slf4j
@Component
public class QueryEnhancer {

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${app.ollama.chat-model:qwen3.6:35b}")
    private String ollamaModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 增强查询：生成原始查询 + 变体查询
     */
    public List<String> enhance(String originalQuery) {
        List<String> queries = new ArrayList<>();
        queries.add(originalQuery); // 原始查询始终包含

        try {
            List<String> variants = generateVariants(originalQuery);
            queries.addAll(variants);
        } catch (Exception e) {
            log.warn("[QueryEnhancer] 生成变体查询失败，仅使用原始查询: {}", e.getMessage());
        }

        log.info("[QueryEnhancer] 增强查询数量: {}, 原始: {}", queries.size(), originalQuery);
        return queries;
    }

    /**
     * 调用大模型生成变体查询
     */
    private List<String> generateVariants(String originalQuery) throws Exception {
        String prompt = String.format("""
            你是查询重写助手。将以下用户问题重写为3个不同的变体查询，用于向量检索。
            变体查询应：
            1. 保持原意不变
            2. 使用不同的表达方式和关键词
            3. 可以补充隐含的上下文信息
            4. 可以拆解为子问题

            原始问题：%s

            请直接返回JSON数组格式，如：["变体1","变体2","变体3"]
            不要包含其他内容。
            """, originalQuery);

        Map<String, Object> options = new HashMap<>();
        options.put("temperature", 0.7);
        options.put("num_predict", 300);
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", ollamaModel);
        payload.put("prompt", prompt);
        payload.put("stream", false);
        payload.put("options", options);
        String requestBody = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.warn("[QueryEnhancer] Ollama返回状态码: {}", response.statusCode());
            return List.of();
        }

        JsonNode root = mapper.readTree(response.body());
        String responseText = root.path("response").asText("").trim();

        // 提取JSON数组
        List<String> variants = parseJsonArray(responseText);
        log.info("[QueryEnhancer] 生成变体: {}", variants);
        return variants;
    }

    private List<String> parseJsonArray(String text) {
        List<String> result = new ArrayList<>();
        try {
            // 尝试直接解析
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start >= 0 && end > start) {
                String jsonStr = text.substring(start, end + 1);
                JsonNode array = mapper.readTree(jsonStr);
                for (JsonNode node : array) {
                    String s = node.asText().trim();
                    if (!s.isEmpty()) {
                        result.add(s);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[QueryEnhancer] 解析变体JSON失败: {}", e.getMessage());
        }
        return result;
    }
}
