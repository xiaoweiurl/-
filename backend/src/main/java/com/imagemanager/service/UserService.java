package com.imagemanager.service;

import com.imagemanager.dto.CreateNotificationRequest;
import com.imagemanager.dto.CreateUserRequest;
import com.imagemanager.dto.UpdateUserRequest;
import com.imagemanager.dto.UserSettings;
import com.imagemanager.entity.Notification;
import com.imagemanager.entity.User;

import java.util.List;

/**
 * 用户服务接口
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
public interface UserService {
    
    /**
     * 获取当前用户信息
     */
    User getCurrentUser();
    
    /**
     * 获取用户通知列表
     */
    List<Notification> getNotifications();
    
    /**
     * 创建通知
     */
    Notification createNotification(CreateNotificationRequest request);
    
    /**
     * 删除通知
     */
    void deleteNotification(String notificationId);
    
    /**
     * 标记通知为已读
     */
    void markNotificationRead(String notificationId);
    
    /**
     * 标记所有通知为已读
     */
    void markAllNotificationsRead();
    
    /**
     * 获取未读通知数量
     */
    Integer getUnreadCount();
    
    /**
     * 获取图片数量
     */
    Integer getImageCount();
    
    /**
     * 获取相册数量
     */
    Integer getAlbumCount();
    
    /**
     * 获取收藏数量
     */
    Integer getFavoriteCount();
    
    /**
     * 获取所有用户列表（管理员）
     */
    List<User> getAllUsers();
    
    /**
     * 根据ID获取用户
     */
    User getUserById(String userId);
    
    /**
     * 获取用户设置
     */
    UserSettings getSettings(String userId);
    
    /**
     * 更新用户设置
     */
    UserSettings updateSettings(String userId, UserSettings settings);
    
    /**
     * 创建新用户（管理员）
     */
    User createUser(CreateUserRequest request);
    
    /**
     * 更新用户信息（管理员）
     */
    User updateUser(String userId, UpdateUserRequest request);
    
    /**
     * 删除用户（管理员）
     */
    void deleteUser(String userId);
    
    /**
     * 重置用户密码（管理员）
     */
    void resetPassword(String userId, String newPassword);
    
    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);
    
    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);
}
