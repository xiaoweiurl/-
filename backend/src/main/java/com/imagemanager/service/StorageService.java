package com.imagemanager.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 存储服务接口
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
public interface StorageService {
    
    /**
     * 上传文件
     * 
     * @param file 文件
     * @param path 存储路径（如 images/2024/01/）
     * @return 文件访问URL
     */
    String uploadFile(MultipartFile file, String path);
    
    /**
     * 上传文件（字节数组）
     * 
     * @param data 文件数据
     * @param fileName 文件名
     * @param contentType 内容类型
     * @return 文件访问URL
     */
    String uploadFile(byte[] data, String fileName, String contentType);
    
    /**
     * 获取文件访问URL
     * 
     * @param fileKey 文件Key
     * @return 访问URL
     */
    String getFileUrl(String fileKey);
    
    /**
     * 生成预签名URL（用于临时访问）
     * 
     * @param fileKey 文件Key
     * @param expireSeconds 过期时间（秒）
     * @return 预签名URL
     */
    String generatePresignedUrl(String fileKey, int expireSeconds);
    
    /**
     * 删除文件
     * 
     * @param fileKey 文件Key
     * @return 是否成功
     */
    boolean deleteFile(String fileKey);
    
    /**
     * 获取文件存储Key
     * 
     * @param fileKey 文件Key
     * @return 存储Key（用于数据库存储）
     */
    String getStorageKey(String fileKey);
    
    /**
     * 获取文件输入流
     * 
     * @param fileKey 文件Key
     * @return 文件输入流
     * @throws Exception 如果文件不存在或读取失败
     */
    InputStream getFileInputStream(String fileKey) throws Exception;
    
    /**
     * 检查文件是否存在
     * 
     * @param fileKey 文件Key
     * @return 是否存在
     */
    boolean fileExists(String fileKey);
}
