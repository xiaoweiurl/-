package com.imagemanager.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

/**
 * Web MVC 配置
 * 注册拦截器、跨域、静态资源等
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    @Autowired
    private AuthInterceptor authInterceptor;
    
    @Autowired
    private ApiMetricsInterceptor apiMetricsInterceptor;
    
    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        // API 监控拦截器（在 auth 之前，记录所有请求）
        registry.addInterceptor(Objects.requireNonNull(apiMetricsInterceptor))
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/swagger-ui/**", "/v3/api-docs/**", "/actuator/**",
                "/favicon.ico", "/error", "/static/**", "/webjars/**"
            );
        
        registry.addInterceptor(Objects.requireNonNull(authInterceptor))
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/auth/login",
                "/auth/session",
                "/auth/register",
                "/auth/forgot-password/**",
                "/auth/dingtalk",
                "/auth/dingtalk/**",
                "/share/access/**",  // 分享链接公开访问（context-path 已去掉 /api 前缀）
                "/api-docs/**",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/health",
                "/actuator/health"
                // 注意：/uploads/** 不再排除，上传文件访问需登录（浏览器同站 <img> 自动携带 Cookie）
            );
    }
    
    /**
     * /uploads/** 改由 {@link com.imagemanager.controller.UploadsController} 提供：
     * 本地磁盘命中则直接输出；否则按存储 key 回源主存储（S3/OSS）；均未命中返回 404。
     * 不再注册静态资源映射，避免缺文件时 NoResourceFoundException 被全局处理成 500。
     */
}
