package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 用户设置实体类
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_settings")
public class UserSettingsEntity {
    
    /**
     * 设置ID
     */
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 用户ID
     */
    @Column(name = "user_id", length = 36, nullable = false, unique = true)
    private String userId;
    
    /**
     * 界面主题（light, dark, system）
     */
    @Column(length = 20)
    @Builder.Default
    private String theme = "system";
    
    /**
     * 语言
     */
    @Column(length = 10)
    @Builder.Default
    private String language = "zh-CN";
    
    /**
     * 每页显示数量
     */
    @Column(name = "page_size")
    @Builder.Default
    private Integer pageSize = 40;
    
    /**
     * 默认排序字段
     */
    @Column(length = 50)
    @Builder.Default
    private String defaultSort = "createdAt";
    
    /**
     * 是否启用AI识别
     */
    @Column(name = "ai_recognition_enabled")
    @Builder.Default
    private Boolean aiRecognitionEnabled = true;
    
    /**
     * 邮件通知
     */
    @Column(name = "email_notifications")
    @Builder.Default
    private Boolean emailNotifications = true;
    
    /**
     * 系统通知
     */
    @Column(name = "system_notifications")
    @Builder.Default
    private Boolean systemNotifications = true;
    
    /**
     * 上传通知
     */
    @Column(name = "upload_notifications")
    @Builder.Default
    private Boolean uploadNotifications = true;
    
    /**
     * 自动播放视频
     */
    @Column(name = "auto_play_videos")
    @Builder.Default
    private Boolean autoPlayVideos = true;
    
    /**
     * 高质量预览
     */
    @Column(name = "high_quality_previews")
    @Builder.Default
    private Boolean highQualityPreviews = true;
    
    /**
     * 紧凑模式
     */
    @Column(name = "compact_mode")
    @Builder.Default
    private Boolean compactMode = false;
    
    /**
     * 显示文件信息
     */
    @Column(name = "show_file_info")
    @Builder.Default
    private Boolean showFileInfo = true;
    
    /**
     * 默认视图模式（grid, masonry, list）
     */
    @Column(length = 20)
    @Builder.Default
    private String defaultView = "grid";
}
