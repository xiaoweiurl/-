package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 审计日志实体
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_audit_log_table_name", columnList = "table_name"),
    @Index(name = "idx_audit_log_operation", columnList = "operation"),
    @Index(name = "idx_audit_log_record_id", columnList = "record_id"),
    @Index(name = "idx_audit_log_changed_by", columnList = "changed_by"),
    @Index(name = "idx_audit_log_changed_at", columnList = "changed_at")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 表名
     */
    @Column(name = "table_name", length = 100, nullable = false)
    private String tableName;
    
    /**
     * 操作类型：INSERT, UPDATE, DELETE
     */
    @Column(length = 20, nullable = false)
    private String operation;
    
    /**
     * 记录ID
     */
    @Column(name = "record_id", length = 36)
    private String recordId;
    
    /**
     * 修改前的值（JSON格式）
     */
    @Column(name = "old_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> oldValue;
    
    /**
     * 修改后的值（JSON格式）
     */
    @Column(name = "new_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> newValue;
    
    /**
     * 操作用户ID
     */
    @Column(name = "changed_by", length = 36)
    private String changedBy;
    
    /**
     * 操作用户名（冗余字段）
     */
    @Column(name = "changed_by_username", length = 100)
    private String changedByUsername;
    
    /**
     * 操作时间
     */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;
    
    /**
     * 客户端IP
     */
    @Column(name = "client_ip", length = 50)
    private String clientIp;
    
    /**
     * 用户代理
     */
    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;
    
    /**
     * 请求路径
     */
    @Column(name = "request_path", length = 255)
    private String requestPath;
    
    /**
     * 请求方法
     */
    @Column(name = "request_method", length = 10)
    private String requestMethod;
    
    /**
     * 描述
     */
    @Column(columnDefinition = "text")
    private String description;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    /**
     * 在保存前设置创建时间
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (changedAt == null) {
            changedAt = LocalDateTime.now();
        }
        if (id == null) {
            id = java.util.UUID.randomUUID().toString();
        }
    }
}
