package com.imagemanager.controller;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.exception.AuthException;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.MarketingChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 市场营销AI对话控制器
 * 针对无缝针织行业的市场营销专用对话
 * 使用 MiniMax chatcompletion_v2 联网对话接口
 */
@RestController
@RequestMapping("/marketing/chat")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class MarketingChatController {

    @Autowired
    private MarketingChatService marketingChatService;

    @Autowired
    private AuthService authService;

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

    /**
     * 市场营销AI对话 (SSE流式)
     */
    @GetMapping(value = "/smart", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter smartChat(
            @RequestParam String message,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();
        String company = user.getCompany() != null ? user.getCompany() : "盈云";
        return marketingChatService.chat(message, userId, company);
    }

    /**
     * 获取对话历史（按userId+company，最近10轮）
     */
    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();
        List<Map<String, Object>> history = marketingChatService.getChatHistory(userId, user.getCompany());
        return ResponseEntity.ok(Map.of("success", true, "history", history));
    }

    /**
     * 清空对话历史
     */
    @DeleteMapping("/history")
    public ResponseEntity<?> clearChatHistory(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();
        marketingChatService.clearChatHistory(userId, user.getCompany());
        return ResponseEntity.ok(Map.of("success", true, "message", "对话历史已清空"));
    }
}
