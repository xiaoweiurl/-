package com.imagemanager.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
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
     * 配置静态资源映射
     * 将 /uploads/** 路径映射到本地 ./uploads 目录
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // 获取当前工作目录
        String userDir = System.getProperty("user.dir");
        String localPath = userDir + "/uploads";
        
        // 添加 uploads 路径映射
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + localPath + "/");
    }
}
