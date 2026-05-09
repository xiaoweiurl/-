package com.imagemanager.service;

import com.imagemanager.entity.Album;

import java.util.List;
import java.util.Optional;

/**
 * 相册服务接口
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
public interface AlbumService {
    
    /**
     * 获取所有相册
     */
    List<Album> getAllAlbums();
    
    /**
     * 根据ID获取相册
     */
    Album getAlbumById(String id);
    
    /**
     * 根据名称查找相册（精确匹配）
     */
    Optional<Album> findByName(String name);
    
    /**
     * 根据父相册和子相册名称查找或创建相册
     * 如果父相册+子相册组合已存在，直接返回；否则创建新的
     */
    Album getOrCreateAlbumByParentAndName(String parentName, String childName, String userId);
    
    /**
     * 创建相册
     */
    Album createAlbum(String name, String description, List<String> keywords);
    
    /**
     * 创建相册（带匹配配置）
     */
    Album createAlbum(String name, String description, List<String> keywords, String matchingConfig);
    
    /**
     * 创建相册（不带关键词）
     */
    Album createAlbum(String name, String description);
    
    /**
     * 更新相册
     */
    Album updateAlbum(String id, String name, String description);
    
    /**
     * 更新相册（带匹配配置）
     */
    Album updateAlbum(String id, String name, String description, String matchingConfig);
    
    /**
     * 删除相册
     */
    void deleteAlbum(String id);
    
    /**
     * 获取相册图片数量
     */
    Integer getImageCount(String albumId);
    
    /**
     * 批量更新所有相册的匹配模式
     * @param mode 匹配模式：contains, exact, startsWith, endsWith, regex, fuzzy
     * @return 更新的相册数量
     */
    int batchUpdateMatchingMode(String mode);
    
    /**
     * 批量重置所有相册的匹配配置为默认（包含匹配）
     * @return 更新的相册数量
     */
    int resetAllMatchingConfig();
    
    /**
     * 创建层级相册（支持父级）
     * @param name 相册名称
     * @param parentId 父相册ID（null 表示顶级）
     * @param description 描述
     * @param keywords 关键词
     * @return 创建的相册
     */
    Album createAlbumWithParent(String name, String parentId, String description, List<String> keywords);
    
    /**
     * 根据路径获取或创建相册（用于自动分类）
     * @param fullPath 完整路径，如 "松野湃/速干T恤"
     * @return 相册
     */
    Album getOrCreateAlbumByPath(String fullPath);
    
    /**
     * 获取用户的层级相册树
     * @param userId 用户ID
     * @return 顶级相册列表（包含子相册）
     */
    List<Album> getAlbumTree(String userId);
    
    /**
     * 获取相册的所有子相册
     * @param parentId 父相册ID
     * @return 子相册列表
     */
    List<Album> getChildAlbums(String parentId);
    
    /**
     * 批量删除相册
     * @param ids 相册ID列表
     * @return 删除结果：成功数量、失败数量、失败原因
     */
    java.util.Map<String, Object> batchDeleteAlbums(java.util.List<String> ids);
}
