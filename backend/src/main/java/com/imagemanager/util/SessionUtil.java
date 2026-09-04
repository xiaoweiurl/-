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
        } catch (Exception ignored) {
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
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    /** 系统统一公司：宝娜斯（不再区分公司） */
    public static final String DEFAULT_COMPANY = "宝娜斯";

    /**
     * 获取当前用户所属公司（系统已统一为宝娜斯，固定返回）
     */
    public static String getCurrentCompany() {
        return DEFAULT_COMPANY;
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
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
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
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
