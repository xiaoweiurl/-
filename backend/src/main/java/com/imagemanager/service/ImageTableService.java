package com.imagemanager.service;

import java.util.List;

/**
 * 动态表管理服务
 * 为每个用户创建独立的图片表（images_<userId>）
 */
public interface ImageTableService {
    
    /**
     * 为用户创建图片表
     * @param userId 用户ID
     * @return 是否创建成功
     */
    boolean createUserImageTable(String userId);
    
    /**
     * 检查用户图片表是否存在
     * @param userId 用户ID
     * @return 表是否存在
     */
    boolean userImageTableExists(String userId);
    
    /**
     * 删除用户图片表（用户注销时）
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean deleteUserImageTable(String userId);
    
    /**
     * 获取所有用户图片表名列表
     * @return 表名列表（如 ["images_user_1", "images_admin"]）
     */
    List<String> getAllUserImageTableNames();
    
    /**
     * 获取用户表名
     * @param userId 用户ID
     * @return 表名（如 "images_user_1"）
     */
    String getUserTableName(String userId);
}