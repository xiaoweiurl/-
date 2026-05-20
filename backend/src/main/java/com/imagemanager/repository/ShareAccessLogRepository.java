package com.imagemanager.repository;

import com.imagemanager.entity.ShareAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ShareAccessLogRepository extends JpaRepository<ShareAccessLog, String> {
    
    Page<ShareAccessLog> findByShareLinkId(String shareLinkId, Pageable pageable);
    
    @Query("SELECT COUNT(s) FROM ShareAccessLog s WHERE s.shareLinkId = :shareLinkId AND s.action = :action")
    long countByShareLinkIdAndAction(@Param("shareLinkId") String shareLinkId, @Param("action") String action);
    
    @Query("SELECT COUNT(s) FROM ShareAccessLog s WHERE s.shareLinkId = :shareLinkId AND s.createdAt BETWEEN :startTime AND :endTime")
    long countByShareLinkIdAndTimeRange(@Param("shareLinkId") String shareLinkId, 
                                         @Param("startTime") LocalDateTime startTime, 
                                         @Param("endTime") LocalDateTime endTime);
    
    @Query("SELECT FUNCTION('DATE', s.createdAt) as date, COUNT(s) as count FROM ShareAccessLog s WHERE s.shareLinkId = :shareLinkId AND s.createdAt >= :since GROUP BY FUNCTION('DATE', s.createdAt) ORDER BY date")
    List<Object[]> getDailyAccessStats(@Param("shareLinkId") String shareLinkId, @Param("since") LocalDateTime since);
    
    @Query("SELECT s.ipAddress, COUNT(s) as count FROM ShareAccessLog s WHERE s.shareLinkId = :shareLinkId GROUP BY s.ipAddress ORDER BY count DESC")
    List<Object[]> getTopIpAddresses(@Param("shareLinkId") String shareLinkId, Pageable pageable);
    
    void deleteByCreatedAtBefore(LocalDateTime before);
}
