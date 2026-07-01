package com.imagemanager.config;

import com.imagemanager.service.OpsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

/**
 * API 监控拦截器
 * 在每次请求完成后自动记录到 api_metrics 表，
 * 异常时同步记录到 system_errors 表
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiMetricsInterceptor implements HandlerInterceptor {

    private final OpsService opsService;

    private static final String ATTR_START_TIME = "apiMetricsStartTime";
    private static final String ATTR_ENDPOINT = "apiMetricsEndpoint";

    // 不记录的路径前缀（避免自引用循环和静态资源）
    private static final String[] EXCLUDED_PATHS = {
            "/ops/metrics", "/ops/errors", "/ops/performance", "/ops/backups",
            "/swagger", "/v3/api-docs", "/actuator", "/favicon", "/error",
            "/static", "/webjars"
    };

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        String path = request.getRequestURI();
        // 排除运维自身接口和静态资源
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                return true;
            }
        }
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        request.setAttribute(ATTR_ENDPOINT, path);
        return true;
    }

    @Override
    public void postHandle(@NonNull HttpServletRequest request,
                           @NonNull HttpServletResponse response,
                           @NonNull Object handler,
                           ModelAndView modelAndView) {
        // 不需要处理
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                Exception ex) {
        try {
            Long startTime = (Long) request.getAttribute(ATTR_START_TIME);
            if (startTime == null) {
                return; // 被排除的路径
            }

            String endpoint = (String) request.getAttribute(ATTR_ENDPOINT);
            String method = request.getMethod();
            int statusCode = response.getStatus();
            long responseTimeMs = System.currentTimeMillis() - startTime;
            String userId = request.getHeader("X-User-Id");
            String ipAddress = getClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            String company = request.getHeader("X-Company");

            // 记录 API 调用指标
            Map<String, Object> metric = new HashMap<>();
            metric.put("endpoint", endpoint);
            metric.put("method", method);
            metric.put("statusCode", statusCode);
            metric.put("responseTimeMs", responseTimeMs);
            metric.put("userId", userId);
            metric.put("ipAddress", ipAddress);
            metric.put("userAgent", userAgent != null && userAgent.length() > 500
                    ? userAgent.substring(0, 500) : userAgent);
            metric.put("company", company);

            if (ex != null) {
                metric.put("errorMessage", ex.getMessage());
            }

            opsService.recordMetric(metric);

            // 如果有异常且是服务端错误，记录到 system_errors
            if (ex != null || statusCode >= 500) {
                Map<String, Object> error = new HashMap<>();
                error.put("errorType", ex != null ? ex.getClass().getSimpleName() : "HttpError");
                error.put("severity", statusCode >= 500 ? "error" : "warning");
                error.put("message", ex != null ? ex.getMessage() : "HTTP " + statusCode);
                error.put("stackTrace", getStackTrace(ex));
                error.put("endpoint", endpoint);
                error.put("method", method);
                error.put("userId", userId);
                error.put("ipAddress", ipAddress);
                error.put("statusCode", statusCode);
                error.put("company", company);

                opsService.recordError(error);
            }
        } catch (Exception e) {
            // 监控记录失败不应影响正常请求
            log.warn("[ApiMetrics] 记录失败: {}", e.getMessage());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String getStackTrace(Exception ex) {
        if (ex == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(ex.toString()).append("\n");
        StackTraceElement[] elements = ex.getStackTrace();
        // 只取前 20 行
        int limit = Math.min(elements.length, 20);
        for (int i = 0; i < limit; i++) {
            sb.append("\tat ").append(elements[i]).append("\n");
        }
        if (elements.length > limit) {
            sb.append("\t... ").append(elements.length - limit).append(" more\n");
        }
        return sb.toString();
    }
}
