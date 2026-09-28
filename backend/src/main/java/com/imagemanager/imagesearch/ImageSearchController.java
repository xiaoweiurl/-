package com.imagemanager.imagesearch;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.util.SessionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 以图搜图。打样会话被 SamplerSessionGuard 拒绝；评测仅管理员。
 */
@Slf4j
@RestController
@RequestMapping("/image-search")
public class ImageSearchController {

    private final ImageSearchProperties properties;
    private final ImageSearchQueryService queryService;
    private final ImageSearchEvalService evalService;

    public ImageSearchController(ImageSearchProperties properties,
                                 ImageSearchQueryService queryService,
                                 ImageSearchEvalService evalService) {
        this.properties = properties;
        this.queryService = queryService;
        this.evalService = evalService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", properties.isEnabled());
        return ApiResponse.success(body);
    }

    @PostMapping(value = "/query", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageSearchModels.ImageSearchResponse>> query(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "scope", required = false, defaultValue = "all") String scope,
            @RequestParam(value = "topK", required = false) Integer topK) {
        if (!properties.isEnabled()) {
            return ResponseEntity.status(503).body(ApiResponse.error(503, "以图搜图未开启"));
        }
        try {
            byte[] bytes = null;
            String filename = null;
            if (file != null && !file.isEmpty()) {
                String contentType = file.getContentType() == null ? "" : file.getContentType();
                String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
                boolean imageType = contentType.startsWith("image/")
                        || original.endsWith(".jpg") || original.endsWith(".jpeg") || original.endsWith(".png")
                        || original.endsWith(".gif") || original.endsWith(".webp") || original.endsWith(".bmp");
                if (!imageType) {
                    return ResponseEntity.badRequest().body(ApiResponse.error(400, "请上传 jpg、png、webp、gif 或 bmp 图片"));
                }
                bytes = file.getBytes();
                filename = file.getOriginalFilename();
            }
            ImageSearchModels.ImageSearchResponse result = queryService.search(
                    bytes, filename, text, scope, topK, SessionUtil.getCurrentCompany());
            return ResponseEntity.ok(ApiResponse.success("检索完成", result));
        } catch (ImageSearchDisabledException e) {
            return ResponseEntity.status(503).body(ApiResponse.error(503, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.warn("以图搜图失败: {}", e.getMessage());
            String message = e.getMessage() == null ? "以图搜图暂不可用" : e.getMessage();
            if (message.length() > 180) {
                message = message.substring(0, 180);
            }
            return ResponseEntity.status(503).body(ApiResponse.error(503, message));
        }
    }

    /**
     * 只读评测。管理员由 AuthInterceptor.requiresAdmin 限制。
     */
    @PostMapping("/eval")
    public ResponseEntity<ApiResponse<Map<String, Object>>> eval(
            @RequestParam(value = "groups", defaultValue = "5") int groups,
            @RequestParam(value = "synthetic", defaultValue = "3") int synthetic,
            @RequestParam(value = "k", defaultValue = "10") int k) {
        if (!properties.isEnabled()) {
            return ResponseEntity.status(503).body(ApiResponse.error(503, "以图搜图未开启"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success("评测完成", evalService.evaluate(groups, synthetic, k)));
        } catch (Exception e) {
            log.warn("以图搜图评测失败: {}", e.getMessage());
            return ResponseEntity.status(503).body(ApiResponse.error(503,
                    e.getMessage() == null ? "评测失败" : e.getMessage()));
        }
    }
}
