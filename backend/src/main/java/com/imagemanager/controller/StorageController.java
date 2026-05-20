package com.imagemanager.controller;

import com.imagemanager.dto.StorageQuotaDTO;
import com.imagemanager.service.StorageService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    /**
     * 获取当前用户的存储配额
     */
    @GetMapping("/quota")
    public ResponseEntity<Map<String, Object>> getMyQuota(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        StorageQuotaDTO quota = storageService.getUserQuota(userId);
        
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
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        
        String currentUserId = (String) session.getAttribute("userId");
        if (currentUserId == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        String role = (String) session.getAttribute("role");
        if (!"admin".equals(role)) {
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
    public ResponseEntity<Map<String, Object>> getSystemStats(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        String role = (String) session.getAttribute("role");
        if (!"admin".equals(role)) {
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
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        String role = (String) session.getAttribute("role");
        if (!"admin".equals(role)) {
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
    public ResponseEntity<Map<String, Object>> recalculateStorage(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        storageService.recalculateUsedStorage(userId);
        
        result.put("success", true);
        result.put("message", "存储使用量已重新计算");
        return ResponseEntity.ok(result);
    }
}
