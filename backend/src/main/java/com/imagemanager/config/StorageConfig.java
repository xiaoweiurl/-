package com.imagemanager.config;

import com.imagemanager.service.FileStorageService;
import com.imagemanager.service.impl.LocalStorageServiceImpl;
import com.imagemanager.service.impl.S3StorageServiceImpl;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 对象存储配置
 * - imageStorageService: 图片存储（S3优先，降级到本地）
 * - localFileStorageService: 文档/知识库/记忆库本地存储（始终本地）
 *
 * S3 密钥硬编码在 Java 类中（避免 YAML 提交 Git 泄露）
 * 环境变量可覆盖
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class StorageConfig {

    private String type = "s3";
    private String localPath = "./uploads";
    private String baseUrl = "/api";

    // ===== 阿里云 OSS 配置（通过环境变量注入，不硬编码密钥）=====
    private String s3Endpoint = "";        // 环境变量 S3_ENDPOINT
    private String s3Region = "";          // 环境变量 S3_REGION
    private String s3BucketName = "";      // 环境变量 S3_BUCKET_NAME
    private String s3AccessKey = "";       // 环境变量 S3_ACCESS_KEY
    private String s3SecretKey = "";       // 环境变量 S3_SECRET_KEY
    private Integer presignedUrlExpire = 604800; // 7天

    /**
     * 图片存储服务（S3优先，降级到本地）
     * 用于：ImageServiceImpl, AiImageController, UserController, WatermarkRemoveServiceImpl
     */
    @Bean
    @Primary
    public FileStorageService imageStorageService() {
        applyEnvironmentOverrides();

        if ("s3".equalsIgnoreCase(type) && s3Endpoint != null && !s3Endpoint.isBlank()
                && s3AccessKey != null && !s3AccessKey.isBlank()
                && s3SecretKey != null && !s3SecretKey.isBlank()
                && s3BucketName != null && !s3BucketName.isBlank()) {
            try {
                S3StorageServiceImpl s3 = new S3StorageServiceImpl(this);
                s3.testConnection();
                log.info("[Storage] 图片存储 - S3 初始化成功: {}/{}", s3Endpoint, s3BucketName);
                return s3;
            } catch (Exception e) {
                log.warn("[Storage] S3连接失败，图片存储降级到本地: {}", e.getMessage());
                return createLocalStorage();
            }
        } else {
            log.info("[Storage] 图片存储 - 使用本地存储模式");
            return createLocalStorage();
        }
    }

    /**
     * 文档/知识库/记忆库本地存储服务（始终本地，不上传到S3）
     * 用于：KnowledgeBaseServiceImpl 等
     */
    @Bean
    public FileStorageService localFileStorageService() {
        log.info("[Storage] 文档存储 - 使用本地存储: {}", localPath);
        return createLocalStorage();
    }

    /**
     * 兼容旧名：fileStorageService 指向 imageStorageService
     */
    @Bean
    public FileStorageService fileStorageService() {
        return imageStorageService();
    }

    /**
     * 环境变量覆盖硬编码默认值（方便生产部署时注入）
     */
    private void applyEnvironmentOverrides() {
        String env;
        env = System.getenv("S3_ENDPOINT");
        if (env != null && !env.isBlank()) s3Endpoint = env;
        env = System.getenv("S3_REGION");
        if (env != null && !env.isBlank()) s3Region = env;
        env = System.getenv("S3_BUCKET_NAME");
        if (env != null && !env.isBlank()) s3BucketName = env;
        env = System.getenv("S3_ACCESS_KEY");
        if (env != null && !env.isBlank()) s3AccessKey = env;
        env = System.getenv("S3_SECRET_KEY");
        if (env != null && !env.isBlank()) s3SecretKey = env;
        env = System.getenv("STORAGE_TYPE");
        if (env != null && !env.isBlank()) type = env;
    }

    private LocalStorageServiceImpl createLocalStorage() {
        LocalStorageServiceImpl local = new LocalStorageServiceImpl();
        local.setStorageConfig(this);
        local.init();
        return local;
    }
}
