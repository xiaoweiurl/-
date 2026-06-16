package com.imagemanager.repository;

import com.imagemanager.entity.KnowledgeBaseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeBaseCategoryRepository extends JpaRepository<KnowledgeBaseCategory, UUID> {
    List<KnowledgeBaseCategory> findByUserIdOrderBySortOrderAsc(String userId);

    Optional<KnowledgeBaseCategory> findByIdAndUserId(UUID id, String userId);

    long countByUserId(String userId);
}
