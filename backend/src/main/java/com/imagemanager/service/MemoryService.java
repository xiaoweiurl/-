package com.imagemanager.service;

import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.entity.KnowledgeCard;
import com.imagemanager.entity.KnowledgeDomain;
import com.imagemanager.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

public interface MemoryService {

    /** 获取用户所属公司的知识域 */
    List<KnowledgeDomain> getAllDomains(String company, String userId);

    /** 获取知识域详情 */
    KnowledgeDomain getDomainByCode(String code, String company);

    /** 创建知识卡片(自动向量化) - 绑定用户和公司 */
    KnowledgeCard createCard(KnowledgeCard card, String userId, String company);

    /** 获取知识域下的卡片列表(公司+用户隔离) */
    List<KnowledgeCard> getCardsByDomain(String domainCode, String company, String userId);

    /** 按关键字搜索卡片(公司+用户隔离) */
    List<KnowledgeCard> searchCards(String keyword, String company, String userId);

    /** 删除知识卡片(含向量) - 只能删自己的 */
    void deleteCard(String cardId, String company, String userId);

    /** 上传文档(PDF/Word/Excel/TXT) → 解析 → 切片 → 向量化入库 */
    KnowledgeDocument uploadDocument(MultipartFile file, String domainCode, String userId, String company);

    /** 获取用户的文档列表(公司+用户隔离) */
    List<KnowledgeDocument> getDocuments(String company, String userId);

    /** 删除文档及其关联的知识卡片和向量 */
    void deleteDocument(String docId, String company, String userId);

    /** 语义检索(公司+用户隔离) */
    List<MemorySearchResult> search(String query, String domainCode, double minScore, int limit, String company, String userId);

    /** AI对话(SSE流式, 用户隔离, 含上下文) */
    SseEmitter chat(String message, String sessionId, String company, String userId);

    /** 获取对话历史 */
    List<Map<String, Object>> getChatHistory(String sessionId, String company, String userId);

    /** 清空对话历史 */
    void clearChatHistory(String sessionId, String company, String userId);
}
