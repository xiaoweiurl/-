package com.imagemanager.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApplicationPathTest {

    @Test
    void stripsServletContextPathSoMatchersAlignWithSecurityConfig() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/goods-library");
        request.setContextPath("/api");
        request.setRequestURI("/api/goods-library");

        assertEquals("/goods-library", ApplicationPath.of(request));
    }

    @Test
    void loginPathWithoutContextPrefix() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setContextPath("/api");
        request.setRequestURI("/api/auth/login");

        assertEquals("/auth/login", ApplicationPath.of(request));
    }
}
