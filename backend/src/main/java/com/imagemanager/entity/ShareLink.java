package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分享链接实体
 */
@Data
@Entity
@Table(name = "share_links")
public class ShareLink {
    @Id
    private String id;

    @Column(name = "resource_type", nullable = false, length = 20)
    private String resourceType; // album, image, document

    @Column(name = "resource_id", nullable = false, length = 36)
    private String resourceId;

    @Column(name = "share_code", nullable = false, unique = true, length = 10)
    private String shareCode;

    @Column(name = "password", length = 50)
    private String password;

    @Column(name = "expire_at")
    private LocalDateTime expireAt;

    @Column(name = "max_views")
    private Integer maxViews = -1;

    @Column(name = "view_count")
    private Integer viewCount = 0;

    @Column(name = "download_count")
    private Integer downloadCount = 0;

    @Column(name = "created_by", nullable = false, length = 36)
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted")
    private Boolean deleted = false;

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
     * 检查是否过期
     */
    public boolean isExpired() {
        return expireAt != null && LocalDateTime.now().isAfter(expireAt);
    }

    /**
     * 检查是否达到访问上限
     */
    public boolean isViewLimitReached() {
        return maxViews != null && maxViews > 0 && viewCount >= maxViews;
    }

    /**
     * 检查是否需要密码
     */
    public boolean isPasswordProtected() {
        return password != null && !password.isEmpty();
    }
}
