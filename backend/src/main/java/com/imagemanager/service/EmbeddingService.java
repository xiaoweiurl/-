package com.imagemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class EmbeddingService {

    @Value("${app.minimax.api-key:}")
    private String minimaxApiKey;

    @Value("${app.minimax.embedding.base-url:https://api.minimaxi.com/v1/embeddings}")
    private String minimaxEmbeddingUrl;

    @Value("${app.minimax.embedding.model:embo-01}")
    private String minimaxEmbeddingModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取文本的向量嵌入
     */
    public float[] getEmbedding(String text) {
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
            body.remove("type");
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

    /**
     * float数组转pgvector字符串
     */
    public String arrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
