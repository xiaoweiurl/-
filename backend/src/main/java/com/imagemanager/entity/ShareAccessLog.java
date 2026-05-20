package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分享访问记录实体
 */
@Data
@Entity
@Table(name = "share_access_logs")
public class ShareAccessLog {
    @Id
    private String id;

    @Column(name = "share_link_id", nullable = false, length = 36)
    private String shareLinkId;

    @Column(name = "action", nullable = false, length = 20)
    private String action; // view, download

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "referer", length = 500)
    private String referer;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
