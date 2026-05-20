package com.imagemanager.service;

import com.imagemanager.dto.StorageQuotaDTO;
import org.springframework.data.domain.Page;

import java.util.Map;

/**
 * 存储服务接口
 */
public interface StorageService {
    
    /**
     * 获取用户存储配额
     */
    StorageQuotaDTO getUserQuota(String userId);
    
    /**
     * 更新用户存储配额
     */
    void updateQuota(String userId, Long maxStorageBytes);
    
    /**
     * 检查用户是否有足够空间
     */
    boolean hasEnoughSpace(String userId, Long bytes);
    
    /**
     * 增加已使用空间
     */
    void addUsedStorage(String userId, Long bytes);
    
    /**
     * 减少已使用空间
     */
    void subtractUsedStorage(String userId, Long bytes);
    
    /**
     * 重新计算用户存储使用量
     */
    void recalculateUsedStorage(String userId);
    
    /**
     * 获取系统存储统计
     */
    Map<String, Object> getSystemStorageStats();
    
    /**
     * 获取所有用户的存储使用情况（管理员）
     */
    Page<StorageQuotaDTO> getAllUserQuotas(int page, int pageSize);
    
    /**
     * 初始化用户存储配额
     */
    void initializeUserQuota(String userId);
}
