package com.imagemanager.controller;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.entity.KnowledgeBaseCategory;
import com.imagemanager.entity.KnowledgeBaseDoc;
import com.imagemanager.exception.AuthException;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.KnowledgeBaseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库控制器 - 独立的知识文档管理系统
 * 与记忆库(Memory)完全分离，各自独立的表和存储
 */
@RestController
@RequestMapping("/knowledge")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final AuthService authService;

    // ====== 认证辅助方法 ======

    /**
     * 从 SecurityContext 获取当前用户（SessionIdAuthFilter 已验证并设置）
     */
    private LoginResponse.UserInfo getCurrentUser(HttpServletRequest request) {
        // 优先从 SecurityContext 获取（SessionIdAuthFilter 已验证）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginResponse.UserInfo) {
            return (LoginResponse.UserInfo) auth.getPrincipal();
        }
        // 降级：手动从 session 验证
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

    // ====== 文档上传 ======

    /**
     * 上传文件到知识库
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "tags", required = false) List<String> tags,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        UUID catId = null;
        if (categoryId != null && !categoryId.isEmpty()) {
            catId = UUID.fromString(categoryId);
        }

        KnowledgeBaseDoc doc = knowledgeBaseService.uploadDocument(file, title, catId, tags, userId, user.getCompany());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "doc", doc,
                "message", "上传成功"
        ));
    }

    // ====== 文档列表 ======

    /**
     * 获取知识库文档列表
     */
    @GetMapping("/docs")
    public ResponseEntity<?> getDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        Pageable pageable = PageRequest.of(page, size);
        String company = user.getCompany();
        Page<KnowledgeBaseDoc> docs;
        if (keyword != null && !keyword.isEmpty()) {
            docs = knowledgeBaseService.searchDocuments(company, userId, keyword, pageable);
        } else if (categoryId != null && !categoryId.isEmpty()) {
            List<KnowledgeBaseDoc> list = knowledgeBaseService.getDocumentsByCategory(company, userId, UUID.fromString(categoryId));
            docs = new org.springframework.data.domain.PageImpl<>(list);
        } else {
            docs = knowledgeBaseService.getDocuments(company, userId, pageable);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "documents", docs.getContent(),
                "total", docs.getTotalElements(),
                "page", docs.getNumber(),
                "size", docs.getSize()
        ));
    }

    /**
     * 获取文档详情
     */
    @GetMapping("/docs/{id}")
    public ResponseEntity<?> getDocumentDetail(@PathVariable UUID id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        KnowledgeBaseDoc doc = knowledgeBaseService.getDocumentDetail(id, user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "doc", doc));
    }

    // ====== 文档删除 ======

    /**
     * 删除知识库文档
     */
    @DeleteMapping("/docs/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable UUID id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        knowledgeBaseService.deleteDocument(id, user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }

    // ====== 分类管理 ======

    /**
     * 创建分类
     */
    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        String name = (String) body.get("name");
        String description = (String) body.get("description");
        String parentId = (String) body.get("parentId");

        UUID parentUUID = null;
        if (parentId != null && !parentId.isEmpty()) {
            parentUUID = UUID.fromString(parentId);
        }

        KnowledgeBaseCategory category = knowledgeBaseService.createCategory(name, description, parentUUID, user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "category", category));
    }

    /**
     * 获取分类列表
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        List<KnowledgeBaseCategory> categories = knowledgeBaseService.getCategories(user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "categories", categories));
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable UUID id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        knowledgeBaseService.deleteCategory(id, user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }

    // ====== 文档下载 ======

    /**
     * 获取文档下载链接
     */
    @GetMapping("/docs/{id}/download")
    public ResponseEntity<?> downloadDocument(@PathVariable UUID id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        try {
            var doc = knowledgeBaseService.getDocumentById(id, user.getCompany(), userId);
            if (doc.getFilePath() != null) {
                String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
                String downloadUrl = baseUrl + "/api/knowledge/docs/" + doc.getId() + "/file";
                String downloadName = doc.getFileName() != null ? doc.getFileName() : doc.getTitle();
                return ResponseEntity.ok(Map.of("success", true, "url", downloadUrl,
                        "fileName", downloadName));
            } else {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "文件不可下载"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ====== 统计 ======

    /**
     * 获取文档统计
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        long count = knowledgeBaseService.getDocumentCount(user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "count", count));
    }

    // ====== 向量搜索 ======

    /**
     * 知识库向量搜索
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "0.25") double minScore,
            @RequestParam(defaultValue = "5") int limit,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        var results = knowledgeBaseService.search(query, minScore, limit, user.getCompany(), userId);
        return ResponseEntity.ok(Map.of("success", true, "results", results));
    }

    /**
     * 重新向量化失败文档
     */
    @PostMapping("/docs/{id}/reembed")
    public ResponseEntity<?> reembedDoc(@PathVariable String id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();
        try {
            knowledgeBaseService.retryEmbedding(id, user.getCompany(), userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "已触发重新向量化"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
