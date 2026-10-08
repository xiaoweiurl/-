package com.imagemanager.imagesearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * 调用本地图片向量服务。失败抛出 IllegalStateException，由上传异步任务吞掉。
 */
@Component
public class ImageEmbedClient implements ImageEmbedder {

    private final ImageSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ImageEmbedClient(ImageSearchProperties properties, ObjectMapper objectMapper) {
        // Java HttpClient 默认 HTTP/2，会发 Upgrade: h2c。uvicorn 的 httptools 因此返回 400、422 或空响应。
        this(properties, objectMapper, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    ImageEmbedClient(ImageSearchProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public float[] embedImage(byte[] data, String filename) {
        return embedImage(data, filename, false);
    }

    @Override
    public float[] embedImage(byte[] data, String filename, boolean crop) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("图片内容为空");
        }
        String boundary = "----ImageSearch" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, filename == null ? "image.jpg" : filename, data, crop);
        HttpRequest request = base("/embed/image")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return readEmbedding(send(request));
    }

    @Override
    public boolean cropDetectorReady() {
        HttpRequest request = base("/health").GET().build();
        try {
            JsonNode root = objectMapper.readTree(send(request));
            return root.path("crop_enabled").asBoolean(false) && root.path("crop_detector_ready").asBoolean(false);
        } catch (IllegalStateException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public float[] embedText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("检索文本为空");
        }
        String payload = "{\"text\":" + jsonString(text.trim()) + "}";
        HttpRequest request = base("/embed/text")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        return readEmbedding(send(request));
    }

    private HttpRequest.Builder base(String path) {
        String root = properties.getEmbedBaseUrl() == null ? "" : properties.getEmbedBaseUrl().replaceAll("/+$", "");
        return HttpRequest.newBuilder(URI.create(root + path))
                .timeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 1000)));
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("图片向量服务返回 " + response.statusCode() + " " + abbreviate(response.body()));
            }
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("图片向量服务不可用: " + e.getMessage(), e);
        }
    }

    private float[] readEmbedding(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode embedding = root.get("embedding");
            if (embedding == null || !embedding.isArray() || embedding.isEmpty()) {
                throw new IllegalStateException("图片向量服务没有返回 embedding");
            }
            if (embedding.size() != properties.getDimension()) {
                throw new IllegalStateException("向量维度是 " + embedding.size()
                        + "，配置 image-search.dimension=" + properties.getDimension());
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("解析图片向量失败: " + e.getMessage(), e);
        }
    }

    private static byte[] multipart(String boundary, String filename, byte[] data, boolean crop) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            String safeName = filename.replace("\"", "").replace("\r", "").replace("\n", "");
            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.UTF_8));
            out.write(data);
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            if (crop) {
                String cropPart = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"crop\"\r\n\r\n"
                        + "1\r\n";
                out.write(cropPart.getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("组装上传请求失败", e);
        }
    }

    private static String jsonString(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        builder.append('"');
        return builder.toString();
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String oneLine = body.replace('\n', ' ').replace('\r', ' ').trim();
        return oneLine.length() > 180 ? oneLine.substring(0, 180) : oneLine;
    }
}
