package com.imagemanager.config;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 自定义认证过滤器
 * 处理 X-Session-Id 请求头进行认证
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@Component
@Order(1) // 在 Spring Security 过滤器之前执行
public class SessionIdAuthFilter extends OncePerRequestFilter {

    @Autowired
    @Lazy
    private AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // 公开端点不需要认证
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 调试日志
        log.info("=== SessionIdAuthFilter ===");
        log.info("请求路径: {}", path);
        log.info("X-Session-Id header: {}", request.getHeader("X-Session-Id"));
        
        // 尝试从多个来源获取 sessionId
        String sessionId = extractSessionId(request);
        
        if (sessionId != null) {
            log.info("提取到的 sessionId: {}", sessionId.length() > 8 ? sessionId.substring(0, 8) + "..." : sessionId);
            
            // 验证 session
            LoginResponse.UserInfo userInfo = authService.validateSession(sessionId);
            
            if (userInfo != null && userInfo.getUsername() != null) {
                log.info("Session 验证成功，用户: {}", userInfo.getUsername());
                
                // 将用户信息存储到 request 属性中，供后续使用
                request.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, userInfo);
                
                // ===== 关键修复：将认证信息设置到 Spring Security 的 SecurityContext =====
                // 创建权限列表
                String role = userInfo.getRole();
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                    role != null && "ADMIN".equalsIgnoreCase(role) ? "ROLE_ADMIN" : "ROLE_USER"
                );
                
                // 创建 Authentication 对象
                // 重要：principal 使用 userInfo.getId()（真正的用户ID，如"user-1"），而不是 getUsername()（用户名）
                // 这样 auth.getName() 会返回用户ID，用于通知创建等场景的外键约束
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        userInfo.getId(),         // principal = 用户ID（如"user-1"）
                        null,                     // credentials
                        Collections.singletonList(authority)  // authorities
                    );
                
                // 将 Authentication 设置到 SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                log.info("已设置 Spring Security 认证上下文");
                
                // 继续执行过滤链
                filterChain.doFilter(request, response);
                return;
            } else {
                log.warn("Session 验证失败");
            }
        } else {
            log.warn("无法提取 sessionId");
        }
        
        // 没有有效的 session，继续执行（让 Spring Security 处理）
        filterChain.doFilter(request, response);
    }
    
    /**
     * 提取 Session ID
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
        
        return null;
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
