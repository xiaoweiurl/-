package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 通知实体类
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notification_user_id", columnList = "user_id"),
    @Index(name = "idx_notification_read", columnList = "read"),
    @Index(name = "idx_notification_created_at", columnList = "created_at")
})
public class Notification {
    
    /**
     * 通知ID
     */
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 通知标题
     */
    @Column(length = 100)
    private String title;
    
    /**
     * 通知内容
     */
    @Column(columnDefinition = "TEXT")
    private String content;
    
    /**
     * 通知类型（upload, album, system）
     */
    @Column(length = 20)
    private String type;
    
    /**
     * 是否已读
     */
    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean read;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    /**
     * 关联资源ID（如图片ID、相册ID）
     */
    @Column(name = "resource_id", length = 36)
    private String resourceId;
    
    /**
     * 用户ID
     */
    @Column(name = "user_id", length = 36)
    private String userId;
}
