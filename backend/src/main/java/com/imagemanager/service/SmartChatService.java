package com.imagemanager.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 智能对话服务 - 双库检索(知识库+记忆库) + DeepSeek流式对话
 */
public interface SmartChatService {

    /**
     * 智能对话 (SSE流式)
     * 同时检索知识库和记忆库, 合并上下文后调DeepSeek
     *
     * @param message        用户消息
     * @param userId         用户ID
     * @param company        用户所属公司
     * @param conversationId 对话ID（可选，为null时自动使用当前活跃对话）
     * @param mode           对话模式（designer/factory，为null时默认designer）
     * @return SSE流式发射器
     */
    SseEmitter smartChat(String message, String userId, String company, String conversationId, String mode);

    /**
     * 智能对话 (SSE流式) - 兼容旧接口(无mode)
     */
    default SseEmitter smartChat(String message, String userId, String company, String conversationId) {
        return smartChat(message, userId, company, conversationId, null);
    }

    /**
     * 智能对话 (SSE流式) - 兼容旧接口
     */
    default SseEmitter smartChat(String message, String userId, String company) {
        return smartChat(message, userId, company, null, null);
    }

    /**
     * 获取对话历史（按conversationId和mode）
     */
    List<Map<String, Object>> getChatHistory(String userId, String company, String conversationId, String mode);

    /**
     * 获取对话历史（兼容旧接口，默认designer模式）
     */
    default List<Map<String, Object>> getChatHistory(String userId, String company, String conversationId) {
        return getChatHistory(userId, company, conversationId, null);
    }

    /**
     * 清空对话历史（按conversationId和mode）
     */
    void clearChatHistory(String userId, String company, String conversationId, String mode);

    /**
     * 清空对话历史（兼容旧接口，默认designer模式）
     */
    default void clearChatHistory(String userId, String company, String conversationId) {
        clearChatHistory(userId, company, conversationId, null);
    }

    // ====== 对话管理 ======

    /**
     * 创建新对话
     */
    Map<String, Object> createConversation(String userId, String company, String title, String mode);

    /**
     * 创建新对话（兼容旧接口，默认designer模式）
     */
    default Map<String, Object> createConversation(String userId, String company, String title) {
        return createConversation(userId, company, title, null);
    }

    /**
     * 获取对话列表（按mode筛选）
     */
    List<Map<String, Object>> getConversations(String userId, String company, String mode);

    /**
     * 获取对话列表（兼容旧接口，默认designer模式）
     */
    default List<Map<String, Object>> getConversations(String userId, String company) {
        return getConversations(userId, company, null);
    }

    /**
     * 更新对话标题
     */
    void updateConversationTitle(String conversationId, String title);

    /**
     * 删除对话（同时删除消息历史）
     */
    void deleteConversation(String conversationId, String userId, String company);
}
