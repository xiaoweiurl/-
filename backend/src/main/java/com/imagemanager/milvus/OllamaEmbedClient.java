package com.imagemanager.milvus;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 调用本机 Ollama /api/embed。评测用它把问题变成 bge-m3 向量，不把向量写回 Milvus。
 */
public final class OllamaEmbedClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public float[] embed(String baseUrl, String model, String text) {
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("input", text == null ? "" : text);
        HttpRequest request = HttpRequest.newBuilder(URI.create(root + "/api/embed"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("调用 Ollama 嵌入失败: " + root + "/api/embed " + e.getMessage(), e);
        }
        if (response.statusCode() / 100 != 2) {
            String snippet = response.body() == null ? "" : response.body();
            if (snippet.length() > 300) {
                snippet = snippet.substring(0, 300);
            }
            throw new IllegalStateException("Ollama 嵌入返回 " + response.statusCode() + " " + snippet);
        }
        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray vector = null;
        if (json.has("embeddings") && json.get("embeddings").isJsonArray() && !json.getAsJsonArray("embeddings").isEmpty()) {
            vector = json.getAsJsonArray("embeddings").get(0).getAsJsonArray();
        } else if (json.has("embedding") && json.get("embedding").isJsonArray()) {
            vector = json.getAsJsonArray("embedding");
        }
        if (vector == null || vector.isEmpty()) {
            throw new IllegalStateException("Ollama 响应里没有 embedding");
        }
        float[] out = new float[vector.size()];
        int i = 0;
        for (JsonElement element : vector) {
            out[i++] = element.getAsFloat();
        }
        return out;
    }
}
