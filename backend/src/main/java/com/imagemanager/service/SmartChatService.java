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
     * @param message   用户消息
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return SSE流式发射器
     */
    SseEmitter smartChat(String message, String sessionId, String userId);

    /**
     * 获取对话历史
     */
    List<Map<String, Object>> getChatHistory(String sessionId, String userId);

    /**
     * 清空对话历史
     */
    void clearChatHistory(String sessionId, String userId);
}
