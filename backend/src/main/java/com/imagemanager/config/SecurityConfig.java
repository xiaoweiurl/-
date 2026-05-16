package com.imagemanager.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security 配置类 - 完整安全配置
 * 
 * @author Image Manager Team
 * @version 2.0.0
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Autowired
    private SessionIdAuthFilter sessionIdAuthFilter;
    
    /**
     * 密码编码器（BCrypt）
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 自定义认证失败处理 - 返回 JSON 而非重定向
     */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"请先登录\"}");
        };
    }

    /**
     * 自定义权限不足处理 - 返回 JSON 而非重定向
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"权限不足\"}");
        };
    }
    
    /**
     * CORS 配置 - 支持跨域请求和 Cookie 传递
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许所有来源（开发环境）
        // 注意：当 allowCredentials=true 时，不能使用 *，必须使用具体的 origin 或 pattern
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 允许所有请求头
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // 允许 credentials（Cookie）跨域传递 - 这是关键！
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        // 允许暴露的响应头
        configuration.setExposedHeaders(Arrays.asList(
            "Authorization", 
            "Set-Cookie",
            "X-Session-Id",
            "Access-Control-Allow-Credentials",
            "Access-Control-Allow-Origin"
        ));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    /**
     * 安全过滤器链配置
     * - 启用 Session 管理
     * - 配置路径权限
     * - 启用 CSRF（仅对特定端点）
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CORS 配置
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // 添加自定义认证过滤器（在 Spring Security 过滤器之前）
            .addFilterBefore(sessionIdAuthFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            // 禁用 CSRF（API 模式下不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 禁用 HTTP Basic
            .httpBasic(AbstractHttpConfigurer::disable)
            // 禁用表单登录
            .formLogin(AbstractHttpConfigurer::disable)
            // Session 管理策略
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            // 权限配置
            .authorizeHttpRequests(auth -> auth
                // 公开端点
                .requestMatchers("/auth/login").permitAll()
                .requestMatchers("/auth/session").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // 静态资源（图片、文件）- 无需认证
                .requestMatchers("/api/uploads/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                // API 文档
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 管理员端点需要 ADMIN 角色
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 其他请求需要认证
                .anyRequest().authenticated()
            )
            // 异常处理
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            )
            // 安全头
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(content -> {})
                .xssProtection(xss -> xss.disable())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicy(permissions -> permissions
                    .policy("camera=(), microphone=(), geolocation=()")
                )
            );
        
        return http.build();
    }
}
