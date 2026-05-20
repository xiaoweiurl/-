package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统设置实体
 */
@Data
@Entity
@Table(name = "system_settings")
public class SystemSetting {
    @Id
    private String id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "setting_value", columnDefinition = "TEXT")
    private String settingValue;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 设置键枚举
     */
    public static class SettingKey {
        public static final String TRASH_RETENTION_DAYS = "trash_retention_days";
        public static final String SHARE_DEFAULT_EXPIRE_DAYS = "share_default_expire_days";
        public static final String DEFAULT_USER_STORAGE_QUOTA = "default_user_storage_quota";
        public static final String MAX_UPLOAD_SIZE = "max_upload_size";
        public static final String ALLOWED_FILE_TYPES = "allowed_file_types";
    }
}
