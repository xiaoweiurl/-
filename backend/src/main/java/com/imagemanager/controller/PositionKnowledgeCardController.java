package com.imagemanager.controller;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.entity.PositionKnowledgeCard;
import com.imagemanager.exception.AuthException;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.PositionKnowledgeCardService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 岗位知识卡片控制器
 * 按岗位知识卡片模板V1.0设计，支持8大模块的完整CRUD
 */
@Slf4j
@RestController
@RequestMapping("/knowledge/cards")
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
@RequiredArgsConstructor
public class PositionKnowledgeCardController {

    private final PositionKnowledgeCardService cardService;
    private final AuthService authService;

    private LoginResponse.UserInfo getCurrentUser(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginResponse.UserInfo) {
            return (LoginResponse.UserInfo) auth.getPrincipal();
        }
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

    /**
     * 创建岗位知识卡片
     */
    @PostMapping
    public ResponseEntity<?> createCard(@RequestBody PositionKnowledgeCard card, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = resolveUserId(user);
        String company = resolveCompany(user);
        try {
            log.info("创建知识卡片: userId={}, company={}, positionName={}", userId, company, card.getPositionName());
            PositionKnowledgeCard created = cardService.createCard(card, userId, company);
            return ResponseEntity.ok(Map.of("success", true, "card", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("创建知识卡片失败", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "创建失败: " + e.getMessage()));
        }
    }

    /**
     * 更新岗位知识卡片
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCard(@PathVariable String id, @RequestBody PositionKnowledgeCard card, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = resolveUserId(user);
        String company = resolveCompany(user);
        try {
            PositionKnowledgeCard updated = cardService.updateCard(id, card, userId, company);
            return ResponseEntity.ok(Map.of("success", true, "card", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("更新知识卡片失败", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "更新失败: " + e.getMessage()));
        }
    }

    /**
     * 获取卡片详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getCardDetail(@PathVariable String id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String company = resolveCompany(user);
        try {
            PositionKnowledgeCard card = cardService.getCardDetail(id, company);
            return ResponseEntity.ok(Map.of("success", true, "card", card));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("获取知识卡片详情失败", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 获取卡片列表
     */
    @GetMapping
    public ResponseEntity<?> getCards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String department,
            HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = resolveUserId(user);
        String company = resolveCompany(user);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        try {
            Page<PositionKnowledgeCard> cards = cardService.getCards(company, userId, keyword, department, pageable);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "cards", cards.getContent(),
                    "total", cards.getTotalElements(),
                    "page", cards.getNumber(),
                    "size", cards.getSize()
            ));
        } catch (Exception e) {
            log.error("获取知识卡片列表失败", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "查询失败: " + e.getMessage()));
        }
    }

    /**
     * 删除卡片
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCard(@PathVariable String id, HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String userId = resolveUserId(user);
        String company = resolveCompany(user);
        try {
            log.info("删除知识卡片: id={}, userId={}, company={}", id, userId, company);
            cardService.deleteCard(id, company, userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "删除成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        } catch (Exception e) {
            log.error("删除知识卡片失败", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "删除失败: " + e.getMessage()));
        }
    }

    /**
     * 生成卡片编号
     */
    @GetMapping("/next-code")
    public ResponseEntity<?> getNextCode(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String company = resolveCompany(user);
        try {
            String code = cardService.generateCardCode(company);
            return ResponseEntity.ok(Map.of("success", true, "code", code));
        } catch (Exception e) {
            log.error("生成卡片编号失败", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "生成编号失败: " + e.getMessage()));
        }
    }

    /**
     * 获取统计
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(HttpServletRequest request) {
        LoginResponse.UserInfo user = getCurrentUser(request);
        String company = resolveCompany(user);
        try {
            long count = cardService.countCards(company);
            return ResponseEntity.ok(Map.of("success", true, "count", count));
        } catch (Exception e) {
            log.error("获取知识卡片统计失败", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "error", "统计失败: " + e.getMessage()));
        }
    }
}
