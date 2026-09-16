package com.imagemanager.config;

import com.imagemanager.service.FileStorageService;
import com.imagemanager.service.impl.LocalStorageServiceImpl;
import com.imagemanager.service.impl.S3StorageServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 对象存储 Bean 注册。
 * <p>
 * - imageStorageService（{@code @Primary}）：图片存储（S3 优先，凭据缺失或连接失败时降级本地）
 * - localFileStorageService：文档/知识库/记忆库本地存储（始终本地）
 * <p>
 * 实现类本身不是 {@code @Service}，只通过本配置创建。S3 未配置时也一定注册本地 Bean，
 * 保证 {@code AiImageController} 等按类型注入始终能拿到 FileStorageService。
 */
@Slf4j
@Configuration
public class StorageConfig {

    /**
     * 图片存储服务（S3 优先，降级到本地）。
     * 用于：ImageServiceImpl, AiImageController, UserController, WatermarkRemoveServiceImpl
     */
    @Bean
    @Primary
    public FileStorageService imageStorageService(StorageProperties properties) {
        properties.applyEnvironmentOverrides();

        if (properties.isS3Configured()) {
            try {
                S3StorageServiceImpl s3 = new S3StorageServiceImpl(properties);
                s3.testConnection();
                log.info("[Storage] 图片存储 - S3 初始化成功: {}/{}",
                        properties.getS3Endpoint(), properties.getS3BucketName());
                return s3;
            } catch (Exception e) {
                log.warn("[Storage] S3连接失败，图片存储降级到本地: {}", e.getMessage());
                return createLocalStorage(properties);
            }
        }

        log.info("[Storage] 图片存储 - 使用本地存储模式");
        return createLocalStorage(properties);
    }

    /**
     * 文档/知识库/记忆库本地存储服务（始终本地，不上传到 S3）。
     * 用于：KnowledgeBaseServiceImpl 等
     */
    @Bean
    public FileStorageService localFileStorageService(StorageProperties properties) {
        log.info("[Storage] 文档存储 - 使用本地存储: {}", properties.getLocalPath());
        return createLocalStorage(properties);
    }

    private LocalStorageServiceImpl createLocalStorage(StorageProperties properties) {
        return new LocalStorageServiceImpl(properties);
    }
}
