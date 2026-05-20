package com.imagemanager.repository;

import com.imagemanager.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    
    Page<AuditLog> findByUserId(String userId, Pageable pageable);
    
    Page<AuditLog> findByAction(String action, Pageable pageable);
    
    Page<AuditLog> findByResourceTypeAndResourceId(String resourceType, String resourceId, Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.userId = :userId AND a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt DESC")
    Page<AuditLog> findByUserIdAndTimeRange(@Param("userId") String userId, 
                                              @Param("startTime") LocalDateTime startTime, 
                                              @Param("endTime") LocalDateTime endTime, 
                                              Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt DESC")
    Page<AuditLog> findByTimeRange(@Param("startTime") LocalDateTime startTime, 
                                    @Param("endTime") LocalDateTime endTime, 
                                    Pageable pageable);
    
    @Query("SELECT a FROM AuditLog a WHERE (:userId IS NULL OR a.userId = :userId) AND (:action IS NULL OR a.action = :action) AND (:resourceType IS NULL OR a.resourceType = :resourceType) AND a.createdAt BETWEEN :startTime AND :endTime ORDER BY a.createdAt DESC")
    Page<AuditLog> searchLogs(@Param("userId") String userId, 
                               @Param("action") String action, 
                               @Param("resourceType") String resourceType,
                               @Param("startTime") LocalDateTime startTime, 
                               @Param("endTime") LocalDateTime endTime, 
                               Pageable pageable);
    
    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findAllDistinctActions();
    
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.userId = :userId AND a.createdAt >= :since")
    long countByUserIdSince(@Param("userId") String userId, @Param("since") LocalDateTime since);
    
    void deleteByCreatedAtBefore(LocalDateTime before);
}
