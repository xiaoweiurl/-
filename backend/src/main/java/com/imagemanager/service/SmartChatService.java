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
     * @param userId    用户ID
     * @param company   用户所属公司
     * @return SSE流式发射器
     */
    SseEmitter smartChat(String message, String userId, String company);

    /**
     * 获取对话历史（按userId+company，最近10轮）
     */
    List<Map<String, Object>> getChatHistory(String userId, String company);

    /**
     * 清空对话历史（按userId+company）
     */
    void clearChatHistory(String userId, String company);
}
