package com.imagemanager.service.impl;

import com.imagemanager.config.StorageProperties;
import com.imagemanager.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
public class LocalStorageServiceImpl implements FileStorageService {
    
    private StorageProperties storageProperties;
    private Path uploadPath;
    private String baseUrl;
    
    public LocalStorageServiceImpl(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        init();
    }
    
    public void init() {
        this.uploadPath = Paths.get(storageProperties.getLocalPath()).toAbsolutePath().normalize();
        this.baseUrl = storageProperties.getBaseUrl();
        
        // 确保 baseUrl 格式正确（不以 / 结尾）
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        try {
            Files.createDirectories(uploadPath);
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
            String fullPath = (path == null || path.isEmpty()) ? fileName : path + "/" + fileName;
            
            Path targetPath = uploadPath.resolve(fullPath);
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath);
            
            // 返回完整URL，格式: {baseUrl}/uploads/{fullPath}
            return baseUrl + "/uploads/" + fullPath;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }
    
    @Override
    public String uploadFileForKey(MultipartFile file, String directory, String fileName) {
        try {
            String dir = (directory == null || directory.isEmpty()) ? "images" : directory;
            String fullPath = dir + "/" + fileName;

            Path targetPath = uploadPath.resolve(fullPath);
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return fullPath;
        } catch (IOException e) {
            log.error("文件上传失败(指定key)", e);
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
        if (fileKey != null && (fileKey.startsWith("http://") || fileKey.startsWith("https://"))) {
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
            String path = extractLocalPath(fileKey);
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
        if (fileKey != null && fileKey.startsWith(baseUrl + "/uploads/")) {
            return fileKey.substring((baseUrl + "/uploads/").length());
        }
        if (fileKey != null && fileKey.startsWith("/uploads/")) {
            return fileKey.substring("/uploads/".length());
        }
        return fileKey;
    }
    
    @Override
    public InputStream getFileInputStream(String fileKey) throws Exception {
        String path = extractLocalPath(fileKey);
        Path targetPath = uploadPath.resolve(path);
        if (!Files.exists(targetPath)) {
            throw new IOException("文件不存在: " + path);
        }
        return Files.newInputStream(targetPath);
    }
    
    @Override
    public boolean fileExists(String fileKey) {
        try {
            String path = extractLocalPath(fileKey);
            Path targetPath = uploadPath.resolve(path);
            return Files.exists(targetPath);
        } catch (Exception e) {
            log.error("检查文件存在性失败: {}", fileKey, e);
            return false;
        }
    }
    
    /**
     * 从fileKey中提取本地相对路径
     * 处理各种格式：完整URL、带baseUrl前缀、带/uploads前缀、纯相对路径
     */
    private String extractLocalPath(String fileKey) {
        if (fileKey == null) return "";
        String path = fileKey;
        
        // 完整HTTP URL，提取路径部分
        if (path.startsWith("http://") || path.startsWith("https://")) {
            try {
                URI uri = URI.create(path);
                path = uri.getPath();
            } catch (@SuppressWarnings("unused") Exception e) {
                log.warn("URL解析失败，尝试字符串截取: {}", fileKey);
            }
        }
        
        // 去掉前缀
        if (path.startsWith(baseUrl + "/uploads/")) {
            path = path.substring((baseUrl + "/uploads/").length());
        } else if (path.startsWith("/api/uploads/")) {
            path = path.substring("/api/uploads/".length());
        } else if (path.startsWith("/uploads/")) {
            path = path.substring("/uploads/".length());
        } else if (path.startsWith("uploads/")) {
            path = path.substring("uploads/".length());
        }
        
        return path;
    }
}
