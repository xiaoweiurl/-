package com.imagemanager.controller;

import com.imagemanager.config.AuthInterceptor;
import com.imagemanager.config.StorageProperties;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.dto.StorageQuotaDTO;
import com.imagemanager.service.FileStorageService;
import com.imagemanager.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 存储配额控制器
 */
@Slf4j
@RestController
@RequestMapping("/storage")
public class StorageController {

    private final StorageService storageService;
    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;

    public StorageController(StorageService storageService,
                             @Qualifier("imageStorageService") FileStorageService fileStorageService,
                             StorageProperties storageProperties) {
        this.storageService = storageService;
        this.fileStorageService = fileStorageService;
        this.storageProperties = storageProperties;
    }

    /**
     * 获取当前用户的存储配额
     */
    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getMyQuota(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        StorageQuotaDTO quota = storageService.getUserQuota(userInfo.getId());
        
        result.put("success", true);
        result.put("data", quota);
        return ResponseEntity.ok(result);
    }

    /**
     * 更新用户存储配额（管理员）
     */
    @PutMapping("/quota/{userId}")
    public ResponseEntity<Map<String, Object>> updateQuota(
            @PathVariable String userId,
            @RequestParam Long maxStorageBytes,
            HttpServletRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        if (!"ADMIN".equalsIgnoreCase(userInfo.getRole())) {
            result.put("error", "无权限");
            return ResponseEntity.status(403).body(result);
        }

        storageService.updateQuota(userId, maxStorageBytes);
        
        result.put("success", true);
        result.put("message", "存储配额已更新");
        return ResponseEntity.ok(result);
    }

    /**
     * 获取系统存储统计（管理员）
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        if (!"ADMIN".equalsIgnoreCase(userInfo.getRole())) {
            result.put("error", "无权限");
            return ResponseEntity.status(403).body(result);
        }

        Map<String, Object> stats = storageService.getSystemStorageStats();
        
        result.put("success", true);
        result.put("data", stats);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有用户的存储使用情况（管理员）
     */
    @GetMapping("/quotas")
    public ResponseEntity<Map<String, Object>> getAllQuotas(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        if (!"ADMIN".equalsIgnoreCase(userInfo.getRole())) {
            result.put("error", "无权限");
            return ResponseEntity.status(403).body(result);
        }

        Page<StorageQuotaDTO> quotas = storageService.getAllUserQuotas(page, pageSize);
        
        result.put("success", true);
        result.put("data", quotas.getContent());
        result.put("total", quotas.getTotalElements());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ResponseEntity.ok(result);
    }

    /**
     * 重新计算用户存储使用量
     */
    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculateStorage(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        storageService.recalculateUsedStorage(userInfo.getId());
        
        result.put("success", true);
        result.put("message", "存储使用量已重新计算");
        return ResponseEntity.ok(result);
    }

    /**
     * 测试 S3/OSS 存储连接状态（管理员）
     */
    @GetMapping("/s3-test")
    public ResponseEntity<Map<String, Object>> testS3Connection(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        if (!"ADMIN".equalsIgnoreCase(userInfo.getRole())) {
            result.put("error", "无权限");
            return ResponseEntity.status(403).body(result);
        }

        // 存储类型信息
        String storageType = storageProperties.getType();
        result.put("storageType", storageType);
        
        if ("s3".equalsIgnoreCase(storageType)) {
            result.put("endpoint", storageProperties.getS3Endpoint());
            result.put("region", storageProperties.getS3Region());
            result.put("bucket", storageProperties.getS3BucketName());
            
            try {
                // 尝试上传一个测试文件（不删除，方便用户在OSS控制台验证）
                String testContent = "S3连接测试 - " + System.currentTimeMillis();
                byte[] testBytes = testContent.getBytes("UTF-8");
                String testKey = fileStorageService.uploadFile(testBytes, "s3-test.txt", "text/plain");
                result.put("uploadSuccess", true);
                result.put("testFileKey", testKey);
                
                // 尝试获取 URL
                String fileUrl = fileStorageService.getFileUrl(testKey);
                result.put("fileUrl", fileUrl);
                
                // 尝试生成预签名URL
                String presignedUrl = fileStorageService.generatePresignedUrl(testKey, 3600);
                result.put("presignedUrl", presignedUrl);
                
                // 不删除测试文件，方便用户到OSS控制台验证文件是否存在
                result.put("deleteSuccess", "跳过（保留测试文件供OSS控制台验证）");
                result.put("tip", "请到阿里云OSS控制台查看 yingyun-image-manager bucket 下是否有 images/s3-test.txt 文件");
                
                result.put("success", true);
                result.put("message", "S3/OSS 上传测试通过，请到OSS控制台验证文件是否实际存在");
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", e.getMessage());
                result.put("errorClass", e.getClass().getSimpleName());
                log.error("S3连接测试失败", e);
            }
        } else {
            result.put("success", true);
            result.put("message", "当前使用本地存储，非S3模式");
            result.put("localPath", storageProperties.getLocalPath());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 从 request 属性中获取用户信息
     */
    private LoginResponse.UserInfo getUserInfo(HttpServletRequest request) {
        return (LoginResponse.UserInfo) request.getAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE);
    }
}
