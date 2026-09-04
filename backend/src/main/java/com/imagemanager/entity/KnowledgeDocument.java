package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    /**
     * 所属公司（宝娜斯/盈云）
     */
    @Column(length = 20)
    private String company;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "domain_code", length = 50)
    private String domainCode;

    @Column(length = 20)
    private String status = "processing";

    @Column(name = "chunk_count")
    private Integer chunkCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
