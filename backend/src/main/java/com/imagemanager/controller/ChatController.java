package com.imagemanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.config.SamplerSessionGuard;
import com.imagemanager.config.SessionAuthorities;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.eval.RagEvalService;
import com.imagemanager.exception.AuthException;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.ChatFeedbackService;
import com.imagemanager.service.SmartChatService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * 智能对话控制器 - 双库检索(知识库+记忆库) + Ollama流式对话
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
    private ChatFeedbackService chatFeedbackService;

    @Autowired
    private RagEvalService ragEvalService;

    @Autowired
    private ObjectMapper objectMapper;

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
        return user.getCompany() != null ? user.getCompany() : "宝娜斯集团";
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
            @RequestParam(required = false) String subMode,
            HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            String userId = resolveUserId(user);
            String company = resolveCompany(user);
            return smartChatService.smartChat(message, userId, company, conversationId, mode, subMode);
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
     * 智能对话 (SSE流式) - POST方式
     * 支持传入图片base64（多模态）和PDF文档
     */
    @PostMapping(value = "/smart", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter smartChatPost(
            @RequestParam(required = false) String mode,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String message = body.get("message") != null ? body.get("message").toString() : null;
        String conversationId = body.get("conversationId") != null ? body.get("conversationId").toString() : null;
        String subMode = body.get("subMode") != null ? body.get("subMode").toString() : null;
        @SuppressWarnings("unchecked")
        List<String> images = body.get("images") != null ? (List<String>) body.get("images") : null;
        @SuppressWarnings("unchecked")
        List<Map<String, String>> pdfs = body.get("pdfs") != null ? (List<Map<String, String>>) body.get("pdfs") : null;

        if (message == null || message.isBlank()) {
            SseEmitter emitter = new SseEmitter(60000L);
            try {
                emitter.send(SseEmitter.event().data("{\"error\":\"消息不能为空\"}"));
                emitter.complete();
            } catch (Exception ignored) {}
            return emitter;
        }
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            String userId = resolveUserId(user);
            String company = resolveCompany(user);
            return smartChatService.smartChatWithAttachments(message, userId, company, conversationId, mode, images, pdfs, subMode);
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
            getCurrentUser(request);
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

    /**
     * 回答反馈。普通用户可以提交自己的反馈；打样作用域会话不能访问。
     */
    @PostMapping("/feedback")
    public ResponseEntity<?> feedback(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            ResponseEntity<?> denied = denySampler(user);
            if (denied != null) {
                return denied;
            }
            Map<String, Object> saved = chatFeedbackService.record(
                    resolveUserId(user),
                    resolveCompany(user),
                    text(body.get("conversationId")),
                    text(body.get("question")),
                    text(body.get("answer")),
                    sourcesJson(body.get("sources")),
                    text(body.get("comment")),
                    text(body.get("verdict"))
            );
            return ResponseEntity.ok(Map.of("success", true, "feedback", saved));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage() == null ? "反馈失败" : e.getMessage()));
        }
    }

    /**
     * 待补充知识。仅管理员，且只看本公司。
     */
    @GetMapping("/knowledge-gaps")
    public ResponseEntity<?> knowledgeGaps(HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            ResponseEntity<?> denied = requireFeedbackAdmin(user);
            if (denied != null) {
                return denied;
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "items", chatFeedbackService.listPending(resolveCompany(user))
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage() == null ? "加载失败" : e.getMessage()));
        }
    }

    /**
     * 把答错的问题导出成评测 jsonl。仅管理员，按公司隔离。
     */
    @GetMapping(value = "/knowledge-gaps/export", produces = "application/x-ndjson")
    public ResponseEntity<?> exportKnowledgeGaps(HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            ResponseEntity<?> denied = requireFeedbackAdmin(user);
            if (denied != null) {
                return denied;
            }
            String jsonl = chatFeedbackService.exportWrongAsJsonl(resolveCompany(user));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"knowledge-gaps-eval.jsonl\"")
                    .contentType(MediaType.parseMediaType("application/x-ndjson;charset=UTF-8"))
                    .body(jsonl);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage() == null ? "导出失败" : e.getMessage()));
        }
    }

    /**
     * 用示例题或服务器上的 jsonl 跑真实检索评测。仅管理员。
     */
    @PostMapping("/rag-eval")
    public ResponseEntity<?> ragEval(HttpServletRequest request) {
        try {
            LoginResponse.UserInfo user = getCurrentUser(request);
            ResponseEntity<?> denied = requireFeedbackAdmin(user);
            if (denied != null) {
                return denied;
            }
            var summary = ragEvalService.runDefault();
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "markdown", ragEvalService.toMarkdown(summary),
                    "report", objectMapper.readValue(ragEvalService.toJson(summary), Map.class)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage() == null ? "评测失败" : e.getMessage()));
        }
    }

    private ResponseEntity<?> denySampler(LoginResponse.UserInfo user) {
        if (SamplerSessionGuard.isSamplerScope(user)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "打样会话仅能填写指定商品表单"));
        }
        return null;
    }

    private ResponseEntity<?> requireFeedbackAdmin(LoginResponse.UserInfo user) {
        ResponseEntity<?> denied = denySampler(user);
        if (denied != null) {
            return denied;
        }
        if (!SessionAuthorities.isAdminRole(user.getRole())) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "您没有权限执行此操作"));
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String sourcesJson(Object sources) {
        if (sources == null) {
            return null;
        }
        if (sources instanceof String s) {
            return s.isBlank() ? null : s;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            return null;
        }
    }
}
