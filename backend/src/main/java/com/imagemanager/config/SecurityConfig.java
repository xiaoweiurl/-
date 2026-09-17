package com.imagemanager.config;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Security 配置类 - 完整安全配置
 * 
 * @author Image Manager Team
 * @version 2.0.0
 */
@Slf4j
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
            log.warn("未认证请求被拒绝: {} {}", request.getMethod(), request.getRequestURI());
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
            log.warn("已登录但权限不足: {} {} principal={}",
                    request.getMethod(), request.getRequestURI(),
                    request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "null");
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
    @Value("${app.cors.allowed-origins:http://localhost:5000}")
    private String allowedOrigins;

    @Value("${app.frontend.url:http://localhost:5000}")
    private String frontendUrl;

    /**
     * CORS 配置：白名单（含 FRONTEND_URL）+ 与请求 Host 相同的 Origin（FRP 同站）。
     * 禁止 "*" 与 allowCredentials 同用。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return new AppCorsConfigurationSource(allowedOrigins, frontendUrl);
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
            .addFilterBefore(sessionIdAuthFilter, UsernamePasswordAuthenticationFilter.class)
            // CSRF 说明：本系统鉴权不依赖浏览器自动携带的凭证 —— 前端通过自定义请求头
            // X-Session-Id 传递会话（跨站表单/图片等 CSRF 向量无法附加自定义头），天然免疫 CSRF；
            // Cookie 仅为同站便捷通道且已设置 SameSite=Lax（见 AuthController），跨站请求不会携带。
            // 因此保持 CSRF 关闭，避免双通道鉴权下的误拦截。
            .csrf(c -> c.disable())
            // 禁用 HTTP Basic
            .httpBasic(b -> b.disable())
            // 禁用表单登录
            .formLogin(f -> f.disable())
            // Session 管理策略
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            // 匿名认证必须保留：无会话时 AuthorizationFilter 会抛 AccessDeniedException，
            // ExceptionTranslationFilter 仅在 AnonymousAuthenticationToken 时走 EntryPoint（401）；
            // 关掉 anonymous 会把「未登录」误报成 403「权限不足」。
            .anonymous(Customizer.withDefaults())
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
                // 钉钉组织同步（办公/管理端）
                .requestMatchers("/org/**").hasRole("ADMIN")
                // 管理员端点需要 ADMIN 角色
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 业务接口（含 /goods-library/**）只需登录，不按管理员角色拦截。
                // 钉钉姓名注册用户角色为 user → ROLE_USER，应能列表/读写商品库。
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
