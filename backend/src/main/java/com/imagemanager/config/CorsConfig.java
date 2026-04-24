package com.imagemanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 跨域配置
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 允许的域名（开发环境用 *，生产环境应该指定具体域名）
        config.addAllowedOrigin("http://localhost:5000");
        config.addAllowedOrigin("http://localhost:3000");
        config.addAllowedOriginPattern("*");
        
        // 允许的请求方法
        config.addAllowedMethod("*");
        
        // 允许的请求头
        config.addAllowedHeader("*");
        
        // 允许携带凭证（允许 cookies）
        config.setAllowCredentials(true);
        
        // 预检请求的有效期（秒）
        config.setMaxAge(3600L);
        
        // 暴露响应头，允许前端访问
        config.addExposedHeader("X-Session-Id");
        config.addExposedHeader("Set-Cookie");
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}
