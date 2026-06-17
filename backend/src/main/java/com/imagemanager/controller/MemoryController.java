package com.imagemanager.controller;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.entity.KnowledgeCard;
import com.imagemanager.entity.KnowledgeDomain;
import com.imagemanager.entity.KnowledgeDocument;
import com.imagemanager.exception.AuthException;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.MemoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 记忆库控制器 - 知识卡片管理 + 文档上传 + 语义检索 + AI问答
 * 每个用户的知识库相互隔离
 */
@RestController
@RequestMapping("/memory")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
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

    // ====== 知识域 ======

    /**
     * 获取所有知识域
     */
    @GetMapping("/domains")
    public ResponseEntity<?> getDomains(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        List<KnowledgeDomain> domains = memoryService.getAllDomains(user.getCompany());
        return ResponseEntity.ok(Map.of("success", true, "domains", domains));
    }

    // ====== 知识卡片 ======

    /**
     * 创建知识卡片
     */
    @PostMapping("/cards")
    public ResponseEntity<?> createCard(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        KnowledgeCard card = new KnowledgeCard();
        card.setId(UUID.randomUUID());
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
        card.setCompany(user.getCompany());

        KnowledgeCard saved = memoryService.createCard(card, userId, user.getCompany());
        return ResponseEntity.ok(Map.of("success", true, "card", saved));
    }

    /**
     * 获取指定知识域的卡片列表
     */
    @GetMapping("/cards")
    public ResponseEntity<?> getCardsByDomain(
            @RequestParam(required = false) String domainCode,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        List<KnowledgeCard> cards;
        if (keyword != null && !keyword.isBlank()) {
            cards = memoryService.searchCards(keyword, user.getCompany(), userId);
        } else {
            cards = memoryService.getCardsByDomain(domainCode, user.getCompany(), userId);
        }
        return ResponseEntity.ok(Map.of("success", true, "cards", cards, "total", cards.size()));
    }

    /**
     * 删除知识卡片
     */
    @DeleteMapping("/cards/{id}")
    public ResponseEntity<?> deleteCard(@PathVariable String id, HttpServletRequest request) {
        LoginResponse.UserInfo userInfo = getCurrentUser(request);
        String userId = userInfo.getId() != null ? userInfo.getId() : userInfo.getUsername();

        memoryService.deleteCard(id, userInfo.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }

    // ====== 文档上传 ======

    /**
     * 上传文档(PDF/Word/Excel/TXT) → 自动解析 → 切片 → 向量化入库
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "domainCode", defaultValue = "product") String domainCode,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "文件不能为空"));
        }

        // 校验文件类型
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "文件名不能为空"));
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!List.of("pdf", "doc", "docx", "xls", "xlsx", "csv", "txt", "text").contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "不支持的文件格式，仅支持 PDF/Word/Excel/TXT"));
        }

        KnowledgeDocument doc = memoryService.uploadDocument(file, domainCode, userId, user.getCompany());
        return ResponseEntity.ok(Map.of("success", true, "document", doc, "message", "文档上传成功，正在后台处理解析和向量化"));
    }

    /**
     * 获取用户的文档列表
     */
    @GetMapping("/documents")
    public ResponseEntity<?> getDocuments(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        List<KnowledgeDocument> docs = memoryService.getDocuments(user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "documents", docs, "total", docs.size()));
    }

    /**
     * 删除文档及其关联的知识卡片和向量
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable String id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        memoryService.deleteDocument(id, user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "文档及关联知识已删除"));
    }

    // ====== 语义检索 ======

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

        List<MemorySearchResult> results = memoryService.search(query, domainCode, minScore, limit, user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "query", query, "results", results, "total", results.size()));
    }

    // ====== AI对话 ======

    /**
     * AI问答 (SSE流式, 含上下文)
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
            @RequestParam String message,
            @RequestParam(required = false) String sessionId,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        String chatSessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        return memoryService.chat(message, chatSessionId, user.getCompany(), userId);
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/chat/history")
    public ResponseEntity<?> getChatHistory(
            @RequestParam String sessionId,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        List<Map<String, Object>> history = memoryService.getChatHistory(sessionId, user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "history", history));
    }

    /**
     * 清空对话历史
     */
    @DeleteMapping("/chat/history")
    public ResponseEntity<?> clearChatHistory(
            @RequestParam String sessionId,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        memoryService.clearChatHistory(sessionId, user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "对话历史已清空"));
    }
}
