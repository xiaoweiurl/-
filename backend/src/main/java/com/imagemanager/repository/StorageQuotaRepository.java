package com.imagemanager.repository;

import com.imagemanager.entity.StorageQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StorageQuotaRepository extends JpaRepository<StorageQuota, String> {
    
    Optional<StorageQuota> findByUserId(String userId);
    
    @Modifying
    @Query("UPDATE StorageQuota s SET s.usedStorageBytes = s.usedStorageBytes + :bytes WHERE s.userId = :userId")
    void addUsedStorage(@Param("userId") String userId, @Param("bytes") Long bytes);
    
    @Modifying
    @Query("UPDATE StorageQuota s SET s.usedStorageBytes = s.usedStorageBytes - :bytes WHERE s.userId = :userId AND s.usedStorageBytes >= :bytes")
    void subtractUsedStorage(@Param("userId") String userId, @Param("bytes") Long bytes);
    
    @Modifying
    @Query("UPDATE StorageQuota s SET s.usedStorageBytes = (SELECT COALESCE(SUM(i.size), 0) FROM Image i WHERE i.userId = :userId AND i.deleted = false)")
    void recalculateUsedStorage(@Param("userId") String userId);
    
    @Query("SELECT COALESCE(SUM(s.usedStorageBytes), 0) FROM StorageQuota s")
    Long getTotalUsedStorage();
    
    @Query("SELECT COALESCE(SUM(s.maxStorageBytes), 0) FROM StorageQuota s")
    Long getTotalMaxStorage();
}
