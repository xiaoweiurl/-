package com.imagemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * AI 图像生成控制器
 * 支持多个模型：
 * - nano-banana 系列：aspectRatio 用比例(1:1, 16:9...)，imageSize 用 1K/2K/4K
 * - gpt-image-2：aspectRatio 用像素值(1024x1024, 2048x1152...)
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

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 生成 AI 图像
     * POST /ai-image/generate
     *
     * 请求体:
     * {
     *   "model": "nano-banana-2" | "gpt-image-2" | ...,
     *   "prompt": "描述文本",
     *   "aspectRatio": "1:1" | "16:9" | "1024x1024" | ...,
     *   "imageSize": "1K" | "2K" | "4K",   // nano-banana 系列专用
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
            String aspectRatio = requestJson.has("aspectRatio") ? requestJson.get("aspectRatio").asText() : "1:1";

            if (prompt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\":\"提示词不能为空\"}");
            }

            // 构建API请求体
            ObjectNode apiRequestBody = objectMapper.createObjectNode();
            apiRequestBody.put("model", model);
            apiRequestBody.put("prompt", prompt);
            apiRequestBody.put("replyType", "json");

            // 根据 model 类型设置参数
            if (model.startsWith("nano-banana")) {
                // nano-banana 系列：aspectRatio 用比例，imageSize 用 1K/2K/4K
                apiRequestBody.put("aspectRatio", aspectRatio);
                String imageSize = requestJson.has("imageSize") ? requestJson.get("imageSize").asText() : "1K";
                apiRequestBody.put("imageSize", imageSize);
            } else if (model.startsWith("gpt-image")) {
                // gpt-image 系列：aspectRatio 直接用像素值
                apiRequestBody.put("aspectRatio", aspectRatio);
            } else {
                // 其他模型默认用比例
                apiRequestBody.put("aspectRatio", aspectRatio);
                String imageSize = requestJson.has("imageSize") ? requestJson.get("imageSize").asText() : "1K";
                if (!imageSize.isEmpty()) {
                    apiRequestBody.put("imageSize", imageSize);
                }
            }

            // images 字段
            if (requestJson.has("images") && requestJson.get("images").isArray()) {
                apiRequestBody.set("images", requestJson.get("images"));
            } else {
                apiRequestBody.putArray("images");
            }

            String apiRequestBodyStr = objectMapper.writeValueAsString(apiRequestBody);
            log.info("AI生图请求: model={}, aspectRatio={}, imageSize={}, prompt长度={}",
                    model, aspectRatio,
                    requestJson.has("imageSize") ? requestJson.get("imageSize").asText() : "N/A",
                    prompt.length());

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", apiKey);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<String> entity = new HttpEntity<>(apiRequestBodyStr, headers);

            // 发送请求到外部API（超时5分钟，生图可能较慢）
            RestTemplate slowRestTemplate = new RestTemplate();
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            javax.net.ssl.SSLSocketFactory socketFactory = sslContext.getSocketFactory();

            org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                    new org.springframework.http.client.SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(30 * 1000);  // 30秒连接超时
            factory.setReadTimeout(5 * 60 * 1000);  // 5分钟读取超时
            slowRestTemplate.setRequestFactory(factory);

            ResponseEntity<String> response = slowRestTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("AI生图API调用失败: status={}, body={}", response.getStatusCode(), response.getBody());
                return ResponseEntity.status(response.getStatusCode())
                        .body("{\"error\":\"AI生图服务调用失败: " + response.getStatusCode() + "\"}");
            }

            log.info("AI生图成功: model={}", model);
            // 直接返回API响应
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());

        } catch (Exception e) {
            log.error("AI生图请求异常", e);
            return ResponseEntity.status(500)
                    .body("{\"error\":\"AI生图服务异常: " + e.getMessage() + "\"}");
        }
    }

    /**
     * 获取支持的模型列表
     * GET /ai-image/models
     */
    @GetMapping("/models")
    public ResponseEntity<?> getModels() {
        try {
            ObjectNode response = objectMapper.createObjectNode();

            // nano-banana 系列模型
            ObjectNode nanoBanana = response.putObject("nanoBanana");
            nanoBanana.putArray("models")
                    .add("nano-banana")
                    .add("nano-banana-fast")
                    .add("nano-banana-2")
                    .add("nano-banana-2-cl")
                    .add("nano-banana-2-4k-cl")
                    .add("nano-banana-pro")
                    .add("nano-banana-pro-cl")
                    .add("nano-banana-pro-vip")
                    .add("nano-banana-pro-4k-vip");
            nanoBanana.putArray("aspectRatios")
                    .add("auto").add("1:1").add("16:9").add("9:16")
                    .add("4:3").add("3:4").add("3:2").add("2:3")
                    .add("5:4").add("4:5").add("21:9")
                    .add("1:4").add("4:1").add("1:8").add("8:1");
            nanoBanana.putArray("imageSizes")
                    .add("1K").add("2K").add("4K");

            // gpt-image 系列模型
            ObjectNode gptImage = response.putObject("gptImage");
            gptImage.putArray("models")
                    .add("gpt-image-2");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.error("获取模型列表异常", e);
            return ResponseEntity.status(500)
                    .body("{\"error\":\"获取模型列表失败\"}");
        }
    }
}
