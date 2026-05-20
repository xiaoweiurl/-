package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志实体
 */
@Data
@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id
    private String id;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "resource_type", length = 20)
    private String resourceType;

    @Column(name = "resource_id", length = 36)
    private String resourceId;

    @Column(name = "resource_name", length = 255)
    private String resourceName;

    @Column(name = "details", columnDefinition = "JSON")
    private String details;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "status", length = 20)
    private String status = "success";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /**
     * 操作类型枚举
     */
    public static class ActionType {
        // 用户相关
        public static final String LOGIN = "login";
        public static final String LOGOUT = "logout";
        public static final String REGISTER = "register";
        public static final String CHANGE_PASSWORD = "change_password";
        public static final String UPDATE_PROFILE = "update_profile";

        // 图片相关
        public static final String UPLOAD_IMAGE = "upload_image";
        public static final String DELETE_IMAGE = "delete_image";
        public static final String RESTORE_IMAGE = "restore_image";
        public static final String DOWNLOAD_IMAGE = "download_image";
        public static final String EDIT_IMAGE = "edit_image";
        public static final String MOVE_IMAGE = "move_image";
        public static final String BATCH_DELETE = "batch_delete";
        public static final String BATCH_DOWNLOAD = "batch_download";

        // 相册相关
        public static final String CREATE_ALBUM = "create_album";
        public static final String DELETE_ALBUM = "delete_album";
        public static final String UPDATE_ALBUM = "update_album";
        public static final String SHARE_ALBUM = "share_album";

        // 分享相关
        public static final String CREATE_SHARE = "create_share";
        public static final String ACCESS_SHARE = "access_share";
        public static final String DOWNLOAD_SHARE = "download_share";

        // 系统相关
        public static final String BACKUP_DATA = "backup_data";
        public static final String RESTORE_DATA = "restore_data";
        public static final String UPDATE_SETTINGS = "update_settings";
        public static final String MANAGE_USER = "manage_user";
    }
}
