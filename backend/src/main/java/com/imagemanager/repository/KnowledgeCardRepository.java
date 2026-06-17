package com.imagemanager.repository;

import com.imagemanager.entity.KnowledgeCard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KnowledgeCardRepository extends JpaRepository<KnowledgeCard, UUID> {

    // ===== company + userId 双重隔离查询 =====

    List<KnowledgeCard> findByCompanyAndDomainCodeAndUserId(String company, String domainCode, String userId);

    List<KnowledgeCard> findByCompanyAndDomainCodeAndUserIdOrderByCreatedAtDesc(String company, String domainCode, String userId);

    @Query("SELECT c FROM KnowledgeCard c WHERE c.company = :company AND c.userId = :userId AND (LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<KnowledgeCard> searchByKeywordAndCompany(@Param("company") String company, @Param("userId") String userId, @Param("keyword") String keyword);

    Optional<KnowledgeCard> findByIdAndCompanyAndUserId(UUID id, String company, String userId);

    @Modifying
    @Query("DELETE FROM KnowledgeCard c WHERE c.id = :id AND c.company = :company AND c.userId = :userId")
    void deleteByIdAndCompanyAndUserId(@Param("id") UUID id, @Param("company") String company, @Param("userId") String userId);

    @Query("SELECT COUNT(c) FROM KnowledgeCard c WHERE c.domainCode = :domainCode AND c.company = :company AND c.userId = :userId")
    long countByDomainCodeAndCompanyAndUserId(@Param("domainCode") String domainCode, @Param("company") String company, @Param("userId") String userId);

    // ===== 用户隔离查询（旧方法保留兼容） =====

    List<KnowledgeCard> findByDomainCodeAndUserId(String domainCode, String userId);

    List<KnowledgeCard> findByUserIdAndDomainCodeOrderByCreatedAtDesc(String userId, String domainCode);

    Optional<KnowledgeCard> findByIdAndUserId(UUID id, String userId);

    @Query("SELECT c FROM KnowledgeCard c WHERE c.userId = :userId AND (LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(c.content) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<KnowledgeCard> searchByKeyword(@Param("userId") String userId, @Param("keyword") String keyword);

    @Modifying
    @Query("DELETE FROM KnowledgeCard c WHERE c.id = :id AND c.userId = :userId")
    void deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") String userId);

    @Query("SELECT COUNT(c) FROM KnowledgeCard c WHERE c.domainCode = :domainCode AND c.userId = :userId")
    long countByDomainCodeAndUserId(@Param("domainCode") String domainCode, @Param("userId") String userId);

    List<KnowledgeCard> findByUserIdAndSource(String userId, String source);

    // ===== 管理员查询（无用户过滤） =====

    Page<KnowledgeCard> findByDomainCode(String domainCode, Pageable pageable);
    List<KnowledgeCard> findByDomainCodeOrderByCreatedAtDesc(String domainCode);

    @Query("SELECT c FROM KnowledgeCard c WHERE c.status = 'published' ORDER BY c.createdAt DESC")
    Page<KnowledgeCard> findAllPublished(Pageable pageable);

    @Query("SELECT COUNT(c) FROM KnowledgeCard c WHERE c.domainCode = :domainCode")
    long countByDomainCode(@Param("domainCode") String domainCode);
}
