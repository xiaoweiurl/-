package com.imagemanager.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 按请求组装 CORS 配置：静态白名单 + 与当前 Host 相同的 Origin（FRP 同站直达 Java）。
 */
@Slf4j
public class AppCorsConfigurationSource implements CorsConfigurationSource {

    private final List<String> whitelist;

    public AppCorsConfigurationSource(String allowedOrigins, String frontendUrl) {
        this.whitelist = CorsOriginPolicy.parseWhitelist(allowedOrigins, frontendUrl);
    }

    List<String> whitelist() {
        return whitelist;
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = new ArrayList<>(whitelist);
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        String host = request.getHeader(HttpHeaders.HOST);
        if (origin != null && !origin.isBlank() && CorsOriginPolicy.isSamePublicHost(origin, host)
                && !origins.contains(origin.trim())) {
            origins.add(origin.trim());
        }
        if (origin != null && !origin.isBlank() && !CorsOriginPolicy.isAllowed(origin, whitelist, host)) {
            log.warn("[CORS] Origin 不在白名单且与 Host 不同，将 403: origin={}, host={}, {} {}",
                    origin, host, request.getMethod(), request.getRequestURI());
        }
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(
                "Content-Type", "Authorization", "X-Session-Id", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        configuration.setExposedHeaders(List.of("Set-Cookie", "X-Session-Id"));
        return configuration;
    }
}
