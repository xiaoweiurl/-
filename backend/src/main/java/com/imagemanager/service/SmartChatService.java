package com.imagemanager.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 智能对话服务 - 双库检索(知识库+记忆库) + MiniMax流式对话
 */
public interface SmartChatService {

    /**
     * 智能对话 (SSE流式)
     * 同时检索知识库和记忆库, 合并上下文后调MiniMax
     *
     * @param message        用户消息
     * @param userId         用户ID
     * @param company        用户所属公司
     * @param conversationId 对话ID（可选，为null时自动使用当前活跃对话）
     * @return SSE流式发射器
     */
    SseEmitter smartChat(String message, String userId, String company, String conversationId);

    /**
     * 智能对话 (SSE流式) - 兼容旧接口
     */
    default SseEmitter smartChat(String message, String userId, String company) {
        return smartChat(message, userId, company, null);
    }

    /**
     * 获取对话历史（按conversationId）
     */
    List<Map<String, Object>> getChatHistory(String userId, String company, String conversationId);

    /**
     * 获取对话历史（兼容旧接口，使用默认对话）
     */
    default List<Map<String, Object>> getChatHistory(String userId, String company) {
        return getChatHistory(userId, company, null);
    }

    /**
     * 清空对话历史（按conversationId）
     */
    void clearChatHistory(String userId, String company, String conversationId);

    /**
     * 清空对话历史（兼容旧接口，清空所有）
     */
    default void clearChatHistory(String userId, String company) {
        clearChatHistory(userId, company, null);
    }

    // ====== 对话管理 ======

    /**
     * 创建新对话
     */
    Map<String, Object> createConversation(String userId, String company, String title);

    /**
     * 获取对话列表
     */
    List<Map<String, Object>> getConversations(String userId, String company);

    /**
     * 更新对话标题
     */
    void updateConversationTitle(String conversationId, String title);

    /**
     * 删除对话（同时删除消息历史）
     */
    void deleteConversation(String conversationId, String userId, String company);
}
