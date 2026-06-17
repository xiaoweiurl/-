package com.imagemanager.service;

import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.entity.KnowledgeBaseCategory;
import com.imagemanager.entity.KnowledgeBaseDoc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface KnowledgeBaseService {
    // 文档管理 - company 从当前用户自动获取，用于数据隔离
    KnowledgeBaseDoc uploadDocument(MultipartFile file, String title, UUID categoryId, List<String> tags, String userId, String company);
    Page<KnowledgeBaseDoc> getDocuments(String company, String userId, Pageable pageable);
    Page<KnowledgeBaseDoc> searchDocuments(String company, String userId, String keyword, Pageable pageable);
    List<KnowledgeBaseDoc> getDocumentsByCategory(String company, String userId, UUID categoryId);
    void deleteDocument(UUID id, String company, String userId);
    KnowledgeBaseDoc getDocumentDetail(UUID id, String company, String userId);

    // 分类管理
    KnowledgeBaseCategory createCategory(String name, String description, UUID parentId, String userId, String company);
    List<KnowledgeBaseCategory> getCategories(String company, String userId);
    void deleteCategory(UUID id, String company, String userId);

    // 统计
    long getDocumentCount(String company, String userId);

    KnowledgeBaseDoc getDocumentById(UUID id, String company, String userId);

    // 向量搜索
    List<MemorySearchResult> search(String query, double minScore, int limit, String company, String userId);

    // 重新向量化失败文档
    void retryEmbedding(String docId, String company, String userId);
}
