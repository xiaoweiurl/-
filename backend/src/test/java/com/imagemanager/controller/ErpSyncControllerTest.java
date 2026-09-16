package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.ErpAuthService;
import com.imagemanager.service.ErpSyncService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErpSyncControllerTest {

    private ErpSyncService erpSyncService;
    private AuthService authService;
    private ErpSyncController controller;

    @BeforeEach
    void setUp() {
        erpSyncService = mock(ErpSyncService.class);
        ErpAuthService erpAuthService = mock(ErpAuthService.class);
        authService = mock(AuthService.class);
        controller = new ErpSyncController(erpSyncService, erpAuthService, authService);
    }

    @Test
    void userRoleReturnsHttp403WithBusinessBody() {
        when(authService.validateSession("user-session")).thenReturn(user("bob", "user"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("session_id", "user-session"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.syncModule("orders", request);

        assertEquals(403, response.getStatusCode().value());
        ApiResponse<Map<String, Object>> body = response.getBody();
        assertNotNull(body);
        assertEquals(403, body.getCode());
        assertFalse(Boolean.TRUE.equals(body.getSuccess()));
        assertEquals("仅管理员及以上角色可访问 ERP 数据同步功能", body.getMessage());
        verify(erpSyncService, never()).syncModule("orders");
    }

    @Test
    void cookieUserBeatsStaleAdminHeader() {
        when(authService.validateSession("user-cookie")).thenReturn(user("bob", "user"));
        when(authService.validateSession("admin-header")).thenReturn(user("alice", "admin"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("session_id", "user-cookie"));
        request.addHeader("X-Session-Id", "admin-header");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.syncModule("orders", request);

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().getCode());
        verify(erpSyncService, never()).syncModule("orders");
    }

    @Test
    void cookieAdminBeatsStaleUserHeaderAndSyncs() {
        when(authService.validateSession("admin-cookie")).thenReturn(user("alice", "admin"));
        when(authService.validateSession("user-header")).thenReturn(user("bob", "user"));
        when(erpSyncService.syncModule("orders")).thenReturn(Map.of("moduleName", "销售订单", "added", 1));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("session_id", "admin-cookie"));
        request.addHeader("X-Session-Id", "user-header");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.syncModule("orders", request);

        assertEquals(200, response.getStatusCode().value());
        ApiResponse<Map<String, Object>> body = response.getBody();
        assertNotNull(body);
        assertTrue(Boolean.TRUE.equals(body.getSuccess()));
        assertEquals(200, body.getCode());
        verify(erpSyncService).syncModule("orders");
    }

    @Test
    void superadminHeaderOnlySucceeds() {
        when(authService.validateSession("sa-header")).thenReturn(user("root", "superadmin"));
        when(erpSyncService.syncModule("orders")).thenReturn(Map.of("added", 0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Session-Id", "sa-header");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.syncModule("orders", request);

        assertEquals(200, response.getStatusCode().value());
        verify(erpSyncService).syncModule("orders");
    }

    @Test
    void missingSessionReturnsHttp401() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.syncModule("orders", request);

        assertEquals(401, response.getStatusCode().value());
        ApiResponse<Map<String, Object>> body = response.getBody();
        assertNotNull(body);
        assertEquals(401, body.getCode());
        assertEquals("未登录", body.getMessage());
        verify(erpSyncService, never()).syncModule("orders");
    }

    @Test
    void expiredSessionReturnsHttp401() {
        when(authService.validateSession("expired")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Session-Id", "expired");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.syncModule("orders", request);

        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(401, response.getBody().getCode());
        assertEquals("会话已过期，请重新登录", response.getBody().getMessage());
    }

    private static LoginResponse.UserInfo user(String username, String role) {
        return LoginResponse.UserInfo.builder()
                .id("id-" + username)
                .username(username)
                .role(role)
                .build();
    }
}
