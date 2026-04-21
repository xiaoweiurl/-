package com.imagemanager.service;

import com.imagemanager.entity.Album;

import java.util.List;

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
}
