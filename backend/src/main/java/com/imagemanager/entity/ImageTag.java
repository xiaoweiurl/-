package com.imagemanager.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 图片标签实体（映射 image_tags 表）
 * 该表有 tag_id NOT NULL 列，无法使用 @ElementCollection
 */
@Entity
@Table(name = "image_tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageTag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "tag_id")
    private String tagId;

    @Column(name = "image_id", nullable = false)
    private String imageId;

    @Column(name = "tag", nullable = false)
    private String tag;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "company")
    private String company;

    /**
     * 标签类型：manual-手动标签, ai-AI识别标签
     * 用于在同一个表中区分手动标签和AI标签
     */
    @Column(name = "tag_type")
    private String tagType;
}
