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
 * 文档重排序器：用大模型对向量检索结果做二次评分排序。
 * 向量相似度高 ≠ 回答相关，Reranker用LLM判断每个chunk与问题的相关性。
 * 基于PDF教程中的Reranker实现。
 */
@Slf4j
@Component
public class Reranker {

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${app.ollama.chat-model:qwen3.6:35b}")
    private String ollamaModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
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

        if (results.size() <= topN) {
            // 结果数量不超过topN，仍尝试重排序以提升质量
            return rerankInternal(query, results, results.size());
        }

        return rerankInternal(query, results, topN);
    }

    /**
     * 调用大模型对每个chunk打分
     */
    private List<MemorySearchResult> rerankInternal(String query, List<MemorySearchResult> results, int topN) {
        try {
            // 构建文档列表（截断每个chunk避免prompt过长）
            StringBuilder docList = new StringBuilder();
            Map<Integer, MemorySearchResult> indexMap = new LinkedHashMap<>();
            int idx = 0;
            for (MemorySearchResult r : results) {
                String content = r.getContent();
                if (content != null && !content.isEmpty()) {
                    // 截断到300字符避免prompt过长
                    String truncated = content.length() > 300 ? content.substring(0, 300) + "..." : content;
                    docList.append(String.format("[%d] %s\n", idx, truncated));
                    indexMap.put(idx, r);
                    idx++;
                }
            }

            if (indexMap.isEmpty()) {
                return results.stream().limit(topN).collect(Collectors.toList());
            }

            String prompt = String.format("""
                你是文档相关性评估助手。请评估以下文档片段与用户问题的相关性。
                对每个文档片段打分（0-10分），10分表示完全相关，0分表示完全无关。

                用户问题：%s

                文档片段：
                %s

                请直接返回JSON数组格式，如：[{"id":0,"score":8},{"id":1,"score":3}]
                不要包含其他内容。
                """, query, docList.toString());

            String requestBody = mapper.writeValueAsString(new HashMap<String, Object>() {{
                put("model", ollamaModel);
                put("prompt", prompt);
                put("stream", false);
                put("options", new HashMap<String, Object>() {{
                    put("temperature", 0.1);
                    put("num_predict", 500);
                }});
            }});

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[Reranker] Ollama返回状态码: {}, 跳过重排序", response.statusCode());
                return results.stream().limit(topN).collect(Collectors.toList());
            }

            JsonNode root = mapper.readTree(response.body());
            String responseText = root.path("response").asText("").trim();

            // 解析打分结果
            Map<Integer, Double> scores = parseScores(responseText, indexMap.size());

            // 按分数排序
            List<MemorySearchResult> reranked = indexMap.entrySet().stream()
                    .sorted((a, b) -> {
                        double scoreA = scores.getOrDefault(a.getKey(), a.getValue().getScore());
                        double scoreB = scores.getOrDefault(b.getKey(), b.getValue().getScore());
                        return Double.compare(scoreB, scoreA); // 降序
                    })
                    .limit(topN)
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());

            log.info("[Reranker] 重排序完成: {} 条 -> {} 条", results.size(), reranked.size());
            return reranked;

        } catch (Exception e) {
            log.warn("[Reranker] 重排序失败，使用原始排序: {}", e.getMessage());
            return results.stream().limit(topN).collect(Collectors.toList());
        }
    }

    /**
     * 解析大模型返回的打分JSON
     */
    private Map<Integer, Double> parseScores(String text, int expectedCount) {
        Map<Integer, Double> scores = new HashMap<>();
        try {
            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start >= 0 && end > start) {
                String jsonStr = text.substring(start, end + 1);
                JsonNode array = mapper.readTree(jsonStr);
                for (JsonNode node : array) {
                    int id = node.path("id").asInt(-1);
                    double score = node.path("score").asDouble(0);
                    if (id >= 0) {
                        scores.put(id, score);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Reranker] 解析打分JSON失败: {}", e.getMessage());
        }
        return scores;
    }
}
