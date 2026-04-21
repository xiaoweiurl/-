package com.imagemanager.config;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器
 * 验证请求中的 Session
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    public static final String USER_INFO_ATTRIBUTE = "userInfo";
    
    @Autowired
    private AuthService authService;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 公开端点不需要认证
        String path = request.getRequestURI();
        if (isPublicEndpoint(path)) {
            return true;
        }
        
        // ===== 优先检查 Spring Security 的 SecurityContext =====
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() != null) {
            log.info("=== AuthInterceptor ===");
            log.info("请求路径: {}", path);
            log.info("SecurityContext 已有认证: {}", authentication.getPrincipal());
            // 如果 SecurityContext 已有认证信息，不再做额外的 session 验证
            return true;
        }
        
        // 调试：打印所有请求头
        log.info("=== AuthInterceptor ===");
        log.info("请求路径: {}", path);
        log.info("SecurityContext 无认证信息，继续验证...");
        log.info("X-Session-Id header: {}", request.getHeader("X-Session-Id"));
        log.info("Cookie header: {}", request.getHeader("Cookie"));
        
        // 尝试从多个来源获取 sessionId
        String sessionId = extractSessionId(request);
        
        // 记录请求的 cookies 用于调试
        StringBuilder cookieDebug = new StringBuilder();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                String valuePreview = c.getValue().length() > 8 ? c.getValue().substring(0, 8) + "..." : c.getValue();
                cookieDebug.append(c.getName()).append("=").append(valuePreview).append("; ");
            }
        } else {
            cookieDebug.append("null");
        }
        log.info("解析到的 cookies: {}", cookieDebug.toString());
        
        // 尝试验证 session
        LoginResponse.UserInfo userInfo = null;
        if (sessionId != null) {
            log.info("提取到的 sessionId: {}", sessionId.substring(0, Math.min(8, sessionId.length())));
            userInfo = authService.validateSession(sessionId);
            if (userInfo != null) {
                log.info("Session 验证成功，用户: {}", userInfo.getUsername());
            } else {
                log.warn("Session 验证失败，sessionId: {}", sessionId.substring(0, Math.min(8, sessionId.length())));
            }
        } else {
            log.warn("无法提取 sessionId");
        }
        
        if (userInfo == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"请先登录\"}");
            return false;
        }
        
        // 将用户信息存储到请求属性中
        request.setAttribute(USER_INFO_ATTRIBUTE, userInfo);
        
        // 检查管理员权限 - 支持大小写不敏感比较
        boolean isAdmin = "ADMIN".equalsIgnoreCase(userInfo.getRole());
        if (path.startsWith("/admin/") && !isAdmin) {
            log.warn("用户 {} (角色: {}) 试图访问管理员端点: {}", userInfo.getUsername(), userInfo.getRole(), path);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"您没有权限执行此操作 (需要 ADMIN 权限，当前: " + userInfo.getRole() + ")\"}");
            return false;
        }
        
        log.debug("用户 {} 通过认证: {}", userInfo.getUsername(), path);
        return true;
    }
    
    /**
     * 提取 Session ID
     * 支持多种方式：session_id cookie, X-Session-Id header, Authorization header
     */
    private String extractSessionId(HttpServletRequest request) {
        // 1. 优先从 X-Session-Id 请求头获取（前端主要方式）
        String xSessionId = request.getHeader("X-Session-Id");
        if (xSessionId != null && !xSessionId.isEmpty()) {
            log.debug("从 X-Session-Id header 获取 sessionId: {}", xSessionId.substring(0, Math.min(8, xSessionId.length())));
            return xSessionId;
        }
        
        // 2. 从 Cookie 中获取
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("session_id".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        // 3. 从 Header 中的 Cookie 字符串获取
        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader != null) {
            for (String part : cookieHeader.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith("session_id=")) {
                    return trimmed.substring("session_id=".length());
                }
            }
        }
        
        // 4. 从 Authorization header 获取（Authorization: Bearer xxx）
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        // 5. 最后从参数中获取
        return request.getParameter("session_id");
    }
    
    /**
     * 判断是否为公开端点
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/auth/login") ||
               path.startsWith("/auth/session") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/health") ||
               path.equals("/actuator/health");
    }
}
