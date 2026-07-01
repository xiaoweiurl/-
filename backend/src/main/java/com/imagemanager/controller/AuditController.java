package com.imagemanager.controller;

import com.imagemanager.config.AuthInterceptor;
import com.imagemanager.dto.AuditLogDTO;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * 获取审计日志列表（根路径，管理员可看全部，普通用户看自己的）
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getLogsRoot(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return getMyLogs(page, pageSize, request);
    }

    /**
     * 获取当前用户的操作日志
     */
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        Page<AuditLogDTO> logs = auditService.getUserLogs(userInfo.getId(), page, pageSize);
        
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
            HttpServletRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        // 检查管理员权限
        if (!"ADMIN".equalsIgnoreCase(userInfo.getRole())) {
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
    public ResponseEntity<Map<String, Object>> getAllActions(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
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

        auditService.cleanOldLogs(retentionDays);
        
        result.put("success", true);
        result.put("message", "已清理 " + retentionDays + " 天前的日志");
        return ResponseEntity.ok(result);
    }

    /**
     * 从 request 属性中获取用户信息
     */
    private LoginResponse.UserInfo getUserInfo(HttpServletRequest request) {
        return (LoginResponse.UserInfo) request.getAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE);
    }
}
