package com.imagemanager.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * 会话角色 → Spring Security 权限。钉钉注册用户默认 {@code user}，映射 {@code ROLE_USER}。
 * 商品库等业务接口只需 {@code authenticated()}，不得要求 {@code ROLE_ADMIN}。
 */
public final class SessionAuthorities {

    private SessionAuthorities() {
    }

    public static List<SimpleGrantedAuthority> fromRole(String role) {
        boolean isAdmin = role != null
                && ("ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role));
        return List.of(new SimpleGrantedAuthority(isAdmin ? "ROLE_ADMIN" : "ROLE_USER"));
    }

    public static boolean isAdminRole(String role) {
        return role != null
                && ("ADMIN".equalsIgnoreCase(role) || "SUPERADMIN".equalsIgnoreCase(role));
    }
}
