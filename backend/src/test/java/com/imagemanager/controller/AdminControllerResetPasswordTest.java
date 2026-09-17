package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.entity.User;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerResetPasswordTest {

    private UserService userService;
    private AuthService authService;
    private AdminController controller;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        authService = mock(AuthService.class);
        controller = new AdminController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "authService", authService);
    }

    @Test
    void ordinaryUserCannotResetAnotherPassword() {
        when(authService.validateSession("user-sess")).thenReturn(operator("u1", "bob", "user"));
        when(userService.getUserById("u2")).thenReturn(target("u2", "alice", "user"));

        ApiResponse<Void> response = controller.resetPassword(
                "u2", Map.of("newPassword", "newpass1"), "user-sess");

        assertEquals(403, response.getCode());
        assertFalse(Boolean.TRUE.equals(response.getSuccess()));
        assertEquals("仅管理员和超级管理员可重置其他用户密码", response.getMessage());
        verify(userService, never()).resetPassword("u2", "newpass1");
    }

    @Test
    void samplerCannotResetAnotherPassword() {
        when(authService.validateSession("sampler-sess")).thenReturn(operator("dt:1", "打样员", "sampler"));

        ApiResponse<Void> response = controller.resetPassword(
                "u2", Map.of("newPassword", "newpass1"), "sampler-sess");

        assertEquals(403, response.getCode());
        verify(userService, never()).resetPassword("u2", "newpass1");
    }

    @Test
    void adminCanResetOrdinaryUserPassword() {
        when(authService.validateSession("admin-sess")).thenReturn(operator("a1", "alice", "admin"));
        when(userService.getUserById("u2")).thenReturn(target("u2", "bob", "user"));

        ApiResponse<Void> response = controller.resetPassword(
                "u2", Map.of("newPassword", "newpass1"), "admin-sess");

        assertEquals(200, response.getCode());
        assertTrue(Boolean.TRUE.equals(response.getSuccess()));
        verify(userService).resetPassword("u2", "newpass1");
    }

    @Test
    void superadminCanResetAdminPassword() {
        when(authService.validateSession("sa-sess")).thenReturn(operator("sa1", "root", "superadmin"));
        when(userService.getUserById("a1")).thenReturn(target("a1", "alice", "admin"));

        ApiResponse<Void> response = controller.resetPassword(
                "a1", Map.of("newPassword", "newpass1"), "sa-sess");

        assertEquals(200, response.getCode());
        verify(userService).resetPassword("a1", "newpass1");
    }

    @Test
    void adminCannotResetOtherAdminPassword() {
        when(authService.validateSession("admin-sess")).thenReturn(operator("a1", "alice", "admin"));
        when(userService.getUserById("a2")).thenReturn(target("a2", "other-admin", "admin"));

        ApiResponse<Void> response = controller.resetPassword(
                "a2", Map.of("newPassword", "newpass1"), "admin-sess");

        assertEquals(403, response.getCode());
        assertEquals("管理员不能修改其他管理员的密码", response.getMessage());
        verify(userService, never()).resetPassword("a2", "newpass1");
    }

    private static LoginResponse.UserInfo operator(String id, String username, String role) {
        return LoginResponse.UserInfo.builder()
                .id(id)
                .username(username)
                .role(role)
                .build();
    }

    private static User target(String id, String username, String role) {
        return User.builder()
                .id(id)
                .username(username)
                .role(role)
                .build();
    }
}
