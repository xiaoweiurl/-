package com.imagemanager.service.impl;

import com.imagemanager.config.StorageProperties;
import com.imagemanager.entity.Image;
import com.imagemanager.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImagePresignedUrlRefreshTest {

    private FileStorageService fileStorageService;
    private StorageProperties storageProperties;
    private ImageServiceImpl imageService;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        storageProperties = new StorageProperties();
        storageProperties.setType("s3");
        storageProperties.setS3Endpoint("https://s3.oss-cn-hangzhou.aliyuncs.com");
        storageProperties.setS3AccessKey("ak");
        storageProperties.setS3SecretKey("sk");
        storageProperties.setS3BucketName("yingyun");

        imageService = new ImageServiceImpl();
        ReflectionTestUtils.setField(imageService, "fileStorageService", fileStorageService);
        ReflectionTestUtils.setField(imageService, "storageProperties", storageProperties);
    }

    @Test
    void relativeUploadUrlBecomesOssPresignedWhenS3Active() {
        when(fileStorageService.generatePresignedUrl("images/abc.jpg", 604800))
                .thenReturn("https://yingyun.oss-cn-hangzhou.aliyuncs.com/images/abc.jpg?X-Amz-Signature=sig");

        Image image = new Image();
        image.setUrl("/api/uploads/images/abc.jpg");
        image.setFileKey("images/abc.jpg");
        image.setThumbnailUrl("/uploads/images/abc.jpg");

        imageService.refreshImagePresignedUrls(List.of(image));

        assertEquals("https://yingyun.oss-cn-hangzhou.aliyuncs.com/images/abc.jpg?X-Amz-Signature=sig",
                image.getUrl());
        assertEquals("https://yingyun.oss-cn-hangzhou.aliyuncs.com/images/abc.jpg?X-Amz-Signature=sig",
                image.getThumbnailUrl());
    }

    @Test
    void localStorageDoesNotRewriteRelativeUploadUrl() {
        storageProperties.setType("local");

        Image image = new Image();
        image.setUrl("/api/uploads/images/abc.jpg");
        image.setFileKey("images/abc.jpg");

        imageService.refreshImagePresignedUrls(List.of(image));

        assertEquals("/api/uploads/images/abc.jpg", image.getUrl());
        verify(fileStorageService, never()).generatePresignedUrl("images/abc.jpg", 604800);
    }
}
