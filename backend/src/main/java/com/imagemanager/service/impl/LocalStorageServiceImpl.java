package com.imagemanager.service.impl;

import com.imagemanager.config.StorageConfig;
import com.imagemanager.service.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地存储服务实现
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class LocalStorageServiceImpl implements StorageService {
    
    @Autowired
    private StorageConfig storageConfig;
    
    private Path uploadPath;
    private String baseUrl;
    
    @PostConstruct
    public void init() {
        this.uploadPath = Paths.get(storageConfig.getLocalPath()).toAbsolutePath().normalize();
        this.baseUrl = storageConfig.getBaseUrl();
        
        // 确保 baseUrl 格式正确（不以 / 结尾）
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        try {
            Files.createDirectories(uploadPath);
            log.info("本地存储目录初始化成功: {}, baseUrl: {}", uploadPath, baseUrl);
        } catch (IOException e) {
            log.error("创建存储目录失败", e);
        }
    }
    
    @Override
    public String uploadFile(MultipartFile file, String path) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                    : ".jpg";
            
            String fileName = UUID.randomUUID().toString() + extension;
            String fullPath = path.isEmpty() ? fileName : path + "/" + fileName;
            
            Path targetPath = uploadPath.resolve(fullPath);
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath);
            
            log.info("文件上传成功: {}", fullPath);
            // 返回完整URL，格式: {baseUrl}/uploads/{fullPath}
            return baseUrl + "/uploads/" + fullPath;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }
    
    @Override
    public String uploadFile(byte[] data, String fileName, String contentType) {
        try {
            String fullPath = "images/" + fileName;
            Path targetPath = uploadPath.resolve(fullPath);
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, data);
            
            log.info("文件上传成功: {}", fullPath);
            // 返回完整URL
            return baseUrl + "/uploads/" + fullPath;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }
    
    @Override
    public String getFileUrl(String fileKey) {
        // 如果已经是完整URL，直接返回
        if (fileKey != null && fileKey.startsWith("http")) {
            return fileKey;
        }
        // 如果是相对路径，拼接 baseUrl
        if (fileKey != null && fileKey.startsWith("/uploads/")) {
            return baseUrl + fileKey;
        }
        return baseUrl + "/uploads/" + fileKey;
    }
    
    @Override
    public String generatePresignedUrl(String fileKey, int expireSeconds) {
        // 本地存储不需要预签名URL，直接返回完整URL
        return getFileUrl(fileKey);
    }
    
    @Override
    public boolean deleteFile(String fileKey) {
        try {
            String path = fileKey.startsWith(baseUrl + "/uploads/") 
                    ? fileKey.substring((baseUrl + "/uploads/").length())
                    : fileKey.startsWith("/uploads/") 
                        ? fileKey.substring("/uploads/".length()) 
                        : fileKey;
            // 如果是完整URL，提取路径部分
            if (path.startsWith("http")) {
                int idx = path.indexOf("/uploads/");
                if (idx >= 0) {
                    path = path.substring(idx + "/uploads/".length());
                }
            }
            Path targetPath = uploadPath.resolve(path);
            return Files.deleteIfExists(targetPath);
        } catch (IOException e) {
            log.error("删除文件失败: {}", fileKey, e);
            return false;
        }
    }
    
    @Override
    public String getStorageKey(String fileKey) {
        // 提取存储key（去掉baseUrl和/uploads前缀）
        if (fileKey.startsWith(baseUrl + "/uploads/")) {
            return fileKey.substring((baseUrl + "/uploads/").length());
        }
        if (fileKey.startsWith("/uploads/")) {
            return fileKey.substring("/uploads/".length());
        }
        return fileKey;
    }
    
    @Override
    public InputStream getFileInputStream(String fileKey) throws Exception {
        String path = fileKey.startsWith(baseUrl + "/uploads/") 
                ? fileKey.substring((baseUrl + "/uploads/").length())
                : fileKey.startsWith("/uploads/") 
                    ? fileKey.substring("/uploads/".length()) 
                    : fileKey;
        // 如果是完整URL，提取路径部分
        if (path.startsWith("http")) {
            int idx = path.indexOf("/uploads/");
            if (idx >= 0) {
                path = path.substring(idx + "/uploads/".length());
            }
        }
        Path targetPath = uploadPath.resolve(path);
        if (!Files.exists(targetPath)) {
            throw new IOException("文件不存在: " + path);
        }
        return Files.newInputStream(targetPath);
    }
    
    @Override
    public boolean fileExists(String fileKey) {
        try {
            String path = fileKey.startsWith(baseUrl + "/uploads/") 
                    ? fileKey.substring((baseUrl + "/uploads/").length())
                    : fileKey.startsWith("/uploads/") 
                        ? fileKey.substring("/uploads/".length()) 
                        : fileKey;
            // 如果是完整URL，提取路径部分
            if (path.startsWith("http")) {
                int idx = path.indexOf("/uploads/");
                if (idx >= 0) {
                    path = path.substring(idx + "/uploads/".length());
                }
            }
            Path targetPath = uploadPath.resolve(path);
            return Files.exists(targetPath);
        } catch (Exception e) {
            log.error("检查文件存在性失败: {}", fileKey, e);
            return false;
        }
    }
}
