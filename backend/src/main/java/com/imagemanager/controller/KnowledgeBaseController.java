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

        KnowledgeBaseDoc doc = knowledgeBaseService.uploadDocument(file, title, catId, tags, userId);
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
    @GetMapping("/documents")
    public ResponseEntity<?> getDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(value = "categoryId", required = false) String categoryId,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        Pageable pageable = PageRequest.of(page, size);
        Page<KnowledgeBaseDoc> docs;
        if (categoryId != null && !categoryId.isEmpty()) {
            List<KnowledgeBaseDoc> list = knowledgeBaseService.getDocumentsByCategory(userId, UUID.fromString(categoryId));
            docs = new org.springframework.data.domain.PageImpl<>(list);
        } else {
            docs = knowledgeBaseService.getDocuments(userId, pageable);
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
    @GetMapping("/documents/{id}")
    public ResponseEntity<?> getDocumentDetail(@PathVariable UUID id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        KnowledgeBaseDoc doc = knowledgeBaseService.getDocumentDetail(id, userId);
        return ResponseEntity.ok(Map.of("success", true, "doc", doc));
    }

    // ====== 文档删除 ======

    /**
     * 删除知识库文档
     */
    @DeleteMapping("/documents/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable UUID id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        knowledgeBaseService.deleteDocument(id, userId);
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

        KnowledgeBaseCategory category = knowledgeBaseService.createCategory(name, description, parentUUID, userId);
        return ResponseEntity.ok(Map.of("success", true, "category", category));
    }

    /**
     * 获取分类列表
     */
    @GetMapping("/categories")
    public ResponseEntity<?> getCategories(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        List<KnowledgeBaseCategory> categories = knowledgeBaseService.getCategories(userId);
        return ResponseEntity.ok(Map.of("success", true, "categories", categories));
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<?> deleteCategory(@PathVariable UUID id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        knowledgeBaseService.deleteCategory(id, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
    }

    // ====== 统计 ======

    /**
     * 获取文档统计
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = user.getId() != null ? user.getId() : user.getUsername();

        long count = knowledgeBaseService.getDocumentCount(userId);
        return ResponseEntity.ok(Map.of("success", true, "count", count));
    }
}
