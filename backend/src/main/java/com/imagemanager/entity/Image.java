package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 图片实体类
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "images", indexes = {
    @Index(name = "idx_image_album_id", columnList = "album_id"),
    @Index(name = "idx_image_user_id", columnList = "user_id"),
    @Index(name = "idx_image_created_at", columnList = "created_at"),
    @Index(name = "idx_image_deleted", columnList = "deleted")
})
public class Image {
    
    /**
     * 图片ID
     */
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 图片标题
     */
    @Column(length = 255)
    private String title;
    
    /**
     * 图片描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * 图片URL
     */
    @Column(length = 500)
    private String url;
    
    /**
     * 图片原始URL（导入时的原始链接）
     */
    @Column(length = 500)
    private String originalUrl;
    
    /**
     * 图片缩略图URL
     */
    @Column(length = 500)
    private String thumbnailUrl;
    
    /**
     * 文件存储Key（对象存储中的路径）
     */
    @Column(length = 500)
    private String fileKey;
    
    /**
     * 文件大小（字节）
     */
    private Long size;
    
    /**
     * 文件大小（格式化字符串，如 "2.4 MB"）
     */
    @Column(length = 20)
    private String sizeFormatted;
    
    /**
     * 图片宽度（像素）
     */
    private Integer width;
    
    /**
     * 图片高度（像素）
     */
    private Integer height;
    
    /**
     * 分辨率（格式化字符串，如 "1920×1080"）
     */
    @Column(length = 20)
    private String resolution;
    
    /**
     * 文件类型（jpg, png, gif等）
     */
    @Column(length = 10)
    private String fileType;
    
    /**
     * 原始文件名
     */
    @Column(length = 255)
    private String originalName;
    
    /**
     * 所属相册ID
     */
    @Column(name = "album_id", length = 36)
    private String albumId;
    
    /**
     * 相册名称
     */
    @Column(length = 100)
    private String albumName;
    
    /**
     * 是否收藏
     */
    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean favorite;
    
    /**
     * 标签列表
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "image_tags", joinColumns = @JoinColumn(name = "image_id"))
    @Column(name = "tag", length = 50)
    private List<String> tags;
    
    /**
     * AI识别的标签
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "image_ai_tags", joinColumns = @JoinColumn(name = "image_id"))
    @Column(name = "tag", length = 50)
    private List<String> aiTags;
    
    /**
     * AI识别置信度（0-100）
     */
    private Double aiConfidence;
    
    /**
     * 分类方法（filename-文件名匹配, llm-AI识别, user-用户指定）
     */
    @Column(length = 20)
    private String classifyMethod;
    
    /**
     * 上传时间
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * 上传用户ID
     */
    @Column(name = "user_id", length = 36)
    private String userId;

    /**
     * 所属公司（宝娜斯/盈云）
     */
    @Column(length = 20)
    private String company;
    
    /**
     * 是否已删除（回收站标记）
     */
    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean deleted;
    
    /**
     * 删除时间
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    /**
     * 浏览次数
     */
    private Integer viewCount;
    
    /**
     * 下载次数
     */
    private Integer downloadCount;
    
    /**
     * 商品ID（用于关联同一商品的所有图片）
     */
    @Column(name = "product_id", length = 255)
    private String productId;
    
    /**
     * 来源表名（动态表方案中使用，不持久化）
     * 用于记录图片来自哪张用户表（如 images_user_1）
     */
    @Transient
    private String sourceTable;
    
    // Getters and Setters for transient field
    public String getSourceTable() {
        return sourceTable;
    }
    
    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }
    /**
     * 是否为主图
     */
    @Column(name = "is_main_image", columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean isMainImage;
    
    /**
     * 显示顺序（用于排序详情图）
     */
    @Column(name = "display_order")
    private Integer displayOrder;

    /**
     * 实体加载后自动处理 URL
     * 将数据库中存储的绝对路径（如 http://localhost:8080/api/uploads/...）
     * 转换为相对路径（/api/uploads/...），避免外网访问时 localhost 无法解析
     */
    @PostLoad
    public void postLoad() {
        this.url = normalizeUrl(this.url);
        this.thumbnailUrl = normalizeUrl(this.thumbnailUrl);
        this.originalUrl = normalizeUrl(this.originalUrl);
    }

    private String normalizeUrl(String url) {
        if (url == null) return null;
        // 去掉 http://localhost:8080 或 https://localhost:8080 前缀
        if (url.startsWith("http://localhost:8080")) {
            return url.substring("http://localhost:8080".length());
        }
        if (url.startsWith("https://localhost:8080")) {
            return url.substring("https://localhost:8080".length());
        }
        return url;
    }
}
