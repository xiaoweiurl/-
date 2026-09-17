package com.imagemanager.controller;

import com.imagemanager.config.StorageProperties;
import com.imagemanager.dto.ApiResponse;
import com.imagemanager.service.FileStorageService;
import com.imagemanager.util.UploadStorageKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import jakarta.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * 上传文件回源：本地磁盘优先，缺失时按存储 key 从主存储（S3/OSS）读取。
 * <p>
 * 替代仅映射本地目录的静态资源处理器，避免缺文件时 {@code NoResourceFoundException}
 * 被全局异常处理成 500。
 */
@Slf4j
@RestController
public class UploadsController {

    private final FileStorageService fileStorageService;
    private final StorageProperties storageProperties;

    public UploadsController(@Qualifier("imageStorageService") FileStorageService fileStorageService,
                             StorageProperties storageProperties) {
        this.fileStorageService = fileStorageService;
        this.storageProperties = storageProperties;
    }

    @RequestMapping(value = "/uploads/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<?> getUpload(HttpServletRequest request) {
        String storageKey;
        try {
            storageKey = resolveRequestKey(request);
        } catch (IllegalArgumentException e) {
            return notFound(e.getMessage());
        }

        if (storageKey == null || storageKey.isEmpty()) {
            return notFound("文件不存在");
        }

        try {
            Path localFile = resolveLocalFile(storageKey);
            if (localFile != null && Files.isRegularFile(localFile)) {
                MediaType mediaType = mediaTypeFor(storageKey);
                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .contentLength(Files.size(localFile))
                        .body(new FileSystemResource(localFile));
            }

            return streamFromPrimaryStorage(storageKey);
        } catch (Exception e) {
            if (isMissingObject(e)) {
                log.info("上传文件不存在: {}", storageKey);
                return notFound("文件不存在: " + storageKey);
            }
            log.error("读取上传文件失败: {}", storageKey, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error(502, "读取存储失败"));
        }
    }

    private ResponseEntity<?> streamFromPrimaryStorage(String storageKey) {
        InputStream inputStream;
        try {
            inputStream = fileStorageService.getFileInputStream(storageKey);
        } catch (Exception e) {
            if (isMissingObject(e)) {
                return notFound("文件不存在: " + storageKey);
            }
            log.error("从对象存储读取失败: {}", storageKey, e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.error(502, "读取存储失败"));
        }

        if (inputStream == null) {
            return notFound("文件不存在: " + storageKey);
        }

        StreamingResponseBody body = outputStream -> {
            try (InputStream in = inputStream) {
                in.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .contentType(mediaTypeFor(storageKey))
                .body(body);
    }

    String resolveRequestKey(HttpServletRequest request) {
        String remaining = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (remaining == null || remaining.isBlank()) {
            String uri = request.getRequestURI();
            String ctx = request.getContextPath();
            if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
                uri = uri.substring(ctx.length());
            }
            remaining = uri;
        }
        String key = UploadStorageKeys.fromUrlOrPath(remaining);
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("无效的文件路径");
        }
        return key;
    }

    Path resolveLocalFile(String storageKey) {
        String localPath = storageProperties.getLocalPath();
        if (localPath == null || localPath.isBlank()) {
            localPath = "./uploads";
        }
        Path root = Paths.get(localPath).toAbsolutePath().normalize();
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("无效的文件路径");
        }
        return resolved;
    }

    static boolean isMissingObject(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof NoSuchKeyException || current instanceof NoSuchFileException) {
                return true;
            }
            if (current instanceof S3Exception s3 && s3.statusCode() == 404) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("nosuchkey")
                        || lower.contains("文件不存在")
                        || lower.contains("not found")
                        || lower.contains("no such key")
                        || lower.contains("no such file")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static ResponseEntity<ApiResponse<Void>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(404, message));
    }

    static MediaType mediaTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (lower.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.endsWith(".svg")) {
            return MediaType.parseMediaType("image/svg+xml");
        }
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
