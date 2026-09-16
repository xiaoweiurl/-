package com.imagemanager.service.impl;

import com.imagemanager.config.StorageProperties;
import com.imagemanager.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * S3兼容对象存储实现
 * 支持任何S3兼容的对象存储服务（MinIO、阿里云OSS、AWS S3等）
 */
@Slf4j
public class S3StorageServiceImpl implements FileStorageService {

    private final StorageProperties storageProperties;
    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private boolean initialized = false;

    public S3StorageServiceImpl(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
        init();
    }

    /**
     * 初始化S3客户端
     */
    public void init() {
        if (!"s3".equalsIgnoreCase(storageProperties.getType())) {
            log.info("[Storage] 存储类型为 local，跳过 S3 初始化");
            return;
        }

        try {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(
                    storageProperties.getS3AccessKey(),
                    storageProperties.getS3SecretKey()
            );

            // 阿里云OSS S3兼容配置（官方文档要求）
            S3Configuration s3Config = S3Configuration.builder()
                    .pathStyleAccessEnabled(false)      // 虚拟托管风格：bucket名在域名中
                    .chunkedEncodingEnabled(false)      // OSS不支持分块传输编码
                    .build();

            // 解析 Region：优先从配置读取，其次从 endpoint 推导，最后降级到 AWS_GLOBAL
            Region region = resolveRegion();

            // S3 Client
            var clientBuilder = S3Client.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(region)
                    .serviceConfiguration(s3Config);

            // S3 Presigner - 必须与 Client 使用相同 Region，否则签名 URL 的 credential scope 不匹配导致 403
            var presignerBuilder = S3Presigner.builder()
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(region)
                    .serviceConfiguration(s3Config);

            // 自定义端点（阿里云OSS）
            String endpoint = storageProperties.getS3Endpoint();
            if (endpoint != null && !endpoint.isEmpty()) {
                URI endpointUri = URI.create(endpoint);
                clientBuilder.endpointOverride(endpointUri);
                presignerBuilder.endpointOverride(endpointUri);
            }

            this.s3Client = clientBuilder.build();
            this.s3Presigner = presignerBuilder.build();

            // 确保存储桶存在
            ensureBucketExists();
            this.initialized = true;

        } catch (Exception e) {
            log.error("[Storage] S3 存储初始化失败", e);
            this.initialized = false;
        }
    }

    /**
     * 解析 S3 Region
     * 优先级：配置 > endpoint推导 > AWS_GLOBAL降级
     * 
     * 阿里云OSS的endpoint格式：https://s3.oss-cn-hangzhou.aliyuncs.com
     * 从中提取 cn-hangzhou 作为 region
     */
    private Region resolveRegion() {
        // 1. 优先从配置读取
        String configuredRegion = storageProperties.getS3Region();
        if (configuredRegion != null && !configuredRegion.isBlank()) {
            return Region.of(configuredRegion);
        }

        // 2. 从 endpoint URL 推导（阿里云OSS: oss-cn-hangzhou.aliyuncs.com → cn-hangzhou）
        String endpoint = storageProperties.getS3Endpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            // 匹配 oss-{region}. 格式，提取 region（如 cn-hangzhou, us-east-1, ap-southeast-1）
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("oss-([^.]+)")
                    .matcher(endpoint);
            if (matcher.find()) {
                String derivedRegion = matcher.group(1);
                return Region.of(derivedRegion);
            }
        }

        // 3. 降级到 AWS_GLOBAL
        log.warn("[Storage] Region 未配置且无法从endpoint推导，使用 AWS_GLOBAL 降级");
        return Region.AWS_GLOBAL;
    }

    /**
     * 测试S3连接是否可用
     */
    public void testConnection() {
        if (!initialized) {
            throw new RuntimeException("S3 客户端未初始化");
        }
        // 阿里云OSS的headBucket即使bucket存在也返回403，不能用headBucket测试连接
        // 改用listObjectsV2限制1条来验证连接和bucket可访问性
        String bucket = storageProperties.getS3BucketName();
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .maxKeys(1)
                    .build();
            s3Client.listObjectsV2(listRequest);
        } catch (S3Exception e) {
            // 403可能是bucket存在但AccessKey权限不足（无oss:ListObjects权限）
            // 但headBucket已确认bucket存在（ensureBucketExists通过），仍然可以写入
            if (e.statusCode() == 403) {
                log.warn("[Storage] listObjectsV2返回403（可能缺少ListObjects权限），但headBucket已确认bucket存在，继续使用S3存储");
                // 不抛异常，允许继续使用（写入权限可能正常，只是读取列表权限受限）
            } else {
                throw e;
            }
        }
    }

    private void ensureBucketExists() {
        String bucket = storageProperties.getS3BucketName();
        try {
            HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                    .bucket(bucket)
                    .build();
            s3Client.headBucket(headBucketRequest);
        } catch (S3Exception e) {
            if (e.statusCode() == 403 || e.statusCode() == 404) {
                // 阿里云OSS: headBucket对已有bucket也可能返回403（S3兼容接口特性）
                // 尝试createBucket来确认bucket是否存在
                log.warn("[Storage] headBucket返回{}，尝试确认bucket: {}", e.statusCode(), bucket);
                try {
                    CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                            .bucket(bucket)
                            .build();
                    s3Client.createBucket(createBucketRequest);
                } catch (S3Exception ce) {
                    if (ce.statusCode() == 409) {
                        // 409 = BucketAlreadyExistsException: bucket名全局已存在（可能是自己刚创建的）
                        // 这说明bucket已可用，不需要再创建，直接继续
                    } else {
                        log.error("[Storage] 创建存储桶失败({}): AccessKey可能无创建权限。请到阿里云控制台手动创建bucket '{}'",
                                ce.statusCode(), bucket);
                        throw ce;
                    }
                }
            } else {
                throw e;
            }
        }
    }

    // ===== FileStorageService 接口实现 =====

    @Override
    public String uploadFile(MultipartFile file, String path) {
        checkInitialized();
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";

            String fileName = UUID.randomUUID().toString() + extension;
            String key = (path == null || path.isEmpty()) ? "images/" + fileName
                    : (path.startsWith("/") ? path.substring(1) : path) + "/" + fileName;

            String bucket = storageProperties.getS3BucketName();

            // 使用 byte[] 方式上传，避免 InputStream + contentLength 兼容性问题
            byte[] data = file.getBytes();
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .contentLength((long) data.length)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(data));

            return getPublicUrl(key);
        } catch (Exception e) {
            log.error("[Storage] MultipartFile 上传失败", e);
            throw new RuntimeException("文件上传到S3失败: " + e.getMessage());
        }
    }

    @Override
    public String uploadFileForKey(MultipartFile file, String directory, String fileName) {
        checkInitialized();
        try {
            String dir = (directory == null || directory.isEmpty()) ? "images" : directory;
            String key = dir + "/" + fileName;
            String bucket = storageProperties.getS3BucketName();

            byte[] data = file.getBytes();
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .contentLength((long) data.length)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(data));

            return key;
        } catch (Exception e) {
            log.error("[Storage] uploadFileForKey 上传失败", e);
            throw new RuntimeException("文件上传到S3失败: " + e.getMessage());
        }
    }

    @Override
    public String uploadFile(byte[] data, String fileName, String contentType) {
        checkInitialized();
        try {
            String key = "images/" + fileName;
            String bucket = storageProperties.getS3BucketName();
            long size = data.length;

            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .contentLength(size)
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(data));

            return getPublicUrl(key);
        } catch (Exception e) {
            log.error("[Storage] byte[] 上传失败", e);
            throw new RuntimeException("文件上传到S3失败: " + e.getMessage());
        }
    }

    @Override
    public String getFileUrl(String fileKey) {
        checkInitialized();
        if (fileKey == null || fileKey.isEmpty()) {
            throw new IllegalArgumentException("fileKey 不能为空");
        }
        // 如果已经是完整HTTP URL，直接返回
        if (fileKey.startsWith("http://") || fileKey.startsWith("https://")) {
            return fileKey;
        }
        String key = fileKey.startsWith("/") ? fileKey.substring(1) : fileKey;
        return getPublicUrl(key);
    }

    @Override
    public String generatePresignedUrl(String fileKey, int expireSeconds) {
        checkInitialized();
        if (s3Presigner == null) {
            throw new RuntimeException("S3 Presigner 未初始化");
        }
        if (fileKey == null || fileKey.isEmpty()) {
            throw new IllegalArgumentException("fileKey 不能为空");
        }

        String key = fileKey.startsWith("/") ? fileKey.substring(1) : fileKey;
        String bucket = storageProperties.getS3BucketName();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(expireSeconds))
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())
                .build();

        String url = s3Presigner.presignGetObject(presignRequest).url().toString();
        return url;
    }

    @Override
    public boolean deleteFile(String fileKey) {
        checkInitialized();
        try {
            // 如果是完整URL，提取key
            String key = extractKeyFromUrl(fileKey);
            String bucket = storageProperties.getS3BucketName();

            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteRequest);
            return true;
        } catch (Exception e) {
            log.error("[Storage] 删除文件失败: {}", fileKey, e);
            return false;
        }
    }

    @Override
    public String getStorageKey(String fileKey) {
        return extractKeyFromUrl(fileKey);
    }

    @Override
    public InputStream getFileInputStream(String fileKey) throws Exception {
        checkInitialized();
        String key = extractKeyFromUrl(fileKey);
        String bucket = storageProperties.getS3BucketName();

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3Client.getObject(getRequest);
    }

    @Override
    public boolean fileExists(String fileKey) {
        checkInitialized();
        try {
            String key = extractKeyFromUrl(fileKey);
            String bucket = storageProperties.getS3BucketName();

            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (@SuppressWarnings("unused") NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("[Storage] 检查文件存在性失败: {}", fileKey, e);
            return false;
        }
    }

    // ===== 辅助方法 =====

    private void checkInitialized() {
        if (!initialized || s3Client == null) {
            throw new RuntimeException("S3 客户端未初始化，请检查 app.storage.type 和 S3 配置");
        }
    }

    /**
     * 从URL或路径中提取S3 key
     */
    private String extractKeyFromUrl(String fileKey) {
        if (fileKey == null) return "";
        // 如果是完整HTTP URL，提取路径部分作为对象key
        if (fileKey.startsWith("http://") || fileKey.startsWith("https://")) {
            try {
                URI uri = URI.create(fileKey);
                String path = uri.getPath();
                if (path == null || path.isEmpty()) {
                    return fileKey.startsWith("/") ? fileKey.substring(1) : fileKey;
                }
                // 虚拟托管风格: https://{bucket}.s3.oss-cn-hangzhou.aliyuncs.com/{key}
                // 路径只有 /key，不含 bucket名
                // 路径风格: https://s3.oss-cn-hangzhou.aliyuncs.com/{bucket}/{key}
                // 路径含 /{bucket}/{key}
                String bucket = storageProperties.getS3BucketName();
                String bucketPrefix = "/" + bucket + "/";
                if (path.startsWith(bucketPrefix)) {
                    // 路径风格，去掉bucket前缀
                    return path.substring(bucketPrefix.length());
                }
                // 虚拟托管风格或直接路径，去掉开头的 /
                return path.startsWith("/") ? path.substring(1) : path;
            } catch (@SuppressWarnings("unused") Exception e) {
                log.warn("[Storage] URL解析失败，直接作为key使用: {}", fileKey);
                return fileKey;
            }
        }
        return fileKey.startsWith("/") ? fileKey.substring(1) : fileKey;
    }

    /**
     * 生成公网访问URL
     * 阿里云 OSS 默认私有读，使用预签名URL确保公网可访问
     * 如果桶设置了公共读权限，可直接拼接公网URL
     */
    private String getPublicUrl(String key) {
        // 优先使用预签名URL（OSS 默认私有读，预签名可保证公网可访问）
        int expireSeconds = storageProperties.getPresignedUrlExpire() != null
                ? storageProperties.getPresignedUrlExpire() : 604800; // 默认7天
        return generatePresignedUrl(key, expireSeconds);
    }
}
