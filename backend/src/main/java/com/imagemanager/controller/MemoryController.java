package com.imagemanager.controller;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.entity.KnowledgeCard;
import com.imagemanager.entity.KnowledgeDomain;
import com.imagemanager.exception.AuthException;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.MemoryService;
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
 * 记忆库控制器 - 知识卡片管理 + 语义检索 + AI问答
 * 每个用户的知识库相互隔离
 */
@RestController
@RequestMapping("/memory")
@CrossOrigin(origins = "*", allowCredentials = "true")
public class MemoryController {

    @Autowired
    private MemoryService memoryService;

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

    /**
     * 获取所有知识域
     */
    @GetMapping("/domains")
    public ResponseEntity<?> getDomains(HttpServletRequest request) {
        getCurrentUser(request); // 验证登录
        List<KnowledgeDomain> domains = memoryService.getAllDomains();
        return ResponseEntity.ok(Map.of("success", true, "domains", domains));
    }

    /**
     * 创建知识卡片
     */
    @PostMapping("/cards")
    public ResponseEntity<?> createCard(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        KnowledgeCard card = new KnowledgeCard();
        card.setId(UUID.randomUUID().toString());
        card.setDomainCode((String) body.getOrDefault("domainCode", "product"));
        card.setTitle((String) body.get("title"));
        card.setContent((String) body.get("content"));

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) body.get("tags");
        card.setTags(tags != null ? tags.toArray(new String[0]) : new String[0]);

        card.setProductCode((String) body.get("productCode"));
        card.setSource((String) body.get("source"));
        card.setConfidence((String) body.getOrDefault("confidence", "medium"));
        card.setStatus("published");
        card.setReviewStatus("pending");
        card.setCreatedBy(user.getUsername());
        card.setUserId(userId);

        KnowledgeCard saved = memoryService.createCard(card, userId);
        return ResponseEntity.ok(Map.of("success", true, "card", saved));
    }

    /**
     * 获取指定知识域的卡片列表
     */
    @GetMapping("/cards")
    public ResponseEntity<?> getCardsByDomain(
            @RequestParam(required = false) String domainCode,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        List<KnowledgeCard> cards = memoryService.getCardsByDomain(domainCode, userId);
        return ResponseEntity.ok(Map.of("success", true, "cards", cards, "total", cards.size()));
    }

    /**
     * 删除知识卡片
     */
    @DeleteMapping("/cards/{id}")
    public ResponseEntity<?> deleteCard(@PathVariable String id, HttpServletRequest request) {
        LoginResponse.UserInfo userInfo = getCurrentUser(request);
        String userId = userInfo.getId() != null ? userInfo.getId() : userInfo.getUsername();

        memoryService.deleteCard(id, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }

    /**
     * 语义检索知识卡片
     */
    @PostMapping("/search")
    public ResponseEntity<?> search(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        String query = (String) body.get("query");
        String domainCode = (String) body.get("domainCode");
        double minScore = body.containsKey("minScore") ?
                ((Number) body.get("minScore")).doubleValue() : 0.3;
        int limit = body.containsKey("limit") ?
                ((Number) body.get("limit")).intValue() : 10;

        List<MemorySearchResult> results = memoryService.search(query, domainCode, minScore, limit, userId);
        return ResponseEntity.ok(Map.of("success", true, "query", query, "results", results, "total", results.size()));
    }

    /**
     * AI问答 (SSE流式)
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        String chatSessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        return memoryService.chat(message, chatSessionId, userId);
    }
}
