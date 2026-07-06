package com.imagemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import com.imagemanager.entity.Image;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.repository.ImageDynamicRepository;
import com.imagemanager.service.ImageTableService;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final ImageRepository imageRepository;
    private final ImageDynamicRepository imageDynamicRepository;
    private final ImageTableService imageTableService;

    public AiImageController(ImageRepository imageRepository,
                             ImageDynamicRepository imageDynamicRepository,
                             ImageTableService imageTableService) {
        this.imageRepository = imageRepository;
        this.imageDynamicRepository = imageDynamicRepository;
        this.imageTableService = imageTableService;
    }

    /**
     * 生成 AI 图像（支持异步轮询）
     * POST /ai-image/generate
     *
     * 外部 API 流程：
     * 1. POST 提交任务 → 返回 { id, status: "running" } 或 { id, status: "succeeded", results: [...] }
     * 2. 如果 status != "succeeded"，轮询 GET /v1/api/generate/{id} 直到完成
     * 3. 最终结果中 results: [{ url }] 包含图片
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
            int count = requestJson.has("count") ? requestJson.get("count").asInt() : 1;
            count = Math.max(1, Math.min(count, 4));

            if (prompt.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\":\"提示词不能为空\"}");
            }

            // 构建API请求体
            ObjectNode apiRequestBody = objectMapper.createObjectNode();
            apiRequestBody.put("model", model);
            apiRequestBody.put("prompt", prompt);
            apiRequestBody.put("replyType", "json");

            if (model.startsWith("nano-banana")) {
                apiRequestBody.put("aspectRatio", aspectRatio);
                String imageSize = requestJson.has("imageSize") ? requestJson.get("imageSize").asText() : "1K";
                apiRequestBody.put("imageSize", imageSize);
            } else if (model.startsWith("gpt-image")) {
                apiRequestBody.put("aspectRatio", aspectRatio);
            } else {
                apiRequestBody.put("aspectRatio", aspectRatio);
                String imageSize = requestJson.has("imageSize") ? requestJson.get("imageSize").asText() : "1K";
                if (!imageSize.isEmpty()) {
                    apiRequestBody.put("imageSize", imageSize);
                }
            }

            if (requestJson.has("images") && requestJson.get("images").isArray()) {
                apiRequestBody.set("images", requestJson.get("images"));
            } else {
                apiRequestBody.putArray("images");
            }

            String apiRequestBodyStr = objectMapper.writeValueAsString(apiRequestBody);
            int imagesCount = requestJson.has("images") && requestJson.get("images").isArray() ? requestJson.get("images").size() : 0;
            log.info("AI生图请求: model={}, prompt长度={}, 参考图片数={}, 生成数量={}", model, prompt.length(), imagesCount, count);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", apiKey);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            // 并发生成，每张独立提交+轮询
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                final int index = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        // 第一步：提交生图任务
                        HttpEntity<String> entity = new HttpEntity<>(apiRequestBodyStr, headers);
                        ResponseEntity<String> resp = createSlowRestTemplate().exchange(apiUrl, HttpMethod.POST, entity, String.class);

                        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                            log.warn("AI生图第{}张提交失败: status={}", index + 1, resp.getStatusCode());
                            return null;
                        }

                        JsonNode resultJson = objectMapper.readTree(resp.getBody());

                        // 第二步：如果异步任务，轮询等待结果
                        resultJson = pollUntilComplete(resultJson, headers);

                        if (resultJson == null) {
                            log.warn("AI生图第{}张轮询超时", index + 1);
                            return null;
                        }

                        // 第三步：从最终结果提取图片 URL
                        String imageUrl = extractImageUrl(resultJson);
                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            Map<String, Object> img = new HashMap<>();
                            img.put("url", imageUrl);
                            img.put("index", index);
                            // 提取 revised_prompt
                            if (resultJson.has("results") && resultJson.get("results").isArray() && resultJson.get("results").size() > 0) {
                                JsonNode firstResult = resultJson.get("results").get(0);
                                if (firstResult.has("revised_prompt")) {
                                    img.put("revised_prompt", firstResult.get("revised_prompt").asText());
                                }
                            }
                            if (resultJson.has("data") && resultJson.get("data").isObject()) {
                                JsonNode dataNode = resultJson.get("data");
                                if (dataNode.has("revised_prompt")) {
                                    img.put("revised_prompt", dataNode.get("revised_prompt").asText());
                                }
                            }
                            return img;
                        }

                        log.warn("AI生图第{}张: 无法提取图片URL, 响应={}", index + 1, resultJson.toString().substring(0, Math.min(200, resultJson.toString().length())));
                        return null;
                    } catch (Exception e) {
                        log.error("AI生图第{}张异常: {}", index + 1, e.getMessage());
                        return null;
                    }
                }));
            }

            // 等待全部完成（最多 5 分钟，由 RestTemplate readTimeout 控制）
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // 收集结果
            List<Map<String, Object>> images = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < futures.size(); i++) {
                try {
                    Map<String, Object> img = futures.get(i).getNow(null);
                    if (img != null) {
                        images.add(img);
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("获取第{}张生图结果异常: {}", i + 1, e.getMessage());
                    failCount++;
                }
            }

            // 构建响应（统一格式，无论 count 是多少）
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("success", successCount > 0);
            responseMap.put("count", count);
            responseMap.put("successCount", successCount);
            responseMap.put("failCount", failCount);
            responseMap.put("images", images);
            responseMap.put("model", model);

            log.info("AI生图完成: model={}, 成功={}, 失败={}", model, successCount, failCount);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(responseMap));

        } catch (Exception e) {
            log.error("AI生图请求异常", e);
            return ResponseEntity.status(500)
                    .body("{\"error\":\"AI生图服务异常: " + e.getMessage() + "\"}");
        }
    }

    /**
     * 轮询异步任务直到完成
     * 外部API可能返回 { id, status: "running" }，需要轮询到 status: "succeeded"
     */
    private JsonNode pollUntilComplete(JsonNode initialResponse, HttpHeaders headers) {
        String status = initialResponse.has("status") ? initialResponse.get("status").asText() : "";

        // 已经是成功状态，直接返回
        if ("succeeded".equals(status)) {
            return initialResponse;
        }

        // 如果没有 id 字段，说明不是异步任务，直接返回原始响应
        if (!initialResponse.has("id")) {
            return initialResponse;
        }

        String taskId = initialResponse.get("id").asText();
        log.info("AI生图异步任务已提交: taskId={}, status={}", taskId, status);

        // 轮询查询结果，最多 120 秒
        String queryUrl = apiUrl + "/" + taskId;
        int maxRetries = 60;  // 60次 × 2秒 = 120秒
        int interval = 2000;  // 2秒间隔

        for (int i = 0; i < maxRetries; i++) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }

            try {
                HttpEntity<String> entity = new HttpEntity<>(headers);
                RestTemplate queryRestTemplate = new RestTemplate();
                ResponseEntity<String> queryResp = queryRestTemplate.exchange(queryUrl, HttpMethod.GET, entity, String.class);

                if (!queryResp.getStatusCode().is2xxSuccessful() || queryResp.getBody() == null) {
                    log.warn("AI生图轮询第{}次失败: status={}", i + 1, queryResp.getStatusCode());
                    continue;
                }

                JsonNode queryResult = objectMapper.readTree(queryResp.getBody());
                String currentStatus = queryResult.has("status") ? queryResult.get("status").asText() : "";

                log.info("AI生图轮询第{}次: taskId={}, status={}", i + 1, taskId, currentStatus);

                if ("succeeded".equals(currentStatus)) {
                    return queryResult;
                }

                if ("failed".equals(currentStatus) || "violation".equals(currentStatus)) {
                    String error = queryResult.has("error") ? queryResult.get("error").asText() : "生成失败";
                    log.error("AI生图任务失败: taskId={}, status={}, error={}", taskId, currentStatus, error);
                    return null;
                }

                // status 仍然是 running，继续轮询
            } catch (Exception e) {
                log.warn("AI生图轮询第{}次异常: {}", i + 1, e.getMessage());
            }
        }

        log.error("AI生图轮询超时: taskId={}", taskId);
        return null;
    }

    /**
     * 从API响应中提取图片URL
     */
    private String extractImageUrl(JsonNode resultJson) {
        // data.url
        if (resultJson.has("data") && resultJson.get("data").isObject()) {
            JsonNode data = resultJson.get("data");
            if (data.has("url")) return data.get("url").asText();
            if (data.has("image_url")) return data.get("image_url").asText();
            if (data.has("b64_json")) return "data:image/png;base64," + data.get("b64_json").asText();
        }
        // 顶层 url / image_url
        if (resultJson.has("url")) return resultJson.get("url").asText();
        if (resultJson.has("image_url")) return resultJson.get("image_url").asText();
        if (resultJson.has("b64_json")) return "data:image/png;base64," + resultJson.get("b64_json").asText();
        // data 是数组
        if (resultJson.has("data") && resultJson.get("data").isArray() && resultJson.get("data").size() > 0) {
            JsonNode first = resultJson.get("data").get(0);
            if (first.has("url")) return first.get("url").asText();
            if (first.has("image_url")) return first.get("image_url").asText();
            if (first.has("b64_json")) return "data:image/png;base64," + first.get("b64_json").asText();
        }
        // results 数组（异步任务完成后返回格式: { results: [{ url }] }）
        if (resultJson.has("results") && resultJson.get("results").isArray() && resultJson.get("results").size() > 0) {
            JsonNode first = resultJson.get("results").get(0);
            if (first.has("url")) return first.get("url").asText();
        }
        // images 数组
        if (resultJson.has("images") && resultJson.get("images").isArray() && resultJson.get("images").size() > 0) {
            JsonNode first = resultJson.get("images").get(0);
            if (first.has("url")) return first.get("url").asText();
        }
        // output.url（某些模型格式）
        if (resultJson.has("output") && resultJson.get("output").isObject()) {
            JsonNode output = resultJson.get("output");
            if (output.has("url")) return output.get("url").asText();
        }
        // 正则匹配
        String jsonStr = resultJson.toString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s\"']+?\\.(png|jpg|jpeg|webp)").matcher(jsonStr);
        if (m.find()) return m.group();
        return null;
    }

    /**
     * 创建超时5分钟的 RestTemplate
     */
    private RestTemplate createSlowRestTemplate() throws Exception {
        RestTemplate slowRestTemplate = new RestTemplate();
        javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);

        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30 * 1000);
        factory.setReadTimeout(5 * 60 * 1000);
        slowRestTemplate.setRequestFactory(factory);
        return slowRestTemplate;
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
                    .add("gpt-image-2")
                    .add("gpt-image-2-vip");
            // gpt-image-2 支持比例格式和像素格式
            gptImage.put("standardSupportsRatio", true);
            gptImage.put("standardSupportsPixel", true);
            // gpt-image-2-vip 只支持像素格式
            gptImage.put("vipSupportsRatio", false);
            gptImage.put("vipSupportsPixel", true);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.error("获取模型列表异常", e);
            return ResponseEntity.status(500)
                    .body("{\"error\":\"获取模型列表失败\"}");
        }
    }

    /**
     * 保存AI生成图片到二创中心（图片库）
     * POST /ai-image/save-to-gallery
     *
     * 请求体:
     * {
     *   "imageUrl": "https://...",
     *   "prompt": "描述文本",
     *   "model": "nano-banana-2",
     *   "aspectRatio": "1:1",
     *   "imageSize": "1K"
     * }
     */
    @PostMapping("/save-to-gallery")
    public ResponseEntity<?> saveToGallery(@RequestBody String requestBody,
                                           HttpServletRequest servletRequest) {
        try {
            // 从session获取用户信息
            String userId = SessionUtil.getCurrentUserId();
            String company = SessionUtil.getCurrentCompany();
            String username = SessionUtil.getCurrentUsername();

            if (userId == null) {
                return ResponseEntity.status(401)
                        .body("{\"error\":\"请先登录\"}");
            }

            JsonNode requestJson = objectMapper.readTree(requestBody);
            String imageUrl = requestJson.has("imageUrl") ? requestJson.get("imageUrl").asText() : "";
            String prompt = requestJson.has("prompt") ? requestJson.get("prompt").asText() : "";
            String model = requestJson.has("model") ? requestJson.get("model").asText() : "unknown";
            String aspectRatio = requestJson.has("aspectRatio") ? requestJson.get("aspectRatio").asText() : "";
            String imageSize = requestJson.has("imageSize") ? requestJson.get("imageSize").asText() : "";

            if (imageUrl.isEmpty()) {
                return ResponseEntity.badRequest().body("{\"error\":\"图片地址不能为空\"}");
            }

            // 创建图片记录
            Image image = new Image();
            image.setId(UUID.randomUUID().toString());
            image.setUrl(imageUrl);
            image.setOriginalUrl(imageUrl);
            image.setThumbnailUrl(imageUrl);
            image.setTitle(prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
            image.setOriginalName("ai-generated-" + System.currentTimeMillis() + ".png");
            image.setAlbumName("二创中心");
            image.setUserId(userId);
            image.setCompany(company != null ? company : "盈云");
            image.setSource("creative"); // 标记为二创图片
            image.setFavorite(false);
            image.setDeleted(false);
            image.setCreatedAt(LocalDateTime.now());
            image.setUpdatedAt(LocalDateTime.now());
            image.setViewCount(0);
            image.setDownloadCount(0);
            image.setDisplayOrder(0);
            image.setIsMainImage(false);

            // 标记为AI生成
            image.setClassifyMethod("ai-generate");
            if (!model.isEmpty()) {
                image.setAiTags(Collections.singletonList("AI:" + model));
            }

            imageRepository.save(image);

            // 同步到用户的动态表（二创中心/我的二创）
            try {
                if (username != null) {
                    imageTableService.ensureUserImageTable(username);
                    imageDynamicRepository.save(image, username);
                    log.info("AI生成图片已同步到用户动态表: username={}", username);
                }
            } catch (Exception e) {
                log.warn("同步到用户动态表失败（不影响主表保存）: {}", e.getMessage());
            }

            log.info("AI生成图片已保存到二创中心: imageId={}, userId={}, company={}", image.getId(), userId, company);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("success", true);
            result.put("imageId", image.getId());
            result.put("message", "已保存到二创中心");
            return ResponseEntity.ok(objectMapper.writeValueAsString(result));

        } catch (Exception e) {
            log.error("保存AI图片到二创中心异常", e);
            return ResponseEntity.status(500)
                    .body("{\"error\":\"保存失败: " + e.getMessage() + "\"}");
        }
    }
}
