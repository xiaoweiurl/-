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
     * CORS 允许来源白名单（逗号分隔），通过 app.cors.allowed-origins 配置。
     * 安全约束：allowCredentials=true 时禁止使用通配符 *，必须显式列举前端地址。
     */
    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:http://localhost:5000}")
    private String allowedOrigins;

    /**
     * CORS 配置 - 来源白名单 + Cookie 传递
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 白名单来源（禁止 "*" 与 allowCredentials 同用的危险组合）
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 仅放行实际需要的请求头
        configuration.setAllowedHeaders(Arrays.asList(
            "Content-Type", "Authorization", "X-Session-Id", "X-Requested-With"
        ));
        // 允许 credentials（Cookie）跨域传递
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        // 允许暴露的响应头
        configuration.setExposedHeaders(Arrays.asList(
            "Set-Cookie",
            "X-Session-Id"
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
            // CSRF 说明：本系统鉴权不依赖浏览器自动携带的凭证 —— 前端通过自定义请求头
            // X-Session-Id 传递会话（跨站表单/图片等 CSRF 向量无法附加自定义头），天然免疫 CSRF；
            // Cookie 仅为同站便捷通道且已设置 SameSite=Lax（见 AuthController），跨站请求不会携带。
            // 因此保持 CSRF 关闭，避免双通道鉴权下的误拦截。
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
                .requestMatchers("/auth/register").permitAll()
                .requestMatchers("/auth/session").permitAll()
                .requestMatchers("/auth/forgot-password/**").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // 分享链接公开访问 - 无需认证（context-path 已去掉 /api 前缀）
                .requestMatchers("/share/access/**").permitAll()
                // API 文档
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 数据修复/运维/审计/备份端点需要 ADMIN 角色（DataFixController 另有功能开关）
                .requestMatchers("/fix/**", "/ops/**", "/audit/**", "/backup/**").hasRole("ADMIN")
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
