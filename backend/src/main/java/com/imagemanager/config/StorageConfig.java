package com.imagemanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 对象存储配置
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class StorageConfig {
    
    /**
     * 存储类型：local-本地存储, s3-S3兼容对象存储
     */
    private String type = "local";
    
    /**
     * 本地存储路径（绝对路径或相对路径）
     * 相对路径会基于运行目录解析为绝对路径
     */
    private String localPath = "./uploads";
    
    /**
     * 存储服务基础URL（用于生成文件访问URL）
     * 使用相对路径，不绑定具体域名，前端通过同域代理或当前域名访问
     * 例如: /api  -> 文件URL格式: /api/uploads/{filePath}
     */
    private String baseUrl = "/api";
    
    /**
     * S3端点URL
     */
    private String s3Endpoint;
    
    /**
     * S3访问密钥
     */
    private String s3AccessKey;
    
    /**
     * S3密钥
     */
    private String s3SecretKey;
    
    /**
     * S3存储桶名称
     */
    private String s3BucketName;
    
    /**
     * S3区域
     */
    private String s3Region = "cn-beijing";
    
    /**
     * 预签名URL有效期（秒）
     */
    private Integer presignedUrlExpire = 2592000; // 30天
}
