package com.imagemanager.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthInterceptorImageSearchTest {

    @Test
    void evalIsAdminOnlyAndQueryIsNot() {
        assertTrue(AuthInterceptor.requiresAdmin("/image-search/eval"));
        assertFalse(AuthInterceptor.requiresAdmin("/image-search/query"));
        assertFalse(AuthInterceptor.requiresAdmin("/image-search/status"));
    }
}
