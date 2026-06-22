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
    // 文档管理 - 按 company 隔离，同一公司共享数据
    KnowledgeBaseDoc uploadDocument(MultipartFile file, String title, UUID categoryId, List<String> tags, String userId, String company);
    KnowledgeBaseDoc createTextDocument(String title, String content, UUID categoryId, String userId, String company);
    Page<KnowledgeBaseDoc> getDocuments(String company, Pageable pageable);
    Page<KnowledgeBaseDoc> searchDocuments(String company, String keyword, Pageable pageable);
    List<KnowledgeBaseDoc> getDocumentsByCategory(String company, UUID categoryId);
    void deleteDocument(UUID id, String company);
    KnowledgeBaseDoc getDocumentDetail(UUID id, String company);

    // 分类管理
    KnowledgeBaseCategory createCategory(String name, String description, UUID parentId, String userId, String company);
    List<KnowledgeBaseCategory> getCategories(String company);
    void deleteCategory(UUID id, String company);

    // 统计
    long getDocumentCount(String company);

    KnowledgeBaseDoc getDocumentById(UUID id, String company);

    // 向量搜索
    List<MemorySearchResult> search(String query, double minScore, int limit, String company);

    // 重新向量化失败文档
    void retryEmbedding(String docId, String company);
}
