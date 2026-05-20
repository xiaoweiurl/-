package com.imagemanager.controller;

import com.imagemanager.dto.AuditLogDTO;
import com.imagemanager.service.AuditService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计日志控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * 获取当前用户的操作日志
     */
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        Page<AuditLogDTO> logs = auditService.getUserLogs(userId, page, pageSize);
        
        result.put("success", true);
        result.put("data", logs.getContent());
        result.put("total", logs.getTotalElements());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ResponseEntity.ok(result);
    }

    /**
     * 搜索日志（管理员）
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchLogs(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        
        String currentUserId = (String) session.getAttribute("userId");
        if (currentUserId == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        // 检查管理员权限
        String role = (String) session.getAttribute("role");
        if (!"admin".equals(role)) {
            result.put("error", "无权限");
            return ResponseEntity.status(403).body(result);
        }

        Page<AuditLogDTO> logs = auditService.searchLogs(
                userId, action, resourceType, startTime, endTime, page, pageSize);
        
        result.put("success", true);
        result.put("data", logs.getContent());
        result.put("total", logs.getTotalElements());
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有操作类型
     */
    @GetMapping("/actions")
    public ResponseEntity<Map<String, Object>> getAllActions(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        
        String userId = (String) session.getAttribute("userId");
        if (userId == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        List<String> actions = auditService.getAllActions();
        
        result.put("success", true);
        result.put("data", actions);
        return ResponseEntity.ok(result);
    }

    /**
     * 清理旧日志（管理员）
     */
    @DeleteMapping("/clean")
    public ResponseEntity<Map<String, Object>> cleanOldLogs(
            @RequestParam(defaultValue = "90") int retentionDays,
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

        auditService.cleanOldLogs(retentionDays);
        
        result.put("success", true);
        result.put("message", "已清理 " + retentionDays + " 天前的日志");
        return ResponseEntity.ok(result);
    }
}
