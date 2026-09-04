package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 图片实体类
 * 
 * 字段映射以 schema_complete.sql 中 images 表定义为准：
 * - name (NOT NULL) → 图片名称
 * - file_path (NOT NULL) → 文件存储路径
 * - file_size (NOT NULL) → 文件大小
 * - mime_type → MIME类型
 * - format → 文件格式
 * - uploader_id → 上传者ID
 * - taken_at → 拍摄时间
 * 
 * @author Image Manager Team
 * @version 2.0.0
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
    
    @Id
    @Column(length = 36)
    private String id;
    
    /**
     * 图片名称（数据库 NOT NULL 字段，值与 title 相同）
     */
    @Column(length = 500, nullable = false)
    private String name;
    
    /**
     * 图片标题（显示名称）
     */
    @Column(length = 255)
    private String title;
    
    /**
     * 图片描述
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * 原始文件名
     */
    @Column(name = "original_name", length = 500)
    private String originalName;
    
    /**
     * 文件存储路径（NOT NULL，本地模式为本地路径，S3模式为 S3 key）
     */
    @Column(name = "file_path", length = 1000, nullable = false)
    private String filePath;
    
    /**
     * 文件存储Key（对象存储中的路径，与 filePath 值相同）
     */
    @Column(name = "file_key", length = 500)
    private String fileKey;
    
    /**
     * 图片URL（访问地址）
     */
    @Column(length = 1000)
    private String url;
    
    /**
     * 图片缩略图URL
     */
    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;
    
    /**
     * 图片原始URL（导入时的原始链接）
     */
    @Column(name = "original_url", length = 1000)
    private String originalUrl;
    
    /**
     * 文件大小（字节）- 映射数据库 file_size 列 (NOT NULL)
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;
    
    /**
     * 文件大小（格式化字符串，如 "2.4 MB"）
     */
    @Column(name = "size_formatted", length = 20)
    private String sizeFormatted;

    /**
     * 文件大小（兼容旧列，nullable）
     * 数据库同时有 size(nullable) 和 file_size(NOT NULL)
     */
    @Column(name = "size")
    private Long size;
    
    /**
     * MIME类型（如 image/jpeg）
     */
    @Column(name = "mime_type", length = 100)
    private String mimeType;
    
    /**
     * 文件格式（如 JPEG、PNG）
     */
    @Column(length = 50)
    private String format;
    
    /**
     * 文件类型简写（jpg, png, gif等）- 兼容旧逻辑
     */
    @Column(name = "file_type", length = 10)
    private String fileType;
    
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
     * 拍摄时间
     */
    @Column(name = "taken_at")
    private LocalDateTime takenAt;
    
    /**
     * 上传者ID
     */
    @Column(name = "uploader_id", length = 36)
    private String uploaderId;
    
    /**
     * 所属相册ID
     */
    @Column(name = "album_id", length = 36)
    private String albumId;
    
    /**
     * 相册名称
     */
    @Column(name = "album_name", length = 100)
    private String albumName;
    
    /**
     * 是否收藏
     */
    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean favorite;
    
    /**
     * AI识别的标签（映射 image_ai_tags 表）
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "image_ai_tags", joinColumns = @JoinColumn(name = "image_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> aiTags = new ArrayList<>();
    
    /**
     * AI识别置信度（0-100）
     */
    @Column(name = "ai_confidence")
    private Double aiConfidence;
    
    /**
     * 分类方法（filename-文件名匹配, llm-AI识别, user-用户指定）
     */
    @Column(name = "classify_method", length = 20)
    private String classifyMethod;
    
    /**
     * 所属公司（系统统一为宝娜斯）
     */
    @Column(length = 50)
    private String company;
    
    /**
     * 图片来源：knowledge=知识图片，creative=二创AI图片，upload=上传
     */
    @Column(length = 20)
    private String source;
    
    /**
     * 上传用户ID
     */
    @Column(name = "user_id", length = 36)
    private String userId;
    
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
     * 浏览次数
     */
    @Column(name = "view_count")
    private Integer viewCount;
    
    /**
     * 下载次数
     */
    @Column(name = "download_count")
    private Integer downloadCount;
    
    /**
     * 商品ID（用于关联同一商品的所有图片）
     */
    @Column(name = "product_id", length = 255)
    private String productId;
    
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
     * 来源表名（动态表方案中使用，不持久化）
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
