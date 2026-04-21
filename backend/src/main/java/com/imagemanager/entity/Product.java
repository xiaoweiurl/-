package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品实体类
 * 用于管理商品信息和关联商品图片
 *
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    /**
     * 商品名称
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * 商品描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 商品分类（如：T恤、内衣、抓绒衣、冲锋衣、软壳）
     */
    @Column(name = "category", length = 100)
    private String category;

    /**
     * 关联的相册ID
     */
    @Column(name = "album_id", length = 36)
    private String albumId;

    /**
     * 所属用户ID
     */
    @Column(name = "user_id", length = 36)
    private String userId;

    /**
     * 封面图片ID（主图ID）
     */
    @Column(name = "cover_image_id", length = 36)
    private String coverImageId;

    /**
     * 该商品的图片总数
     */
    @Column(name = "image_count")
    private Integer imageCount;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }
}
