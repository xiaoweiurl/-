package com.imagemanager.repository;

import com.imagemanager.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 通知数据访问层
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {
    
    /**
     * 查询用户的通知（分页）
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    /**
     * 查询用户的通知（列表）
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * 查询用户的未读通知
     */
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId);
    
    /**
     * 查询用户的未读通知（无排序）
     */
    List<Notification> findByUserIdAndReadFalse(String userId);
    
    /**
     * 统计用户未读通知数量
     */
    int countByUserIdAndReadFalse(String userId);
    
    /**
     * 标记用户所有通知为已读
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    int markAllAsRead(@Param("userId") String userId);
    
    /**
     * 删除用户的所有通知
     */
    void deleteByUserId(String userId);
}
