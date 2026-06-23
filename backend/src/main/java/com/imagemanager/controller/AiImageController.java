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

    private final RestTemplate restTemplate = createRestTemplate();

    private RestTemplate createRestTemplate() {
        RestTemplate rt = new RestTemplate();
        // 设置超时
        javax.net.ssl.SSLContext sslContext = null;
        try {
            sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
        } catch (Exception e) {
            log.warn("SSL初始化失败", e);
        }
        return rt;
    }

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

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", apiKey);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<String> entity = new HttpEntity<>(apiRequestBodyStr, headers);

            // 发送请求到外部API
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

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
}
