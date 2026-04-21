package com.imagemanager.service.impl;

import com.imagemanager.config.StorageConfig;
import com.imagemanager.entity.Document;
import com.imagemanager.entity.Notification;
import com.imagemanager.repository.DocumentRepository;
import com.imagemanager.repository.NotificationRepository;
import com.imagemanager.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文档服务实现
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {
    
    @Autowired
    private StorageConfig storageConfig;
    
    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Value("${app.upload.document-max-size:5368709120}")
    private long maxDocumentSize; // 默认 5GB
    
    // 文档存储路径（绝对路径）
    private Path documentPath;
    
    // 存储服务基础URL
    private String baseUrl;
    
    // 当前用户ID（TODO: 从会话中获取）
    private static final String CURRENT_USER_ID = "user-1";
    
    @PostConstruct
    public void init() {
        // 使用存储配置的绝对路径
        this.documentPath = Paths.get(storageConfig.getLocalPath()).toAbsolutePath().normalize().resolve("documents");
        this.baseUrl = storageConfig.getBaseUrl();
        
        // 确保 baseUrl 格式正确（不以 / 结尾）
        if (baseUrl != null && baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        try {
            Files.createDirectories(documentPath);
            log.info("文档存储目录初始化成功: {}, baseUrl: {}", documentPath, baseUrl);
        } catch (IOException e) {
            log.error("创建文档存储目录失败", e);
        }
    }
    
    /**
     * 创建文档相关通知
     */
    private void createNotification(String type, String title, String content, String resourceId) {
        try {
            Notification notification = Notification.builder()
                    .id(UUID.randomUUID().toString())
                    .type(type)
                    .title(title)
                    .content(content)
                    .resourceId(resourceId)
                    .userId(CURRENT_USER_ID)
                    .read(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(notification);
            log.info("文档通知已创建: {} - {}", title, resourceId);
        } catch (Exception e) {
            log.error("创建文档通知失败", e);
        }
    }
    
    /**
     * 获取分类显示名称
     */
    private String getCategoryDisplayName(String category) {
        if (category == null) return "其他";
        switch (category.toLowerCase()) {
            case "pdf": return "PDF文档";
            case "word": return "Word文档";
            case "excel": return "Excel表格";
            case "ppt": return "PPT演示文稿";
            case "zip": return "压缩包";
            default: return "其他文件";
        }
    }
    
    @Override
    @Transactional
    public Map<String, Object> uploadDocument(MultipartFile file, String fileName) {
        return uploadDocument(file, fileName, null);
    }
    
    @Override
    @Transactional
    public Map<String, Object> uploadDocument(MultipartFile file, String fileName, String category) {
        try {
            // 验证文件大小
            if (file.getSize() > maxDocumentSize) {
                throw new RuntimeException("文件大小超过限制，最大支持 " + (maxDocumentSize / 1024 / 1024 / 1024) + "GB");
            }
            
            // 生成文档ID
            String id = UUID.randomUUID().toString();
            
            // 获取原始文件名和扩展名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            }
            
            // 如果未指定分类，根据扩展名自动判断
            String autoCategory = category;
            if (autoCategory == null || autoCategory.isEmpty()) {
                autoCategory = determineCategory(originalFilename);
            }
            
            // 生成 UUID 作为文件名
            String uuidFileName = UUID.randomUUID().toString();
            // 如果有扩展名，添加扩展名
            if (!extension.isEmpty()) {
                uuidFileName = uuidFileName + "." + extension;
            }
            
            // 存储路径：documents/assets/{uuid}.{ext}
            String relativePath = "assets/" + uuidFileName;
            
            // 存储文件
            Path targetPath = documentPath.resolve("assets").resolve(uuidFileName);
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath);
            
            // 构建访问URL - 使用配置的 baseUrl
            // 格式: {baseUrl}/uploads/documents/{id}/file
            String url = baseUrl + "/uploads/documents/" + id + "/file";
            
            // 构建文档实体并保存到数据库
            Document document = Document.builder()
                    .id(id)
                    .name(fileName)
                    .originalName(originalFilename)
                    .storedName(uuidFileName)
                    .filePath(relativePath)
                    .url(url)
                    .size(file.getSize())
                    .contentType(file.getContentType())
                    .extension(extension)
                    .category(autoCategory)
                    .userId("user-1") // TODO: 从会话中获取实际用户ID
                    .deleted(false)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            document = documentRepository.save(document);
            
            log.info("文档上传成功: {} -> {}, category: {}", fileName, url, autoCategory);
            
            // 创建上传通知
            createNotification(
                "document",
                "文档上传成功",
                "「" + fileName + "」已成功上传为" + getCategoryDisplayName(autoCategory),
                document.getId()
            );
            
            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("id", document.getId());
            result.put("name", document.getName());
            result.put("originalName", document.getOriginalName());
            result.put("url", document.getUrl());
            result.put("size", document.getSize());
            result.put("contentType", document.getContentType());
            result.put("extension", document.getExtension());
            result.put("category", document.getCategory());
            result.put("uploadTime", document.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            return result;
            
        } catch (IOException e) {
            log.error("文档上传失败", e);
            throw new RuntimeException("文档上传失败: " + e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public List<Map<String, Object>> batchUploadDocuments(List<MultipartFile> files) {
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;
        List<String> successFileNames = new ArrayList<>();
        
        for (MultipartFile file : files) {
            try {
                // 根据文件名自动判断分类
                String category = determineCategory(file.getOriginalFilename());
                Map<String, Object> result = uploadDocument(file, file.getOriginalFilename(), category);
                results.add(result);
                if (result.get("id") != null) {
                    successCount++;
                    successFileNames.add((String) result.get("originalName"));
                }
            } catch (Exception e) {
                log.error("批量上传中单个文件上传失败: {}", file.getOriginalFilename(), e);
                failCount++;
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("name", file.getOriginalFilename());
                errorResult.put("error", e.getMessage());
                results.add(errorResult);
            }
        }
        
        // 批量上传完成通知
        if (successCount > 0) {
            String title = successCount == 1 ? "文档上传成功" : "批量上传成功";
            String content = successCount == 1 
                ? "「" + successFileNames.get(0) + "」已成功上传"
                : "成功上传 " + successCount + " 个文档" + (failCount > 0 ? "，" + failCount + " 个失败" : "");
            createNotification("document", title, content, null);
        }
        
        return results;
    }
    
    /**
     * 根据文件名判断文档分类
     */
    private String determineCategory(String fileName) {
        if (fileName == null) return "other";
        String lowerName = fileName.toLowerCase();
        
        if (lowerName.endsWith(".pdf")) return "pdf";
        if (lowerName.endsWith(".doc") || lowerName.endsWith(".docx")) return "word";
        if (lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx") || lowerName.endsWith(".csv")) return "excel";
        if (lowerName.endsWith(".ppt") || lowerName.endsWith(".pptx")) return "ppt";
        if (lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") 
            || lowerName.endsWith(".tar") || lowerName.endsWith(".gz")) return "zip";
        
        return "other";
    }
    
    @Override
    @Transactional
    public void deleteDocument(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + id));
        
        // 软删除
        document.setDeleted(true);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        
        log.info("文档已删除: {}", id);
        
        // 创建删除通知
        createNotification(
            "document",
            "文档已移入回收站",
            "「" + document.getOriginalName() + "」已移入回收站",
            id
        );
    }
    
    @Override
    @Transactional
    public Map<String, Object> batchDeleteDocuments(List<String> ids) {
        int successCount = 0;
        int failCount = 0;
        List<String> successNames = new ArrayList<>();
        
        for (String id : ids) {
            try {
                Document document = documentRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("文档不存在: " + id));
                
                // 软删除
                document.setDeleted(true);
                document.setUpdatedAt(LocalDateTime.now());
                documentRepository.save(document);
                
                successCount++;
                successNames.add(document.getOriginalName());
            } catch (Exception e) {
                log.error("批量删除文档失败: {}", id, e);
                failCount++;
            }
        }
        
        // 创建批量删除通知
        if (successCount > 0) {
            String title = successCount == 1 ? "文档已移入回收站" : "批量文档已移入回收站";
            String content = successCount == 1
                ? "「" + successNames.get(0) + "」已移入回收站"
                : successCount + " 个文档已移入回收站" + (failCount > 0 ? "，" + failCount + " 个失败" : "");
            createNotification("document", title, content, null);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        return result;
    }
    
    @Override
    @Transactional
    public void restoreDocument(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + id));
        
        // 恢复
        document.setDeleted(false);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        
        log.info("文档已恢复: {}", id);
        
        // 创建恢复通知
        createNotification(
            "document",
            "文档已恢复",
            "「" + document.getOriginalName() + "」已从回收站恢复",
            id
        );
    }
    
    @Override
    @Transactional
    public Map<String, Object> batchRestoreDocuments(List<String> ids) {
        int successCount = 0;
        int failCount = 0;
        List<String> successNames = new ArrayList<>();
        
        for (String id : ids) {
            try {
                Document document = documentRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("文档不存在: " + id));
                
                // 恢复
                document.setDeleted(false);
                document.setUpdatedAt(LocalDateTime.now());
                documentRepository.save(document);
                
                successCount++;
                successNames.add(document.getOriginalName());
            } catch (Exception e) {
                log.error("批量恢复文档失败: {}", id, e);
                failCount++;
            }
        }
        
        // 创建批量恢复通知
        if (successCount > 0) {
            String title = successCount == 1 ? "文档已恢复" : "批量文档已恢复";
            String content = successCount == 1
                ? "「" + successNames.get(0) + "」已从回收站恢复"
                : successCount + " 个文档已从回收站恢复" + (failCount > 0 ? "，" + failCount + " 个失败" : "");
            createNotification("document", title, content, null);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        return result;
    }
    
    @Override
    @Transactional
    public void permanentDeleteDocument(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + id));
        
        // 获取文件路径并删除物理文件
        String filePath = document.getFilePath();
        if (filePath != null && !filePath.isEmpty()) {
            try {
                Path fullPath = Paths.get(filePath);
                if (Files.exists(fullPath)) {
                    Files.delete(fullPath);
                    log.info("文档物理文件已删除: {}", fullPath);
                }
            } catch (IOException e) {
                log.error("删除文档物理文件失败: {}", filePath, e);
            }
        }
        
        // 从数据库中永久删除
        documentRepository.delete(document);
        
        log.info("文档已永久删除: {}", id);
        
        // 创建永久删除通知
        createNotification(
            "document",
            "文档已永久删除",
            "「" + document.getOriginalName() + "」已被永久删除，无法恢复",
            id
        );
    }
    
    @Override
    public Map<String, Object> getDocumentById(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + id));
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", document.getId());
        result.put("name", document.getName());
        result.put("originalName", document.getOriginalName());
        result.put("storedName", document.getStoredName());
        result.put("filePath", document.getFilePath());
        result.put("url", document.getUrl());
        result.put("size", document.getSize());
        result.put("contentType", document.getContentType());
        result.put("extension", document.getExtension());
        result.put("category", document.getCategory());
        result.put("uploadTime", document.getCreatedAt() != null ? 
                document.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        
        return result;
    }
    
    @Override
    public String getDownloadUrl(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文档不存在: " + id));
        
        // 使用配置的 baseUrl
        return baseUrl + "/uploads/documents/" + id + "/file";
    }
    
    @Override
    public Map<String, Object> getDocuments(int page, int pageSize) {
        return getDocumentsByCategory(page, pageSize, null);
    }
    
    @Override
    public Map<String, Object> getDocumentsByCategory(int page, int pageSize, String category) {
        // 创建分页请求，按创建时间倒序
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<Document> documentPage;
        if (category == null || category.isEmpty() || "all".equals(category)) {
            documentPage = documentRepository.findByUserIdAndDeletedFalse("user-1", pageable);
        } else {
            documentPage = documentRepository.findByUserIdAndCategoryAndDeletedFalse("user-1", category, pageable);
        }
        
        // 转换为返回格式
        List<Map<String, Object>> documents = documentPage.getContent().stream()
                .map(doc -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", doc.getId());
                    item.put("name", doc.getName());
                    item.put("originalName", doc.getOriginalName());
                    item.put("url", doc.getUrl());
                    item.put("size", doc.getSize());
                    item.put("contentType", doc.getContentType());
                    item.put("extension", doc.getExtension());
                    item.put("category", doc.getCategory());
                    item.put("uploadTime", doc.getCreatedAt() != null ? 
                            doc.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
                    return item;
                })
                .collect(Collectors.toList());
        
        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("documents", documents);
        result.put("total", documentPage.getTotalElements());
        result.put("page", documentPage.getNumber());
        result.put("pageSize", documentPage.getSize());
        result.put("totalPages", documentPage.getTotalPages());
        
        return result;
    }
    
    @Override
    public Map<String, Integer> getDocumentStats() {
        // 统计各分类文档数量
        List<Object[]> categoryStats = documentRepository.countByCategoryGroupByCategory("user-1");
        
        Map<String, Integer> stats = new HashMap<>();
        stats.put("pdf", 0);
        stats.put("word", 0);
        stats.put("excel", 0);
        stats.put("ppt", 0);
        stats.put("zip", 0);
        stats.put("other", 0);
        stats.put("all", 0);
        
        int total = 0;
        for (Object[] row : categoryStats) {
            String cat = (String) row[0];
            Long count = (Long) row[1];
            if (stats.containsKey(cat)) {
                stats.put(cat, count.intValue());
            }
            total += count.intValue();
        }
        stats.put("all", total);
        
        return stats;
    }
}
