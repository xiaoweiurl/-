package com.imagemanager.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/auth/login",
                "/auth/session",
                "/api/share/access/**",  // 分享链接公开访问
                "/api-docs/**",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/health",
                "/actuator/health",
                "/uploads/**",  // 静态资源不需要认证
                "/api/uploads/**"  // API 形式访问静态资源
            );
    }
    
    /**
     * 配置静态资源映射
     * 将 /uploads/** 路径映射到本地 ./uploads 目录
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 获取当前工作目录
        String userDir = System.getProperty("user.dir");
        String localPath = userDir + "/uploads";
        
        // 添加 uploads 路径映射
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + localPath + "/");
        
        System.out.println("静态资源映射: /uploads/** -> file:" + localPath);
        System.out.println("工作目录: " + userDir);
    }
}
