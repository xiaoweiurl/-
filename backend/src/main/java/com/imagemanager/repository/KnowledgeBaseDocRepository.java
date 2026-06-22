package com.imagemanager.repository;

import com.imagemanager.entity.KnowledgeBaseDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeBaseDocRepository extends JpaRepository<KnowledgeBaseDoc, UUID> {
    // ===== 按 company 隔离（同一公司共享数据） =====
    Page<KnowledgeBaseDoc> findByCompanyOrderByCreatedAtDesc(String company, Pageable pageable);

    List<KnowledgeBaseDoc> findByCompanyAndCategoryIdOrderByCreatedAtDesc(String company, UUID categoryId);

    Optional<KnowledgeBaseDoc> findByIdAndCompany(UUID id, String company);

    long countByCompany(String company);

    @Query("SELECT d FROM KnowledgeBaseDoc d WHERE d.company = :company AND (LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.fileContent) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<KnowledgeBaseDoc> searchByKeyword(@Param("company") String company, @Param("keyword") String keyword, Pageable pageable);

    long countByCompanyAndCategoryId(String company, UUID categoryId);

    // ===== 旧方法保留兼容（按 company + userId 双重过滤，已弃用） =====
    Page<KnowledgeBaseDoc> findByCompanyAndUserIdOrderByCreatedAtDesc(String company, String userId, Pageable pageable);
    List<KnowledgeBaseDoc> findByCompanyAndUserIdAndCategoryIdOrderByCreatedAtDesc(String company, String userId, UUID categoryId);
    Optional<KnowledgeBaseDoc> findByIdAndCompanyAndUserId(UUID id, String company, String userId);
    long countByCompanyAndUserId(String company, String userId);

    // ===== 旧方法保留兼容（无 company 过滤，已弃用） =====
    Page<KnowledgeBaseDoc> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    List<KnowledgeBaseDoc> findByUserIdAndCategoryIdOrderByCreatedAtDesc(String userId, UUID categoryId);
    Optional<KnowledgeBaseDoc> findByIdAndUserId(UUID id, String userId);
    long countByUserId(String userId);
}
