package com.imagemanager.config;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.AuthService;
import com.imagemanager.util.SessionIdExtractor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 自定义认证过滤器。
 * 会话解析与 BFF 一致：session_id Cookie 优先于 X-Session-Id（见 {@link SessionIdExtractor}）。
 *
 * @author Image Manager Team
 * @version 1.0.0
 */
@Component
@Order(1) // 在 Spring Security 过滤器之前执行
public class SessionIdAuthFilter extends OncePerRequestFilter {

    @Autowired
    @Lazy
    private AuthService authService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // 公开端点不需要认证
        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String sessionId = SessionIdExtractor.extract(request);
        
        if (sessionId != null) {
            // 验证 session（从 Redis 读取）
            LoginResponse.UserInfo userInfo = authService.validateSession(sessionId);
            
            if (userInfo != null && userInfo.getUsername() != null) {
                // 将用户信息存储到 request 属性中，供后续使用
                request.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, userInfo);
                
                // 将认证信息设置到 Spring Security 的 SecurityContext
                // admin 与 superadmin 均映射 ROLE_ADMIN（超级管理员拥有全部管理权限）
                String role = userInfo.getRole();
                boolean isAdmin = role != null
                        && ("ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role));
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority(
                    isAdmin ? "ROLE_ADMIN" : "ROLE_USER"
                );
                
                UsernamePasswordAuthenticationToken authentication = 
                    new UsernamePasswordAuthenticationToken(
                        userInfo.getId(),
                        null,
                        Collections.singletonList(authority)
                    );
                
                SecurityContextHolder.getContext().setAuthentication(authentication);
                
                filterChain.doFilter(request, response);
                return;
            }
        }
        
        // 没有有效的 session，继续执行（让 Spring Security 处理）
        filterChain.doFilter(request, response);
    }
    
    /**
     * 判断是否为公开端点
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/auth/login") ||
               path.startsWith("/auth/register") ||
               path.startsWith("/auth/session") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/health") ||
               path.equals("/actuator/health");
    }
}
