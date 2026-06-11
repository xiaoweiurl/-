package com.imagemanager.service;

import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.entity.KnowledgeCard;
import com.imagemanager.entity.KnowledgeDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

public interface MemoryService {

    /** 获取所有知识域(含当前用户的卡片计数) */
    List<KnowledgeDomain> getAllDomains(String userId);

    /** 获取知识域详情 */
    KnowledgeDomain getDomainByCode(String code);

    /** 创建知识卡片(自动向量化) - 绑定用户 */
    KnowledgeCard createCard(String domainCode, String title, String content,
                             String[] tags, String productCode, String source,
                             String confidence, String createdBy, String userId);

    /** 获取知识域下的卡片列表(用户隔离) */
    Page<KnowledgeCard> getCardsByDomain(String domainCode, String userId, Pageable pageable);

    /** 获取所有已发布卡片(用户隔离) */
    Page<KnowledgeCard> getAllPublishedCards(String userId, Pageable pageable);

    /** 删除知识卡片(含向量) - 只能删自己的 */
    void deleteCard(UUID cardId, String userId);

    /** 语义检索(用户隔离) */
    List<MemorySearchResult> search(String query, String domainCode, double minScore, int limit, String userId);

    /** AI对话(SSE流式, 用户隔离) */
    SseEmitter chat(String message, UUID sessionId, String domainCode, String userId);
}
