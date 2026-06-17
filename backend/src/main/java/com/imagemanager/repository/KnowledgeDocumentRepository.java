package com.imagemanager.repository;

import com.imagemanager.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    List<KnowledgeDocument> findByCompanyAndUserIdOrderByCreatedAtDesc(String company, String userId);
    List<KnowledgeDocument> findByCompanyAndUserIdAndStatus(String company, String userId, String status);
    long countByCompanyAndUserId(String company, String userId);
}
