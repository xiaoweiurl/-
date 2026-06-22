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

/**
 * 智能对话控制器 - 双库检索(知识库+记忆库) + MiniMax流式对话
 * 前端 Next.js /chat 页面专用
 * 对话历史按 userId+company 绑定，不依赖 sessionId
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
     * 同时检索知识库和记忆库, 合并结果后调MiniMax
     */
    @GetMapping(value = "/smart", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter smartChat(
            @RequestParam String message,
            HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            String userId = user.getId() != null ? user.getId() : user.getUsername();
            String company = user.getCompany() != null ? user.getCompany() : "盈云";
            return smartChatService.smartChat(message, userId, company);
        } catch (Exception e) {
            SseEmitter emitter = new SseEmitter(60000L);
            try {
                emitter.send(SseEmitter.event().data("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }
    }

    /**
     * 获取对话历史（按userId+company，最近10轮）
     */
    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();
        String company = user.getCompany() != null ? user.getCompany() : "盈云";
        List<Map<String, Object>> history = smartChatService.getChatHistory(userId, company);
        return ResponseEntity.ok(Map.of("success", true, "history", history));
    }

    /**
     * 清空对话历史（按userId+company）
     */
    @DeleteMapping("/history")
    public ResponseEntity<?> clearChatHistory(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();
        String company = user.getCompany() != null ? user.getCompany() : "盈云";
        smartChatService.clearChatHistory(userId, company);
        return ResponseEntity.ok(Map.of("success", true, "message", "对话历史已清空"));
    }
}
