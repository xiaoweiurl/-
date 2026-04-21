package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档实体类
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "documents")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Document {
    
    @Id
    @Column(name = "id", length = 36)
    private String id;
    
    @Column(name = "name", length = 500, nullable = false)
    private String name;
    
    @Column(name = "original_name", length = 500)
    private String originalName;
    
    @Column(name = "stored_name", length = 255, nullable = false)
    private String storedName;
    
    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath;
    
    @Column(name = "url", length = 1000)
    private String url;
    
    @Column(name = "size")
    private Long size;
    
    @Column(name = "content_type", length = 100)
    private String contentType;
    
    @Column(name = "extension", length = 20)
    private String extension;
    
    @Column(name = "category", length = 20, nullable = false)
    private String category;
    
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;
    
    @Column(name = "deleted")
    private Boolean deleted;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (deleted == null) {
            deleted = false;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
