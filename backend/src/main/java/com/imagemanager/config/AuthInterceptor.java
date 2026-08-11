package com.imagemanager.config;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
        
        // 优先检查 Spring Security 的 SecurityContext（SessionIdAuthFilter 已设置）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && authentication.getPrincipal() != null) {
            return true;
        }
        
        // SecurityContext 无认证信息，尝试从请求中提取 sessionId 验证
        String sessionId = extractSessionId(request);
        
        LoginResponse.UserInfo userInfo = null;
        if (sessionId != null) {
            userInfo = authService.validateSession(sessionId);
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
        
        // 检查管理员权限
        boolean isAdmin = "ADMIN".equalsIgnoreCase(userInfo.getRole());
        if (path.startsWith("/admin/") && !isAdmin) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"您没有权限执行此操作\"}");
            return false;
        }
        
        return true;
    }
    
    /**
     * 提取 Session ID
     * 支持多种方式：session_id cookie, X-Session-Id header, Authorization header
     */
    private String extractSessionId(HttpServletRequest request) {
        // 1. 优先从 X-Session-Id 请求头获取
        String xSessionId = request.getHeader("X-Session-Id");
        if (xSessionId != null && !xSessionId.isEmpty()) {
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
        
        // 4. 从 Authorization header 获取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        return request.getParameter("session_id");
    }
    
    /**
     * 判断是否为公开端点
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/auth/login") ||
               path.startsWith("/auth/session") ||
               path.startsWith("/auth/forgot-password") ||
               path.startsWith("/auth/register") ||
               path.startsWith("/share/access") ||  // 分享链接公开访问（context-path 已去掉 /api 前缀）
               path.startsWith("/api-docs") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.startsWith("/uploads/") ||  // 公开访问上传的文件
               path.equals("/health") ||
               path.equals("/actuator/health");
    }
}
