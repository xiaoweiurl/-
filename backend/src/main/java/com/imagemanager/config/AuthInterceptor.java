package com.imagemanager.config;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.AuthService;
import com.imagemanager.util.ApplicationPath;
import com.imagemanager.util.SessionIdExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器
 * 验证请求中的 Session
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
    public static final String USER_INFO_ATTRIBUTE = "userInfo";
    
    @Autowired
    private AuthService authService;
    
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        // 公开端点不需要认证
        String path = ApplicationPath.of(request);
        if (isPublicEndpoint(path)) {
            return true;
        }
        
        // 优先检查 Spring Security 的 SecurityContext（SessionIdAuthFilter 已设置）
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        LoginResponse.UserInfo userInfo = (LoginResponse.UserInfo) request.getAttribute(USER_INFO_ATTRIBUTE);

        if (isRealUser(authentication)) {
            if (userInfo == null) {
                String sessionId = SessionIdExtractor.extract(request);
                if (sessionId != null) {
                    userInfo = authService.validateSession(sessionId);
                    if (userInfo != null) {
                        request.setAttribute(USER_INFO_ATTRIBUTE, userInfo);
                    }
                }
            }
            if (userInfo != null && !SamplerSessionGuard.allows(path, request.getMethod(), userInfo)) {
                forbidSampler(response);
                return false;
            }
            if (requiresAdmin(path) && !isAdminUser(userInfo, authentication)) {
                forbidAdmin(response);
                return false;
            }
            return true;
        }
        
        // SecurityContext 无认证信息，尝试从请求中提取 sessionId 验证
        String sessionId = SessionIdExtractor.extract(request);
        
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

        if (!SamplerSessionGuard.allows(path, request.getMethod(), userInfo)) {
            forbidSampler(response);
            return false;
        }
        
        if (requiresAdmin(path) && !SessionAuthorities.isAdminRole(userInfo.getRole())) {
            forbidAdmin(response);
            return false;
        }
        
        return true;
    }

    private static boolean requiresAdmin(String path) {
        return path.startsWith("/admin/") || path.equals("/admin");
    }

    private static boolean isAdminUser(LoginResponse.UserInfo userInfo,
                                       Authentication authentication) {
        if (userInfo != null) {
            return SessionAuthorities.isAdminRole(userInfo.getRole());
        }
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static void forbidAdmin(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"error\":\"您没有权限执行此操作\"}");
    }

    private static void forbidSampler(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"error\":\"打样会话仅能填写指定商品表单\"}");
    }

    private static boolean isRealUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() != null
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !"anonymousUser".equals(authentication.getPrincipal());
    }
    
    /**
     * 判断是否为公开端点（路径不含 context-path /api）
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/auth/login") ||
               path.startsWith("/auth/session") ||
               path.startsWith("/auth/forgot-password") ||
               path.startsWith("/auth/register") ||
               path.startsWith("/auth/dingtalk") ||
               path.startsWith("/share/access") ||  // 分享链接公开访问（context-path 已去掉 /api 前缀）
               path.startsWith("/api-docs") ||
               path.startsWith("/swagger-ui") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/health") ||
               path.equals("/actuator/health");
    }
}
