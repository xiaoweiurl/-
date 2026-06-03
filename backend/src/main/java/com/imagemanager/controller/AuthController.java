package com.imagemanager.controller;

import com.imagemanager.dto.*;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@Tag(name = "认证管理", description = "登录、登出、会话管理等")
public class AuthController {
    
    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名密码登录")
    public ApiResponse<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response) {
        log.info("收到登录请求: username={}, rememberMe={}", request.getUsername(), request.getRememberMe());
        
        try {
            LoginResponse loginResponse = authService.login(request);

            String sessionId = loginResponse.getSessionId();
            log.info("登录成功，sessionId: {}", sessionId);

            // 设置 CORS 响应头（关键：允许前端访问）
            response.setHeader("Access-Control-Allow-Origin", "http://localhost:5000");
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Expose-Headers", "X-Session-Id");
            // 将 sessionId 通过响应头返回
            response.setHeader("X-Session-Id", sessionId);

            // 创建登录通知
            if (loginResponse.getUser() != null) {
                userService.notify("user_login", "登录成功",
                    "欢迎回来，" + loginResponse.getUser().getNickname(),
                    loginResponse.getUser().getId());
            }

            return ApiResponse.success("登录成功", loginResponse);
        } catch (Exception e) {
            log.error("登录失败: ", e);
            return ApiResponse.error(401, e.getMessage());
        }
    }
    
    /**
     * 用户登出
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "退出当前登录状态")
    public ApiResponse<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        log.info("用户登出");
        
        // 从请求头获取 session_id
        String sessionId = extractSessionId(request);
        if (sessionId != null) {
            authService.logout(sessionId);
        }
        
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:5000");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        
        return ApiResponse.success("登出成功", null);
    }
    
    /**
     * 验证会话
     */
    @GetMapping("/session")
    @Operation(summary = "验证会话", description = "检查当前会话是否有效")
    public ApiResponse<LoginResponse.UserInfo> validateSession(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        response.setHeader("Access-Control-Allow-Origin", "http://localhost:5000");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        
        String sessionId = extractSessionId(request);
        
        if (sessionId == null) {
            log.warn("验证会话失败：没有 session_id");
            return ApiResponse.error(401, "未登录");
        }
        
        log.info("验证会话，sessionId: {}", sessionId.substring(0, Math.min(8, sessionId.length())));
        LoginResponse.UserInfo user = authService.validateSession(sessionId);
        if (user == null) {
            log.warn("验证会话失败：session 无效");
            return ApiResponse.error(401, "会话已过期");
        }
        
        log.info("验证会话成功：{}", user.getUsername());
        return ApiResponse.success(user);
    }
    
    /**
     * 从请求中提取 session_id
     */
    private String extractSessionId(HttpServletRequest request) {
        // 从 X-Session-Id 头获取（前端传递）
        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId != null && !sessionId.isEmpty()) {
            return sessionId;
        }
        
        // 从 Authorization 头获取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        // 从 Cookie 中获取
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("session_id".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        return null;
    }
}
