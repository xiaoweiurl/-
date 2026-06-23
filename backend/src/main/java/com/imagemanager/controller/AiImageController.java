package com.imagemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/**
 * AI 图像生成控制器
 * 支持 gpt-image-2 和 nano-banana-2 模型
 */
@Slf4j
@RestController
@RequestMapping("/ai-image")
public class AiImageController {

    @Value("${app.ai-image.api-url:https://grsaiapi.com/v1/api/generate}")
    private String apiUrl;

    @Value("${app.ai-image.api-key:sk-40901f63b84840338584ef2115cecbd1}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 生成 AI 图像
     * POST /ai-image/generate
     *
     * 请求体:
     * {
     *   "model": "gpt-image-2" | "nano-banana-2",
     *   "prompt": "描述文本",
     *   "aspectRatio": "1024x1024",
     *   "images": []  // 可选，用于图生图
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody String requestBody,
                                      HttpServletRequest servletRequest) {
        try {
            // 解析请求参数
            JsonNode requestJson = objectMapper.readTree(requestBody);
            String model = requestJson.has("model") ? requestJson.get("model").asText() : "nano-banana-2";
            String prompt = requestJson.has("prompt") ? requestJson.get("prompt").asText() : "";
            String aspectRatio = requestJson.has("aspectRatio") ? requestJson.get("aspectRatio").asText() : "1024x1024";

            if (prompt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\":\"提示词不能为空\"}");
            }

            // 构建API请求体
            ObjectNode apiRequestBody = objectMapper.createObjectNode();
            apiRequestBody.put("model", model);
            apiRequestBody.put("prompt", prompt);
            apiRequestBody.put("aspectRatio", aspectRatio);
            apiRequestBody.put("replyType", "json");

            // images 字段
            if (requestJson.has("images") && requestJson.get("images").isArray()) {
                apiRequestBody.set("images", requestJson.get("images"));
            } else {
                apiRequestBody.putArray("images");
            }

            String apiRequestBodyStr = objectMapper.writeValueAsString(apiRequestBody);
            log.info("AI生图请求: model={}, aspectRatio={}, prompt长度={}", model, aspectRatio, prompt.length());

            // 发送请求到外部API
            MediaType mediaType = MediaType.parse("application/json");
            RequestBody body = RequestBody.create(mediaType, apiRequestBodyStr);

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .method("POST", body)
                    .addHeader("Authorization", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("AI生图API调用失败: status={}, body={}", response.code(), responseBody);
                    return ResponseEntity.status(response.code())
                            .body("{\"error\":\"AI生图服务调用失败: " + response.code() + "\"}");
                }

                log.info("AI生图成功: model={}", model);
                // 直接返回API响应
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(responseBody);
            }

        } catch (IOException e) {
            log.error("AI生图请求异常", e);
            return ResponseEntity.status(500)
                    .body("{\"error\":\"AI生图服务异常: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            log.error("AI生图参数解析异常", e);
            return ResponseEntity.badRequest()
                    .body("{\"error\":\"请求参数异常: " + e.getMessage() + "\"}");
        }
    }
}
