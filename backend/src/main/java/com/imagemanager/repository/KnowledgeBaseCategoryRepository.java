package com.imagemanager.repository;

import com.imagemanager.entity.KnowledgeBaseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeBaseCategoryRepository extends JpaRepository<KnowledgeBaseCategory, UUID> {
    // 按 company + userId 双重过滤
    List<KnowledgeBaseCategory> findByCompanyAndUserIdOrderBySortOrderAsc(String company, String userId);

    Optional<KnowledgeBaseCategory> findByIdAndCompanyAndUserId(UUID id, String company, String userId);

    long countByCompanyAndUserId(String company, String userId);

    List<KnowledgeBaseCategory> findByCompanyAndUserIdOrderByCreatedAtDesc(String company, String userId);

    // 旧方法保留兼容
    List<KnowledgeBaseCategory> findByUserIdOrderBySortOrderAsc(String userId);
    Optional<KnowledgeBaseCategory> findByIdAndUserId(UUID id, String userId);
    List<KnowledgeBaseCategory> findByUserIdOrderByCreatedAtDesc(String userId);
}
