package com.imagemanager.service;

import com.imagemanager.entity.KnowledgeBaseCategory;
import com.imagemanager.entity.KnowledgeBaseDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface KnowledgeBaseService {
    // 文档管理
    KnowledgeBaseDoc uploadDocument(MultipartFile file, String title, UUID categoryId, List<String> tags, String userId);
    Page<KnowledgeBaseDoc> getDocuments(String userId, Pageable pageable);
    List<KnowledgeBaseDoc> getDocumentsByCategory(String userId, UUID categoryId);
    void deleteDocument(UUID id, String userId);
    KnowledgeBaseDoc getDocumentDetail(UUID id, String userId);

    // 分类管理
    KnowledgeBaseCategory createCategory(String name, String description, UUID parentId, String userId);
    List<KnowledgeBaseCategory> getCategories(String userId);
    void deleteCategory(UUID id, String userId);

    // 统计
    long getDocumentCount(String userId);
}
