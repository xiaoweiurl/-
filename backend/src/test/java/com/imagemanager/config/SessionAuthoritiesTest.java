package com.imagemanager.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionAuthoritiesTest {

    @Test
    void dingTalkRegisteredUserIsRoleUserNotAdmin() {
        List<? extends GrantedAuthority> user = SessionAuthorities.fromRole("user");
        assertEquals("ROLE_USER", user.get(0).getAuthority());
        assertFalse(SessionAuthorities.isAdminRole("user"));
        assertFalse(SessionAuthorities.isAdminRole(null));
    }

    @Test
    void adminAndSuperadminMapToRoleAdmin() {
        assertEquals("ROLE_ADMIN", SessionAuthorities.fromRole("admin").get(0).getAuthority());
        assertEquals("ROLE_ADMIN", SessionAuthorities.fromRole("SUPERADMIN").get(0).getAuthority());
        assertTrue(SessionAuthorities.isAdminRole("admin"));
        assertTrue(SessionAuthorities.isAdminRole("superadmin"));
    }

    @Test
    void goodsLibraryDoesNotRequireAdminRole() {
        // SecurityConfig.anyRequest().authenticated() — ROLE_USER is sufficient.
        assertEquals("ROLE_USER", SessionAuthorities.fromRole("user").get(0).getAuthority());
        assertFalse(SessionAuthorities.isAdminRole("user"),
                "钉钉注册办公用户必须能访问商品库，不能因非 ADMIN 被 403");
    }

    @Test
    void samplerRoleIsNotAdmin() {
        assertEquals("ROLE_USER", SessionAuthorities.fromRole("sampler").get(0).getAuthority());
        assertFalse(SessionAuthorities.isAdminRole("sampler"));
    }
}
