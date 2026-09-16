package com.imagemanager.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Session 工具类
 */
public class SessionUtil {

    public static final String USER_INFO_ATTRIBUTE = "userInfo";

    /**
     * 获取当前登录用户ID
     */
    public static String getCurrentUserId() {
        // 1. 从Spring Security上下文获取
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (@SuppressWarnings("unused") Exception ignored) {
        }

        // 2. 从当前Request的Attribute获取（由拦截器设置）
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                Object userInfo = request.getAttribute(USER_INFO_ATTRIBUTE);
                if (userInfo != null) {
                    if (userInfo instanceof Map) {
                        Object id = ((Map<?, ?>) userInfo).get("id");
                        if (id != null) return id.toString();
                    } else {
                        try {
                            Object id = userInfo.getClass().getMethod("getId").invoke(userInfo);
                            if (id != null) return id.toString();
                        } catch (@SuppressWarnings("unused") Exception ignored) {
                        }
                    }
                }
            }
        } catch (@SuppressWarnings("unused") Exception ignored) {
        }

        return null;
    }

    /**
     * 获取当前登录用户ID，无会话时直接抛出 401 异常（禁止降级默认用户）。
     * 所有需要租户隔离的业务代码必须统一使用本方法。
     */
    public static String requireCurrentUserId() {
        String userId = getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "未登录或会话已过期");
        }
        return userId;
    }

    /**
     * 获取当前用户所属公司
     */
    public static String getCurrentCompany() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                Object userInfo = request.getAttribute(USER_INFO_ATTRIBUTE);
                if (userInfo != null) {
                    if (userInfo instanceof Map) {
                        Object company = ((Map<?, ?>) userInfo).get("company");
                        if (company != null && !company.toString().isEmpty()) return company.toString();
                    } else {
                        try {
                            Object company = userInfo.getClass().getMethod("getCompany").invoke(userInfo);
                            if (company != null && !company.toString().isEmpty()) return company.toString();
                        } catch (@SuppressWarnings("unused") Exception ignored) {
                        }
                    }
                }
            }
        } catch (@SuppressWarnings("unused") Exception ignored) {
        }
        return "宝娜斯集团"; // 默认值
    }

    /**
     * 获取当前用户角色
     */
    public static String getCurrentUserRole() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                Object userInfo = request.getAttribute(USER_INFO_ATTRIBUTE);
                if (userInfo != null) {
                    if (userInfo instanceof Map) {
                        Object role = ((Map<?, ?>) userInfo).get("role");
                        if (role != null) return role.toString();
                    } else {
                        try {
                            Object role = userInfo.getClass().getMethod("getRole").invoke(userInfo);
                            if (role != null) return role.toString();
                        } catch (@SuppressWarnings("unused") Exception ignored) {
                        }
                    }
                }
            }
        } catch (@SuppressWarnings("unused") Exception ignored) {
        }
        return "user"; // 默认普通用户
    }

    /**
     * 获取当前登录用户名
     */
    public static String getCurrentUsername() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                Object userInfo = request.getAttribute(USER_INFO_ATTRIBUTE);
                if (userInfo != null) {
                    if (userInfo instanceof Map) {
                        Object username = ((Map<?, ?>) userInfo).get("username");
                        if (username != null) return username.toString();
                    } else {
                        try {
                            Object username = userInfo.getClass().getMethod("getUsername").invoke(userInfo);
                            if (username != null) return username.toString();
                        } catch (@SuppressWarnings("unused") Exception ignored) {
                        }
                    }
                }
            }
        } catch (@SuppressWarnings("unused") Exception ignored) {
        }
        return null;
    }
}
