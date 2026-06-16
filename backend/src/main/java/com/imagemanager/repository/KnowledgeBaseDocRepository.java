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
    Page<KnowledgeBaseDoc> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    List<KnowledgeBaseDoc> findByUserIdAndCategoryIdOrderByCreatedAtDesc(String userId, UUID categoryId);

    Optional<KnowledgeBaseDoc> findByIdAndUserId(UUID id, String userId);

    long countByUserId(String userId);

    List<KnowledgeBaseDoc> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status);
    long countByCategoryId(UUID categoryId);

    @Query("SELECT d FROM KnowledgeBaseDoc d WHERE d.userId = :userId AND (LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.fileContent) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<KnowledgeBaseDoc> searchByKeyword(@Param("userId") String userId, @Param("keyword") String keyword, Pageable pageable);
}
