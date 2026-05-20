package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.AuditLogDTO;
import com.imagemanager.entity.AuditLog;
import com.imagemanager.entity.User;
import com.imagemanager.repository.AuditLogRepository;
import com.imagemanager.repository.UserRepository;
import com.imagemanager.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Async
    @Transactional
    public void log(String action, String resourceType, String resourceId, String resourceName, 
                    String details, String userId) {
        log(action, resourceType, resourceId, resourceName, details, userId, null, null);
    }

    @Override
    @Async
    @Transactional
    public void log(String action, String resourceType, String resourceId, String resourceName, 
                    String details, String userId, String ipAddress, String userAgent) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setResourceName(resourceName);
            // 确保 details 是有效的 JSON 格式
            auditLog.setDetails(toJsonDetails(details));
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setStatus("success");

            // 获取用户名
            if (userId != null) {
                userRepository.findById(userId)
                        .ifPresent(user -> auditLog.setUsername(user.getUsername()));
            }

            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: {} - {} - {}", action, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }

    /**
     * 将详情字符串转换为 JSON 格式
     */
    private String toJsonDetails(String details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        // 如果已经是 JSON 格式（以 { 或 [ 开头），直接返回
        String trimmed = details.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return details;
        }
        // 否则包装成 JSON 对象
        try {
            return objectMapper.writeValueAsString(new java.util.HashMap<String, String>() {{
                put("message", details);
            }});
        } catch (Exception e) {
            return "{\"message\":\"" + details.replace("\"", "\\\"") + "\"}";
        }
    }

    @Override
    @Async
    @Transactional
    public void logFailure(String action, String resourceType, String resourceId, String resourceName, 
                           String errorMessage, String userId, String ipAddress, String userAgent) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setResourceName(resourceName);
            // 构造错误详情 JSON
            auditLog.setDetails(toJsonDetails("操作失败: " + errorMessage));
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setStatus("failed");
            auditLog.setErrorMessage(errorMessage);

            if (userId != null) {
                userRepository.findById(userId)
                        .ifPresent(user -> auditLog.setUsername(user.getUsername()));
            }

            auditLogRepository.save(auditLog);
            log.debug("Audit failure log saved: {} - {} - {}", action, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to save audit failure log: {}", e.getMessage());
        }
    }

    @Override
    public Page<AuditLogDTO> getUserLogs(String userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        return auditLogRepository.findByUserId(userId, pageable)
                .map(this::toDTO);
    }

    @Override
    public Page<AuditLogDTO> searchLogs(String userId, String action, String resourceType, 
                                         String startTime, String endTime, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        
        LocalDateTime start = startTime != null ? 
                LocalDateTime.parse(startTime, DateTimeFormatter.ISO_DATE_TIME) : 
                LocalDateTime.now().minusDays(30);
        LocalDateTime end = endTime != null ? 
                LocalDateTime.parse(endTime, DateTimeFormatter.ISO_DATE_TIME) : 
                LocalDateTime.now();

        return auditLogRepository.searchLogs(userId, action, resourceType, start, end, pageable)
                .map(this::toDTO);
    }

    @Override
    public List<String> getAllActions() {
        return auditLogRepository.findAllDistinctActions();
    }

    @Override
    @Transactional
    public void cleanOldLogs(int retentionDays) {
        LocalDateTime before = LocalDateTime.now().minusDays(retentionDays);
        auditLogRepository.deleteByCreatedAtBefore(before);
        log.info("Cleaned audit logs older than {} days", retentionDays);
    }

    private AuditLogDTO toDTO(AuditLog auditLog) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.setId(auditLog.getId());
        dto.setUserId(auditLog.getUserId());
        dto.setUsername(auditLog.getUsername());
        dto.setAction(auditLog.getAction());
        dto.setResourceType(auditLog.getResourceType());
        dto.setResourceId(auditLog.getResourceId());
        dto.setResourceName(auditLog.getResourceName());
        dto.setDetails(auditLog.getDetails());
        dto.setIpAddress(auditLog.getIpAddress());
        dto.setUserAgent(auditLog.getUserAgent());
        dto.setStatus(auditLog.getStatus());
        dto.setErrorMessage(auditLog.getErrorMessage());
        dto.setCreatedAt(auditLog.getCreatedAt());
        dto.setTimeAgo(calculateTimeAgo(auditLog.getCreatedAt()));
        return dto;
    }

    private String calculateTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        
        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long minutes = duration.toMinutes();
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        
        long hours = duration.toHours();
        if (hours < 24) return hours + "小时前";
        
        long days = duration.toDays();
        if (days < 30) return days + "天前";
        
        long months = days / 30;
        if (months < 12) return months + "个月前";
        
        long years = days / 365;
        return years + "年前";
    }
}
