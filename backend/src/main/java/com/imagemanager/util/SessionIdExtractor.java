package com.imagemanager.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一 Session ID 解析，与 BFF {@code resolveBffSessionId} 口径一致：
 * <ol>
 *   <li>httpOnly {@code session_id} Cookie（含 Cookie 头字符串兜底）</li>
 *   <li>{@code X-Session-Id} 请求头</li>
 *   <li>{@code Authorization: Bearer}（curl / API 客户端）</li>
 * </ol>
 * Cookie 与 header 都有且不一致时以 Cookie 为准，避免 localStorage 陈旧会话覆盖有效 Cookie。
 * 禁止从 URL 查询参数读取 session_id。
 */
@Slf4j
public final class SessionIdExtractor {

    public static final String COOKIE_NAME = "session_id";
    public static final String HEADER_NAME = "X-Session-Id";

    private SessionIdExtractor() {
    }

    /**
     * 从请求中提取 session id：Cookie 优先，其次 header，再次 Bearer。
     */
    public static String extract(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        String cookie = fromCookie(request);
        String header = blankToNull(request.getHeader(HEADER_NAME));

        if (cookie != null && header != null && !cookie.equals(header)) {
            log.warn("[Session] X-Session-Id 与 session_id Cookie 不一致，优先使用 Cookie, cookie={}***, header={}***",
                    prefix(cookie), prefix(header));
        }
        if (cookie != null) {
            return cookie;
        }
        if (header != null) {
            return header;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return blankToNull(authHeader.substring(7));
        }

        return null;
    }

    private static String fromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    String value = blankToNull(cookie.getValue());
                    if (value != null) {
                        return value;
                    }
                }
            }
        }

        String cookieHeader = request.getHeader("Cookie");
        if (cookieHeader != null) {
            for (String part : cookieHeader.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith(COOKIE_NAME + "=")) {
                    String value = blankToNull(trimmed.substring((COOKIE_NAME + "=").length()));
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String prefix(String value) {
        return value.length() > 8 ? value.substring(0, 8) : value;
    }
}
