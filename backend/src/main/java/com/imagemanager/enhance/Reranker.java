package com.imagemanager.enhance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.MemorySearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档重排序器：调用独立的 Reranker 服务对向量检索结果做二次评分排序。
 * 向量相似度高 ≠ 回答相关，Reranker 判断每个 chunk 与问题的实际相关性。
 * 
 * 依赖服务：reranker-service（基于 FlagEmbedding + bge-reranker-v2-m3）
 * 服务地址：通过 app.reranker.base-url 配置
 */
@Slf4j
@Component
public class Reranker {

    @Value("${app.reranker.base-url:http://localhost:8001}")
    private String rerankerBaseUrl;

    @Value("${app.reranker.timeout:5000}")
    private int timeoutMs;

    @Value("${app.reranker.enabled:true}")
    private boolean enabled;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 对检索结果重排序
     * @param query 用户问题
     * @param results 向量检索结果
     * @param topN 保留前N条
     * @return 重排序后的结果
     */
    public List<MemorySearchResult> rerank(String query, List<MemorySearchResult> results, int topN) {
        if (results == null || results.isEmpty()) {
            return results;
        }

        // 如果禁用 reranker，直接返回原始结果
        if (!enabled) {
            return results.stream().limit(topN).collect(Collectors.toList());
        }

        // 结果数量少于等于 topN，仍尝试重排序以提升质量
        return rerankInternal(query, results, topN);
    }

    /**
     * 调用 Reranker 服务进行重排序
     */
    private List<MemorySearchResult> rerankInternal(String query, List<MemorySearchResult> results, int topN) {
        try {
            // 提取文档内容
            List<String> documents = results.stream()
                    .map(r -> r.getContent() != null ? r.getContent() : "")
                    .collect(Collectors.toList());

            if (documents.isEmpty()) {
                return results.stream().limit(topN).collect(Collectors.toList());
            }

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            requestBody.put("documents", documents);
            requestBody.put("top_k", topN);
            requestBody.put("normalize", true);

            String requestJson = mapper.writeValueAsString(requestBody);

            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rerankerBaseUrl + "/rerank"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[Reranker] 服务返回状态码: {}, 使用原始排序", response.statusCode());
                return results.stream().limit(topN).collect(Collectors.toList());
            }

            // 解析响应
            JsonNode root = mapper.readTree(response.body());
            JsonNode resultsNode = root.path("results");

            if (!resultsNode.isArray()) {
                log.warn("[Reranker] 响应格式错误，使用原始排序");
                return results.stream().limit(topN).collect(Collectors.toList());
            }

            // 构建重排序后的结果
            List<MemorySearchResult> reranked = new ArrayList<>();
            for (JsonNode item : resultsNode) {
                int index = item.path("index").asInt(-1);
                double score = item.path("score").asDouble(0.0);

                if (index >= 0 && index < results.size()) {
                    MemorySearchResult original = results.get(index);
                    // 使用 Builder 创建新结果，更新分数
                    MemorySearchResult rerankedResult = MemorySearchResult.builder()
                            .id(original.getId())
                            .domainId(original.getDomainId())
                            .domainName(original.getDomainName())
                            .domainCode(original.getDomainCode())
                            .cardId(original.getCardId())
                            .title(original.getTitle())
                            .content(original.getContent())
                            .chunkText(original.getChunkText())
                            .score(score)  // 使用 reranker 的分数
                            .confidence(original.getConfidence())
                            .source(original.getSource())
                            .sourceDocId(original.getSourceDocId())
                            .createdAt(original.getCreatedAt())
                            .build();
                    reranked.add(rerankedResult);
                }
            }


            return reranked;

        } catch (Exception e) {
            log.warn("[Reranker] 调用失败，使用原始排序: {}", e.getMessage());
            return results.stream().limit(topN).collect(Collectors.toList());
        }
    }

    /**
     * 检查 Reranker 服务是否可用
     */
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rerankerBaseUrl + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = mapper.readTree(response.body());
                return root.path("loaded").asBoolean(false);
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
