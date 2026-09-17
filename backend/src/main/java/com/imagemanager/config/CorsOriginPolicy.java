package com.imagemanager.config;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CORS 来源判定：白名单 + 与请求 Host 相同的公开来源（FRP 同站）。
 * <p>
 * 禁止 {@code *} 与 {@code allowCredentials=true} 组合。浏览器 {@code fetch()} 总会带
 * {@code Origin}；若 BFF 把 FRP 公网 Origin（如 {@code http://ai.bonasoma.com}）原样转到
 * Java，而白名单只有 {@code http://localhost:5000}，Spring {@code CorsFilter} 会在鉴权前
 * 直接 403，表现为「已登录但大量接口鉴权失败」。
 */
public final class CorsOriginPolicy {

    private CorsOriginPolicy() {
    }

    /**
     * 合并 {@code app.cors.allowed-origins} 与 {@code app.frontend.url}，去掉空白和 {@code *}。
     */
    public static List<String> parseWhitelist(String allowedOrigins, String frontendUrl) {
        Set<String> origins = new LinkedHashSet<>();
        addCsv(origins, allowedOrigins);
        addCsv(origins, frontendUrl);
        origins.remove("*");
        if (origins.isEmpty()) {
            origins.add("http://localhost:5000");
        }
        return List.copyOf(origins);
    }

    /**
     * Origin 是否与请求 Host 为同一公开主机（忽略默认 80/443 端口与 http/https 差，适配 TLS 终结）。
     */
    public static boolean isSamePublicHost(String origin, String hostHeader) {
        HostPort originHost = hostPortFromOrigin(origin);
        HostPort requestHost = hostPortFromHostHeader(hostHeader);
        if (originHost == null || requestHost == null) {
            return false;
        }
        return originHost.host.equals(requestHost.host);
    }

    public static boolean isAllowed(String origin, List<String> whitelist, String hostHeader) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        String trimmed = origin.trim();
        if (whitelist != null && whitelist.contains(trimmed)) {
            return true;
        }
        return isSamePublicHost(trimmed, hostHeader);
    }

    private static void addCsv(Set<String> target, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                target.add(trimmed);
            }
        }
    }

    private static HostPort hostPortFromOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(origin.trim());
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            return new HostPort(host.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static HostPort hostPortFromHostHeader(String hostHeader) {
        if (hostHeader == null || hostHeader.isBlank()) {
            return null;
        }
        String raw = hostHeader.trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("[")) {
            int end = raw.indexOf(']');
            if (end < 0) {
                return null;
            }
            String host = raw.substring(1, end);
            return new HostPort(host);
        }
        int colon = raw.lastIndexOf(':');
        if (colon > 0 && raw.indexOf(':') == colon) {
            return new HostPort(raw.substring(0, colon));
        }
        return new HostPort(raw);
    }

    private static final class HostPort {
        private final String host;

        private HostPort(String host) {
            this.host = host;
        }
    }
}
