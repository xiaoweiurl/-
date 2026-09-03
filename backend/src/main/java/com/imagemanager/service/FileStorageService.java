package com.imagemanager.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 文件存储服务接口
 */
public interface FileStorageService {
    
    /**
     * 上传文件
     */
    String uploadFile(MultipartFile file, String path);
    
    /**
     * 上传文件（字节数组）
     */
    String uploadFile(byte[] data, String fileName, String contentType);

    /**
     * 上传文件到指定目录并使用指定文件名，返回存储 key
     * 用于需要固定 key 结构（便于按 key 删除/签名）的场景，如商品库图片
     *
     * @param file     上传文件
     * @param directory 目录（如 goods-library/货号品名），不含首尾斜杠
     * @param fileName 文件名（如 main.jpg）
     * @return 存储 key（directory/fileName）
     */
    String uploadFileForKey(MultipartFile file, String directory, String fileName);
    
    /**
     * 获取文件访问URL
     */
    String getFileUrl(String fileKey);
    
    /**
     * 生成预签名URL
     */
    String generatePresignedUrl(String fileKey, int expireSeconds);
    
    /**
     * 删除文件
     */
    boolean deleteFile(String fileKey);
    
    /**
     * 获取存储key
     */
    String getStorageKey(String fileKey);
    
    /**
     * 获取文件输入流
     */
    InputStream getFileInputStream(String fileKey) throws Exception;
    
    /**
     * 检查文件是否存在
     */
    boolean fileExists(String fileKey);
}
