package com.imagemanager.enhance;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆管理器：基于conversationId隔离多轮对话记忆。
 * 不依赖LangChain4j的ChatMemory（Ollama版本对ChatMemory支持不完善），
 * 自行管理messages列表，在构建Prompt时注入历史对话。
 * 
 * 对应PDF教程中的ChatMemoryManager。
 */
@Component
public class ChatMemoryManager {

    private static final int MAX_MESSAGES = 20; // 每个会话最多保留20条历史消息

    /**
     * 单条消息
     */
    public static class ChatMessage {
        public String role; // "user" or "assistant"
        public String content;
        public long timestamp;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // conversationId -> messages
    private final Map<String, LinkedList<ChatMessage>> memoryMap = new ConcurrentHashMap<>();

    /**
     * 添加用户消息
     */
    public void addUserMessage(String conversationId, String content) {
        addMessage(conversationId, new ChatMessage("user", content));
    }

    /**
     * 添加AI回复消息
     */
    public void addAssistantMessage(String conversationId, String content) {
        addMessage(conversationId, new ChatMessage("assistant", content));
    }

    /**
     * 获取会话历史消息
     */
    public List<ChatMessage> getMessages(String conversationId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedList<ChatMessage> messages = memoryMap.get(conversationId);
        if (messages == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(messages);
    }

    /**
     * 构建历史对话上下文文本（用于注入Prompt）
     * 只保留最近几轮，避免上下文过长
     */
    public String buildContextString(String conversationId) {
        List<ChatMessage> messages = getMessages(conversationId);
        if (messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 历史对话\n");
        for (ChatMessage msg : messages) {
            if ("user".equals(msg.role)) {
                sb.append("用户: ").append(msg.content).append("\n");
            } else {
                sb.append("助手: ").append(msg.content).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 构建Ollama messages数组（用于chat API）
     */
    public List<Map<String, Object>> buildOllamaMessages(String conversationId) {
        List<ChatMessage> messages = getMessages(conversationId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role", msg.role);
            m.put("content", msg.content);
            result.add(m);
        }
        return result;
    }

    /**
     * 清除会话记忆
     */
    public void clear(String conversationId) {
        if (conversationId != null) {
            memoryMap.remove(conversationId);
        }
    }

    /**
     * 获取活跃会话数
     */
    public int getActiveSessionCount() {
        return memoryMap.size();
    }

    /**
     * 清理超时的会话（超过1小时无活动）
     */
    public void cleanupStaleSessions(long maxIdleMs) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, LinkedList<ChatMessage>>> it = memoryMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, LinkedList<ChatMessage>> entry = it.next();
            LinkedList<ChatMessage> msgs = entry.getValue();
            if (msgs.isEmpty() || (now - msgs.getLast().timestamp) > maxIdleMs) {
                it.remove();
            }
        }
    }

    private void addMessage(String conversationId, ChatMessage message) {
        if (conversationId == null || conversationId.isEmpty()) {
            return; // 无会话ID不保存记忆
        }
        memoryMap.computeIfAbsent(conversationId, k -> new LinkedList<>()).add(message);
        // 限制历史消息数量
        LinkedList<ChatMessage> messages = memoryMap.get(conversationId);
        while (messages.size() > MAX_MESSAGES) {
            messages.removeFirst();
        }
    }
}
