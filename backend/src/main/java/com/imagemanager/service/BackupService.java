package com.imagemanager.service;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 备份服务接口
 */
public interface BackupService {
    
    /**
     * 创建备份
     */
    Map<String, Object> createBackup(String userId, String backupType);
    
    /**
     * 获取备份列表
     */
    Map<String, Object> getBackupList(String userId, int page, int pageSize);
    
    /**
     * 下载备份文件
     */
    ResponseEntity<byte[]> downloadBackup(String userId, String backupId);
    
    /**
     * 从备份恢复
     */
    Map<String, Object> restoreFromBackup(String userId, String backupId);
    
    /**
     * 删除备份
     */
    void deleteBackup(String userId, String backupId);
    
    /**
     * 导出用户数据
     */
    Map<String, Object> exportUserData(String userId);
    
    /**
     * 导入用户数据
     */
    Map<String, Object> importUserData(String userId, String jsonData);
}
