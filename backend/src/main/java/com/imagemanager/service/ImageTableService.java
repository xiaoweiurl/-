package com.imagemanager.service;

import java.util.List;

/**
 * 动态表管理服务
 * 为每个用户创建独立的图片表（images_<username>）
 */
public interface ImageTableService {

    /**
     * 为用户创建图片表
     * @param username 用户名（用于生成表名）
     * @return 是否创建成功
     */
    boolean createUserImageTable(String username);

    /**
     * 检查用户图片表是否存在
     * @param username 用户名（用于生成表名）
     * @return 表是否存在
     */
    boolean userImageTableExists(String username);

    /**
     * 删除用户图片表（用户注销时）
     * @param username 用户名（用于生成表名）
     * @return 是否删除成功
     */
    boolean deleteUserImageTable(String username);

    /**
     * 获取所有用户图片表名列表
     * @return 表名列表（如 ["images_admin", "images_zhangsan"]）
     */
    List<String> getAllUserImageTableNames();

    /**
     * 获取用户表名
     * @param username 用户名（用于生成表名）
     * @return 表名（如 "images_admin"）
     */
    String getUserTableName(String username);

    /**
     * 确保用户图片表存在（不存在则创建）
     * @param username 用户名（用于生成表名）
     * @return 表是否存在/创建成功
     */
    boolean ensureUserImageTable(String username);
}