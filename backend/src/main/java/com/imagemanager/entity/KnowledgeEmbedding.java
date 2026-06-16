package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "knowledge_embeddings")
public class KnowledgeEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "card_id")
    private UUID cardId;

    @Column(name = "embedding", nullable = false, columnDefinition = "vector(1536)")
    private String embedding; // 存储为字符串格式的向量, 通过原生SQL操作

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "chunk_text", columnDefinition = "TEXT")
    private String chunkText;

    @Column(name = "chunk_index")
    @Builder.Default
    private Integer chunkIndex = 0;

    @Column(name = "source_type", length = 20)
    @Builder.Default
    private String sourceType = "MEMORY";

    @Column(name = "source_doc_id", length = 100)
    private String sourceDocId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
