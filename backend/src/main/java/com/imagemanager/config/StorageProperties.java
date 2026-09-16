package com.imagemanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对象存储配置属性。
 * <p>
 * 必须与 {@link StorageConfig}（{@code @Configuration} + {@code @Bean}）拆开：
 * 在配置类上同时使用 {@code @ConfigurationProperties} 和 Lombok {@code @Data}
 * 会生成基于可变字段的 equals/hashCode，破坏 CGLIB 对 {@code @Bean} 方法的代理，
 * 导致 FileStorageService 根本不会被注册。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String type = "s3";
    private String localPath = "./uploads";
    private String baseUrl = "/api";

    private String s3Endpoint = "";
    private String s3Region = "";
    private String s3BucketName = "";
    private String s3AccessKey = "";
    private String s3SecretKey = "";
    private Integer presignedUrlExpire = 604800;

    public boolean isS3Configured() {
        return "s3".equalsIgnoreCase(type)
                && isPresent(s3Endpoint)
                && isPresent(s3AccessKey)
                && isPresent(s3SecretKey)
                && isPresent(s3BucketName);
    }

    /**
     * 短名环境变量覆盖（S3_ENDPOINT 等）。
     * Spring Boot 只会把 APP_STORAGE_S3_ENDPOINT 绑到 app.storage.s3-endpoint，
     * 这里保留项目一直使用的短名变量。
     */
    public void applyEnvironmentOverrides() {
        String env = System.getenv("S3_ENDPOINT");
        if (isPresent(env)) {
            s3Endpoint = env;
        }
        env = System.getenv("S3_REGION");
        if (isPresent(env)) {
            s3Region = env;
        }
        env = System.getenv("S3_BUCKET_NAME");
        if (isPresent(env)) {
            s3BucketName = env;
        }
        env = System.getenv("S3_ACCESS_KEY");
        if (isPresent(env)) {
            s3AccessKey = env;
        }
        env = System.getenv("S3_SECRET_KEY");
        if (isPresent(env)) {
            s3SecretKey = env;
        }
        env = System.getenv("STORAGE_TYPE");
        if (isPresent(env)) {
            type = env;
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
