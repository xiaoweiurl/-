package com.imagemanager.service;

import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.entity.KnowledgeCard;
import com.imagemanager.entity.KnowledgeDomain;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface MemoryService {

    /** 获取所有知识域 */
    List<KnowledgeDomain> getAllDomains();

    /** 获取知识域详情 */
    KnowledgeDomain getDomainByCode(String code);

    /** 创建知识卡片(自动向量化) - 绑定用户 */
    KnowledgeCard createCard(KnowledgeCard card, String userId);

    /** 获取知识域下的卡片列表(用户隔离) */
    List<KnowledgeCard> getCardsByDomain(String domainCode, String userId);

    /** 删除知识卡片(含向量) - 只能删自己的 */
    void deleteCard(String cardId, String userId);

    /** 语义检索(用户隔离) */
    List<MemorySearchResult> search(String query, String domainCode, double minScore, int limit, String userId);

    /** AI对话(SSE流式, 用户隔离) */
    SseEmitter chat(String message, String sessionId, String userId);
}
