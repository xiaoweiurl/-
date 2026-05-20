package com.imagemanager.repository;

import com.imagemanager.entity.ShareLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, String> {
    
    Optional<ShareLink> findByShareCodeAndDeletedFalse(String shareCode);
    
    List<ShareLink> findByResourceIdAndDeletedFalse(String resourceId);
    
    List<ShareLink> findByResourceTypeAndResourceIdAndDeletedFalse(String resourceType, String resourceId);
    
    Page<ShareLink> findByResourceTypeAndCreatedByAndDeletedFalse(String resourceType, String createdBy, Pageable pageable);
    
    Page<ShareLink> findByCreatedByAndDeletedFalse(String createdBy, Pageable pageable);
    
    @Query("SELECT s FROM ShareLink s WHERE s.createdBy = :userId AND s.deleted = false ORDER BY s.createdAt DESC")
    Page<ShareLink> findByCreatedByOrderByCreatedAtDesc(@Param("userId") String userId, Pageable pageable);
    
    @Query("SELECT s FROM ShareLink s WHERE s.expireAt < :now AND s.deleted = false")
    List<ShareLink> findExpiredLinks(@Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(s) FROM ShareLink s WHERE s.resourceId = :resourceId AND s.deleted = false")
    long countByResourceId(@Param("resourceId") String resourceId);
    
    @Query("SELECT SUM(s.viewCount) FROM ShareLink s WHERE s.resourceId = :resourceId AND s.deleted = false")
    Long sumViewCountByResourceId(@Param("resourceId") String resourceId);
    
    @Query("SELECT SUM(s.downloadCount) FROM ShareLink s WHERE s.resourceId = :resourceId AND s.deleted = false")
    Long sumDownloadCountByResourceId(@Param("resourceId") String resourceId);
}
