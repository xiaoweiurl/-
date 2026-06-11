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

    // ===== 用户隔离查询 =====

    List<KnowledgeCard> findByDomainCodeAndUserId(String domainCode, String userId);

    Page<KnowledgeCard> findByUserIdAndDomainCode(String userId, String domainCode, Pageable pageable);

    List<KnowledgeCard> findByUserIdAndDomainCodeOrderByCreatedAtDesc(String userId, String domainCode);

    Page<KnowledgeCard> findByUserIdAndDomainCodeAndStatus(String userId, String domainCode, String status, Pageable pageable);

    List<KnowledgeCard> findByUserIdAndProductCode(String userId, String productCode);

    @Query("SELECT c FROM KnowledgeCard c WHERE c.userId = :userId AND c.status = 'published' ORDER BY c.createdAt DESC")
    Page<KnowledgeCard> findAllPublishedByUserId(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT c FROM KnowledgeCard c WHERE c.userId = :userId ORDER BY c.createdAt DESC")
    Page<KnowledgeCard> findAllByUserId(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT COUNT(c) FROM KnowledgeCard c WHERE c.domainCode = :domainCode AND c.userId = :userId")
    long countByDomainCodeAndUserId(@Param("domainCode") String domainCode, @Param("userId") String userId);

    Optional<KnowledgeCard> findByIdAndUserId(UUID id, String userId);

    @Modifying
    @Query("DELETE FROM KnowledgeCard c WHERE c.id = :id AND c.userId = :userId")
    void deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") String userId);

    // ===== 管理员查询（无用户过滤） =====

    Page<KnowledgeCard> findByDomainCode(String domainCode, Pageable pageable);

    List<KnowledgeCard> findByDomainCodeOrderByCreatedAtDesc(String domainCode);

    @Query("SELECT c FROM KnowledgeCard c WHERE c.status = 'published' ORDER BY c.createdAt DESC")
    Page<KnowledgeCard> findAllPublished(Pageable pageable);

    @Query("SELECT COUNT(c) FROM KnowledgeCard c WHERE c.domainCode = :domainCode")
    long countByDomainCode(@Param("domainCode") String domainCode);
}
