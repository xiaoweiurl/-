package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 用户设置
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {
    
    /**
     * 界面主题（light, dark, system）
     */
    private String theme;
    
    /**
     * 语言
     */
    private String language;
    
    /**
     * 每页显示数量
     */
    private Integer pageSize;
    
    /**
     * 默认排序字段
     */
    private String defaultSort;
    
    /**
     * 是否启用AI识别
     */
    private Boolean aiRecognitionEnabled;
    
    /**
     * 邮件通知
     */
    private Boolean emailNotifications;
    
    /**
     * 系统通知
     */
    private Boolean systemNotifications;
    
    /**
     * 上传通知
     */
    private Boolean uploadNotifications;
    
    /**
     * 自动播放视频
     */
    private Boolean autoPlayVideos;
    
    /**
     * 高质量预览
     */
    private Boolean highQualityPreviews;
    
    /**
     * 紧凑模式
     */
    private Boolean compactMode;
    
    /**
     * 显示文件信息
     */
    private Boolean showFileInfo;
    
    /**
     * 默认视图模式（grid, masonry, list）
     */
    private String defaultView;
}
