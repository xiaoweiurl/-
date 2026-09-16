package com.imagemanager.config;

import com.imagemanager.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies StorageConfig actually registers FileStorageService beans.
 * The previous @Configuration + @ConfigurationProperties + @Data combo
 * could skip @Bean methods entirely (no [Storage] logs, missing bean).
 */
class StorageConfigTest {

    @Test
    void registersPrimaryAndLocalFileStorageServiceBeans() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setType("local");
        properties.setLocalPath(Files.createTempDirectory("storage-config-test").toString());
        properties.setBaseUrl("/api");

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(StorageProperties.class, () -> properties);
            ctx.register(StorageConfig.class);
            ctx.refresh();

            Map<String, FileStorageService> beans = ctx.getBeansOfType(FileStorageService.class);
            assertTrue(beans.containsKey("imageStorageService"),
                    "imageStorageService @Bean must be registered");
            assertTrue(beans.containsKey("localFileStorageService"),
                    "localFileStorageService @Bean must be registered");
            assertEquals(2, beans.size());

            FileStorageService primary = ctx.getBean(FileStorageService.class);
            assertNotNull(primary);
            assertSame(beans.get("imageStorageService"), primary,
                    "@Primary imageStorageService should be used for unqualified injection");
        }
    }

    @Test
    void emptyS3CredentialsFallBackToLocalBean() throws Exception {
        StorageProperties properties = new StorageProperties();
        properties.setType("s3");
        properties.setS3Endpoint("");
        properties.setS3AccessKey("");
        properties.setS3SecretKey("");
        properties.setS3BucketName("");
        properties.setLocalPath(Files.createTempDirectory("storage-config-fallback").toString());
        properties.setBaseUrl("/api");

        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(StorageProperties.class, () -> properties);
            ctx.register(StorageConfig.class);
            ctx.refresh();

            FileStorageService primary = ctx.getBean(FileStorageService.class);
            assertNotNull(primary, "FileStorageService must still be created when S3 credentials are empty");
            assertTrue(ctx.containsBean("imageStorageService"));
            assertTrue(ctx.containsBean("localFileStorageService"));
        }
    }
}
