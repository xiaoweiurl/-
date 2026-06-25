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
 * 支持多对话管理，对话历史按 conversationId 隔离
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

    private String resolveUserId(LoginResponse.UserInfo user) {
        return user.getId() != null ? user.getId() : user.getUsername();
    }

    private String resolveCompany(LoginResponse.UserInfo user) {
        return user.getCompany() != null ? user.getCompany() : "盈云";
    }

    // ====== 智能对话 ======

    /**
     * 智能对话 (SSE流式, 双库检索)
     */
    @GetMapping(value = "/smart", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter smartChat(
            @RequestParam String message,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String mode,
            HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            String userId = resolveUserId(user);
            String company = resolveCompany(user);
            return smartChatService.smartChat(message, userId, company, conversationId, mode);
        } catch (Exception e) {
            SseEmitter emitter = new SseEmitter(60000L);
            try {
                emitter.send(SseEmitter.event().data("{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }
    }

    // ====== 对话管理 ======

    /**
     * 创建新对话
     */
    @PostMapping("/conversations")
    public ResponseEntity<?> createConversation(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            String userId = resolveUserId(user);
            String company = resolveCompany(user);
            String title = (body != null) ? body.get("title") : null;
            String mode = (body != null) ? body.get("mode") : null;
            Map<String, Object> conv = smartChatService.createConversation(userId, company, title, mode);
            return ResponseEntity.ok(Map.of("success", true, "conversation", conv));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 获取对话列表（按mode筛选）
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(
            @RequestParam(required = false) String mode,
            HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            String userId = resolveUserId(user);
            String company = resolveCompany(user);
            List<Map<String, Object>> conversations = smartChatService.getConversations(userId, company, mode);
            return ResponseEntity.ok(Map.of("success", true, "conversations", conversations));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 更新对话标题
     */
    @PutMapping("/conversations/{id}")
    public ResponseEntity<?> updateConversation(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            String title = body.get("title");
            smartChatService.updateConversationTitle(id, title);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<?> deleteConversation(
            @PathVariable String id,
            HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            String userId = resolveUserId(user);
            String company = resolveCompany(user);
            smartChatService.deleteConversation(id, userId, company);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ====== 对话历史 ======

    /**
     * 获取对话历史（按conversationId或userId+company）
     */
    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String mode,
            HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            String userId = resolveUserId(user);
            String company = resolveCompany(user);
            List<Map<String, Object>> history = smartChatService.getChatHistory(userId, company, conversationId, mode);
            return ResponseEntity.ok(Map.of("success", true, "history", history));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 清空对话历史（按conversationId或userId+company+mode）
     */
    @DeleteMapping("/history")
    public ResponseEntity<?> clearChatHistory(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String mode,
            HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            String userId = resolveUserId(user);
            String company = resolveCompany(user);
            smartChatService.clearChatHistory(userId, company, conversationId, mode);
            return ResponseEntity.ok(Map.of("success", true, "message", "对话历史已清空"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
