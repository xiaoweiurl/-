package com.imagemanager.controller;

import com.imagemanager.config.StorageProperties;
import com.imagemanager.dto.ApiResponse;
import com.imagemanager.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadsControllerTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;
    private StorageProperties storageProperties;
    private UploadsController controller;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        storageProperties = new StorageProperties();
        storageProperties.setLocalPath(tempDir.toString());
        controller = new UploadsController(fileStorageService, storageProperties);
    }

    @Test
    void localHitStreamsFileWithoutTouchingS3() throws Exception {
        Path images = tempDir.resolve("images");
        Files.createDirectories(images);
        byte[] bytes = "local-jpeg".getBytes(StandardCharsets.UTF_8);
        Files.write(images.resolve("abc.jpg"), bytes);

        ResponseEntity<?> response = controller.getUpload(request("images/abc.jpg"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("image/jpeg", response.getHeaders().getContentType().toString());
        FileSystemResource resource = assertInstanceOf(FileSystemResource.class, response.getBody());
        try (var in = resource.getInputStream()) {
            assertArrayEquals(bytes, in.readAllBytes());
        }
        verify(fileStorageService, never()).getFileInputStream(anyString());
    }

    @Test
    void missingLocalFallsBackToS3Stream() throws Exception {
        byte[] bytes = "from-oss".getBytes(StandardCharsets.UTF_8);
        when(fileStorageService.getFileInputStream("images/oss.jpg"))
                .thenReturn(new ByteArrayInputStream(bytes));

        ResponseEntity<?> response = controller.getUpload(request("images/oss.jpg"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        StreamingResponseBody body = assertInstanceOf(StreamingResponseBody.class, response.getBody());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        body.writeTo(out);
        assertArrayEquals(bytes, out.toByteArray());
        verify(fileStorageService).getFileInputStream("images/oss.jpg");
    }

    @Test
    void missingLocalAndS3Returns404Json() throws Exception {
        when(fileStorageService.getFileInputStream("images/missing.jpg"))
                .thenThrow(new IOException("文件不存在: images/missing.jpg"));

        ResponseEntity<?> response = controller.getUpload(request("images/missing.jpg"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        @SuppressWarnings("unchecked")
        ApiResponse<Void> body = (ApiResponse<Void>) response.getBody();
        assertNotNull(body);
        assertEquals(404, body.getCode());
        assertTrue(body.getMessage().contains("文件不存在"));
    }

    @Test
    void noSuchKeyFromS3Returns404Not500() throws Exception {
        when(fileStorageService.getFileInputStream("images/gone.jpg"))
                .thenThrow(NoSuchKeyException.builder().message("Not Found").statusCode(404).build());

        ResponseEntity<?> response = controller.getUpload(request("images/gone.jpg"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        @SuppressWarnings("unchecked")
        ApiResponse<Void> body = (ApiResponse<Void>) response.getBody();
        assertNotNull(body);
        assertEquals(404, body.getCode());
    }

    @Test
    void requestWithUploadsPrefixStillResolvesStorageKey() throws Exception {
        byte[] bytes = "prefixed".getBytes(StandardCharsets.UTF_8);
        when(fileStorageService.getFileInputStream("images/prefixed.jpg"))
                .thenReturn(new ByteArrayInputStream(bytes));

        MockHttpServletRequest req = request("/uploads/images/prefixed.jpg");
        ResponseEntity<?> response = controller.getUpload(req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(fileStorageService).getFileInputStream("images/prefixed.jpg");
    }

    private static MockHttpServletRequest request(String pathWithinMapping) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/uploads/" + pathWithinMapping.replaceFirst("^/+", ""));
        request.setContextPath("/api");
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, pathWithinMapping);
        return request;
    }
}
