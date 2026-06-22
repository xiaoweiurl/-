package com.imagemanager.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 市场营销AI对话服务
 * 针对无缝针织行业，使用MiniMax chatcompletion_v2联网对话接口
 */
public interface MarketingChatService {

    /**
     * 市场营销AI对话 (SSE流式)
     */
    SseEmitter chat(String message, String userId, String company);

    /**
     * 获取对话历史（最近10轮）
     */
    List<Map<String, Object>> getChatHistory(String userId, String company);

    /**
     * 清空对话历史
     */
    void clearChatHistory(String userId, String company);
}
