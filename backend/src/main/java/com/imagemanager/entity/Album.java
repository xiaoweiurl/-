package com.imagemanager.entity;

import com.imagemanager.dto.MatchingConfig;
import com.imagemanager.util.MatchingEngine;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 相册实体类
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "albums", indexes = {
    @Index(name = "idx_album_user_id", columnList = "user_id"),
    @Index(name = "idx_album_sort_order", columnList = "sort_order")
})
public class Album {
    
    /**
     * 相册ID
     */
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 相册名称
     */
    @Column(length = 100, nullable = false)
    private String name;
    
    /**
     * 相册描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * 相册封面图片URL
     */
    @Column(length = 500)
    private String coverUrl;
    
    /**
     * 相册内图片数量
     */
    private Integer imageCount;
    
    /**
     * 相册排序
     */
    @Column(name = "sort_order")
    private Integer sortOrder;
    
    /**
     * 自动分类关键词（用于匹配图片分类）
     */
    @ElementCollection
    @CollectionTable(name = "album_keywords", joinColumns = @JoinColumn(name = "album_id"))
    @Column(name = "keyword", length = 50)
    private List<String> keywords;
    
    /**
     * 匹配规则配置（JSON格式）
     * 支持模式：contains, exact, startsWith, endsWith, regex, fuzzy
     */
    @Column(name = "matching_config", columnDefinition = "TEXT")
    private String matchingConfig;
    
    /**
     * 是否为系统预置相册
     */
    @Column(name = "is_system", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isSystem;
    
    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * 所属用户ID
     */
    @Column(name = "user_id", length = 36)
    private String userId;
    
    /**
     * 获取匹配配置对象
     */
    @Transient
    public MatchingConfig getMatchingConfigObj() {
        if (matchingConfig == null || matchingConfig.isEmpty()) {
            return MatchingEngine.createDefaultConfig();
        }
        try {
            // 使用简单的JSON解析或直接返回默认配置
            // 实际项目中可以使用 Jackson 或 Gson
            return MatchingEngine.createDefaultConfig();
        } catch (Exception e) {
            return MatchingEngine.createDefaultConfig();
        }
    }
}
