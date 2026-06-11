package com.imagemanager.repository;

import com.imagemanager.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    List<KnowledgeDocument> findByUserIdOrderByCreatedAtDesc(String userId);
    List<KnowledgeDocument> findByUserIdAndStatus(String userId, String status);
    long countByUserId(String userId);
}
