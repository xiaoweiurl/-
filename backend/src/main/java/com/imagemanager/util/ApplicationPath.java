package com.imagemanager.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 去掉 servlet context-path（{@code /api}）后的应用路径，供过滤器/拦截器与
 * {@code SecurityConfig} 的 {@code requestMatchers} 对齐。
 */
public final class ApplicationPath {

    private ApplicationPath() {
    }

    public static String of(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            uri = "";
        }
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            String path = uri.substring(context.length());
            return path.isEmpty() ? "/" : path;
        }
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isEmpty()) {
            String info = request.getPathInfo();
            return info == null ? servletPath : servletPath + info;
        }
        return uri.isEmpty() ? "/" : uri;
    }
}
