package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 存储配额实体
 */
@Data
@Entity
@Table(name = "storage_quotas")
public class StorageQuota {
    @Id
    private String id;

    @Column(name = "user_id", nullable = false, unique = true, length = 36)
    private String userId;

    @Column(name = "max_storage_bytes")
    private Long maxStorageBytes = 10737418240L; // 默认10GB

    @Column(name = "used_storage_bytes")
    private Long usedStorageBytes = 0L;

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
     * 获取剩余空间
     */
    public Long getRemainingBytes() {
        return maxStorageBytes - usedStorageBytes;
    }

    /**
     * 检查是否有足够空间
     */
    public boolean hasEnoughSpace(Long bytes) {
        return getRemainingBytes() >= bytes;
    }

    /**
     * 获取使用百分比
     */
    public double getUsagePercentage() {
        return (double) usedStorageBytes / maxStorageBytes * 100;
    }

    /**
     * 格式化存储大小
     */
    public static String formatSize(Long bytes) {
        if (bytes == null || bytes < 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes.doubleValue();
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format("%.2f %s", size, units[unitIndex]);
    }
}
