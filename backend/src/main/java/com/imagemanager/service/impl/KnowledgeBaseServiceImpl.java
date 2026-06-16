package com.imagemanager.service.impl;

import com.imagemanager.entity.KnowledgeBaseCategory;
import com.imagemanager.entity.KnowledgeBaseDoc;
import com.imagemanager.repository.KnowledgeBaseCategoryRepository;
import com.imagemanager.repository.KnowledgeBaseDocRepository;
import com.imagemanager.service.FileStorageService;
import com.imagemanager.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseDocRepository docRepository;
    private final KnowledgeBaseCategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;

    @Override
    public KnowledgeBaseDoc uploadDocument(MultipartFile file, String title, UUID categoryId, List<String> tags, String userId) {
        try {
            // 文件扩展名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            }

            // 存储路径
            String storagePath = "knowledge/" + userId + "/" + UUID.randomUUID() + "." + extension;
            String fileUrl = fileStorageService.uploadFile(file, storagePath);

            // 判断文件类型
            String fileType = determineFileType(extension);

            KnowledgeBaseDoc doc = new KnowledgeBaseDoc();
            doc.setId(UUID.randomUUID());
            doc.setTitle(title != null && !title.isEmpty() ? title : originalFilename);
            doc.setFileName(originalFilename);
            doc.setFilePath(fileUrl);
            doc.setFileType(fileType);
            doc.setFileSize(file.getSize());
            doc.setCategoryId(categoryId);
            doc.setTags(tags);
            doc.setUserId(userId);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());

            return docRepository.save(doc);
        } catch (Exception e) {
            log.error("知识库文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public Page<KnowledgeBaseDoc> getDocuments(String userId, Pageable pageable) {
        return docRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public List<KnowledgeBaseDoc> getDocumentsByCategory(String userId, UUID categoryId) {
        return docRepository.findByUserIdAndCategoryIdOrderByCreatedAtDesc(userId, categoryId);
    }

    @Override
    public void deleteDocument(UUID id, String userId) {
        KnowledgeBaseDoc doc = docRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("文档不存在或无权限"));

        // 删除存储的文件
        try {
            fileStorageService.deleteFile(doc.getFileUrl());
        } catch (Exception e) {
            log.warn("删除知识库文件失败: {}", e.getMessage());
        }

        docRepository.delete(doc);
    }

    @Override
    public KnowledgeBaseDoc getDocumentDetail(UUID id, String userId) {
        return docRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("文档不存在或无权限"));
    }

    @Override
    public KnowledgeBaseCategory createCategory(String name, String description, UUID parentId, String userId) {
        KnowledgeBaseCategory category = new KnowledgeBaseCategory();
        category.setId(UUID.randomUUID());
        category.setName(name);
        category.setDescription(description);
        category.setParentId(parentId);
        category.setUserId(userId);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    @Override
    public List<KnowledgeBaseCategory> getCategories(String userId) {
        return categoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public void deleteCategory(UUID id, String userId) {
        // 检查分类下是否有文档
        long docCount = docRepository.countByCategoryId(id);
        if (docCount > 0) {
            throw new RuntimeException("该分类下存在文档，无法删除");
        }

        KnowledgeBaseCategory category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("分类不存在或无权限"));
        categoryRepository.delete(category);
    }

    @Override
    public long getDocumentCount(String userId) {
        return docRepository.countByUserId(userId);
    }

    private String determineFileType(String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> "pdf";
            case "doc", "docx" -> "word";
            case "xls", "xlsx", "csv" -> "excel";
            case "ppt", "pptx" -> "ppt";
            case "txt", "text" -> "txt";
            case "md", "markdown" -> "markdown";
            case "zip", "rar", "7z" -> "archive";
            case "jpg", "jpeg", "png", "gif", "webp" -> "image";
            default -> "other";
        };
    }
}
