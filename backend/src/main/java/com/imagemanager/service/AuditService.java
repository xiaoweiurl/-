package com.imagemanager.service;

import com.imagemanager.dto.AuditLogDTO;
import com.imagemanager.entity.AuditLog;
import org.springframework.data.domain.Page;

/**
 * 审计服务接口
 */
public interface AuditService {
    
    /**
     * 记录操作日志
     */
    void log(String action, String resourceType, String resourceId, String resourceName, String details, String userId);
    
    /**
     * 记录操作日志（带IP和User-Agent）
     */
    void log(String action, String resourceType, String resourceId, String resourceName, 
             String details, String userId, String ipAddress, String userAgent);
    
    /**
     * 记录失败的日志
     */
    void logFailure(String action, String resourceType, String resourceId, String resourceName, 
                    String errorMessage, String userId, String ipAddress, String userAgent);
    
    /**
     * 获取用户操作日志
     */
    Page<AuditLogDTO> getUserLogs(String userId, int page, int pageSize);
    
    /**
     * 搜索日志（管理员）
     */
    Page<AuditLogDTO> searchLogs(String userId, String action, String resourceType, 
                                  String startTime, String endTime, int page, int pageSize);
    
    /**
     * 获取所有操作类型
     */
    java.util.List<String> getAllActions();
    
    /**
     * 清理旧日志
     */
    void cleanOldLogs(int retentionDays);
}
