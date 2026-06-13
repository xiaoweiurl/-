package com.imagemanager.controller;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.exception.AuthException;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.SmartChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智能对话控制器 - 双库检索(知识库+记忆库) + MiniMax流式对话
 * 前端 Next.js /chat 页面专用
 */
@RestController
@RequestMapping("/chat")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class ChatController {

    @Autowired
    private SmartChatService smartChatService;

    @Autowired
    private AuthService authService;

    // ====== 认证辅助方法 ======

    private LoginResponse.UserInfo getCurrentUser(HttpServletRequest request) {
        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId == null && request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("session_id".equals(cookie.getName())) {
                    sessionId = cookie.getValue();
                    break;
                }
            }
        }
        if (sessionId == null) {
            throw new AuthException("未登录");
        }
        LoginResponse.UserInfo user = authService.validateSession(sessionId);
        if (user == null) {
            throw new AuthException("会话已过期");
        }
        return user;
    }

    // ====== 智能对话 ======

    /**
     * 智能对话 (SSE流式, 双库检索)
     * 同时检索知识库(Coze SDK)和记忆库(PostgreSQL向量), 合并结果后调MiniMax
     */
    @GetMapping(value = "/smart", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter smartChat(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();
        String chatSessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        return smartChatService.smartChat(message, chatSessionId, userId);
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory(
            @RequestParam String sessionId,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        List<Map<String, Object>> history = smartChatService.getChatHistory(sessionId, userId);
        return ResponseEntity.ok(Map.of("success", true, "history", history));
    }

    /**
     * 清空对话历史
     */
    @DeleteMapping("/history")
    public ResponseEntity<?> clearChatHistory(
            @RequestParam String sessionId,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        smartChatService.clearChatHistory(sessionId, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "对话历史已清空"));
    }
}
