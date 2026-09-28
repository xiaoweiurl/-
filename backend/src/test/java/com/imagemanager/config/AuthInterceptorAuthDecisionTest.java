package com.imagemanager.config;

import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AuthInterceptor 鉴权判定：匿名 ≠ 已登录（避免 403/放行错位）；
 * 普通 user 访问商品库放行；未登录 401。
 */
class AuthInterceptorAuthDecisionTest {

    private AuthService authService;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        interceptor = new AuthInterceptor();
        ReflectionTestUtils.setField(interceptor, "authService", authService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousTokenOnGoodsLibraryIsUnauthorizedNotForbidden() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        MockHttpServletRequest request = goodsLibraryRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(request, response, new Object());
        assertTrue(!ok);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("请先登录"));
    }

    @Test
    void authenticatedUserMayAccessGoodsLibrary() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "ding-user-1", null, SessionAuthorities.fromRole("user")));

        MockHttpServletRequest request = goodsLibraryRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void missingSessionOnGoodsLibraryIs401() throws Exception {
        MockHttpServletRequest request = goodsLibraryRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(request, response, new Object());
        assertTrue(!ok);
        assertEquals(401, response.getStatus());
    }

    @Test
    void userRoleHittingAdminIs403() throws Exception {
        when(authService.validateSession("sess")).thenReturn(LoginResponse.UserInfo.builder()
                .id("u1").username("xiaowei").role("user").build());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/users");
        request.setContextPath("/api");
        request.setRequestURI("/api/admin/users");
        request.addHeader("X-Session-Id", "sess");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(request, response, new Object());
        assertTrue(!ok);
        assertEquals(403, response.getStatus());
    }

    @Test
    void authenticatedUserMayAccessOwnSettings() throws Exception {
        when(authService.validateSession("sess")).thenReturn(LoginResponse.UserInfo.builder()
                .id("u1").username("xiaowei").role("user").scope("full").build());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/settings");
        request.setContextPath("/api");
        request.setRequestURI("/api/user/settings");
        request.addHeader("X-Session-Id", "sess");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void securityContextUserMayAccessOwnSettings() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "u1", null, SessionAuthorities.fromRole("user")));
        when(authService.validateSession("sess")).thenReturn(LoginResponse.UserInfo.builder()
                .id("u1").username("xiaowei").role("user").scope("full").build());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/settings");
        request.setContextPath("/api");
        request.setRequestURI("/api/user/settings");
        request.addHeader("X-Session-Id", "sess");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void samplerScopeCannotAccessAccountSettings() throws Exception {
        LoginResponse.UserInfo sampler = LoginResponse.UserInfo.builder()
                .id("dt:u1").username("打样员").role("sampler").scope("sampler").samplerGoodsId("9").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dt:u1", null, SessionAuthorities.fromRole("sampler")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/settings");
        request.setContextPath("/api");
        request.setRequestURI("/api/user/settings");
        request.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, sampler);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(!interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
    }

    @Test
    void securityContextUserCannotResetOthersPassword() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "u1", null, SessionAuthorities.fromRole("user")));
        when(authService.validateSession("sess")).thenReturn(LoginResponse.UserInfo.builder()
                .id("u1").username("xiaowei").role("user").build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/users/u2/reset-password");
        request.setContextPath("/api");
        request.setRequestURI("/api/admin/users/u2/reset-password");
        request.addHeader("X-Session-Id", "sess");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(request, response, new Object());
        assertTrue(!ok);
        assertEquals(403, response.getStatus());
    }

    @Test
    void samplerScopeCannotHitErpOrOrg() throws Exception {
        LoginResponse.UserInfo sampler = LoginResponse.UserInfo.builder()
                .id("dt:u1").username("打样员").role("sampler").scope("sampler").samplerGoodsId("9").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dt:u1", null, SessionAuthorities.fromRole("sampler")));

        MockHttpServletRequest erp = new MockHttpServletRequest("GET", "/erp-sync/status");
        erp.setContextPath("/api");
        erp.setRequestURI("/api/erp-sync/status");
        erp.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, sampler);
        MockHttpServletResponse erpRes = new MockHttpServletResponse();
        assertTrue(!interceptor.preHandle(erp, erpRes, new Object()));
        assertEquals(403, erpRes.getStatus());

        MockHttpServletRequest org = new MockHttpServletRequest("POST", "/org/sync");
        org.setContextPath("/api");
        org.setRequestURI("/api/org/sync");
        org.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, sampler);
        MockHttpServletResponse orgRes = new MockHttpServletResponse();
        assertTrue(!interceptor.preHandle(org, orgRes, new Object()));
        assertEquals(403, orgRes.getStatus());
    }

    @Test
    void samplerScopeMayAccessBoundGoodsForm() throws Exception {
        LoginResponse.UserInfo sampler = LoginResponse.UserInfo.builder()
                .id("dt:u1").username("打样员").role("sampler").scope("sampler").samplerGoodsId("9").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dt:u1", null, SessionAuthorities.fromRole("sampler")));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/goods-library/9");
        request.setContextPath("/api");
        request.setRequestURI("/api/goods-library/9");
        request.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, sampler);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void userCanSubmitFeedbackButCannotListOrExportGaps() throws Exception {
        LoginResponse.UserInfo user = LoginResponse.UserInfo.builder()
                .id("u1").username("xiaowei").role("user").company("EXAMPLE").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u1", null, SessionAuthorities.fromRole("user")));

        MockHttpServletRequest feedback = apiRequest("POST", "/chat/feedback");
        feedback.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, user);
        MockHttpServletResponse feedbackRes = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(feedback, feedbackRes, new Object()));

        MockHttpServletRequest gaps = apiRequest("GET", "/chat/knowledge-gaps");
        gaps.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, user);
        MockHttpServletResponse gapsRes = new MockHttpServletResponse();
        assertTrue(!interceptor.preHandle(gaps, gapsRes, new Object()));
        assertEquals(403, gapsRes.getStatus());

        MockHttpServletRequest export = apiRequest("GET", "/chat/knowledge-gaps/export");
        export.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, user);
        MockHttpServletResponse exportRes = new MockHttpServletResponse();
        assertTrue(!interceptor.preHandle(export, exportRes, new Object()));
        assertEquals(403, exportRes.getStatus());
    }

    @Test
    void adminCanListGapsAndTriggerEval() throws Exception {
        LoginResponse.UserInfo admin = LoginResponse.UserInfo.builder()
                .id("a1").username("admin").role("admin").company("EXAMPLE").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("a1", null, SessionAuthorities.fromRole("admin")));

        MockHttpServletRequest gaps = apiRequest("GET", "/chat/knowledge-gaps");
        gaps.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, admin);
        assertTrue(interceptor.preHandle(gaps, new MockHttpServletResponse(), new Object()));

        LoginResponse.UserInfo superadmin = LoginResponse.UserInfo.builder()
                .id("s1").username("superadmin").role("superadmin").company("EXAMPLE").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("s1", null, SessionAuthorities.fromRole("superadmin")));
        MockHttpServletRequest eval = apiRequest("POST", "/chat/rag-eval");
        eval.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, superadmin);
        assertTrue(interceptor.preHandle(eval, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void samplerCannotUseFeedbackManagement() throws Exception {
        LoginResponse.UserInfo sampler = LoginResponse.UserInfo.builder()
                .id("dt:u1").username("打样员").role("sampler").scope("sampler").samplerGoodsId("9").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dt:u1", null, SessionAuthorities.fromRole("sampler")));

        for (String[] call : new String[][]{
                {"POST", "/chat/feedback"},
                {"GET", "/chat/knowledge-gaps"},
                {"GET", "/chat/knowledge-gaps/export"},
                {"POST", "/chat/rag-eval"}
        }) {
            MockHttpServletRequest request = apiRequest(call[0], call[1]);
            request.setAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE, sampler);
            MockHttpServletResponse response = new MockHttpServletResponse();
            assertTrue(!interceptor.preHandle(request, response, new Object()));
            assertEquals(403, response.getStatus());
        }
    }

    private static MockHttpServletRequest apiRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContextPath("/api");
        request.setRequestURI("/api" + path);
        return request;
    }

    private static MockHttpServletRequest goodsLibraryRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/goods-library");
        request.setContextPath("/api");
        request.setRequestURI("/api/goods-library");
        return request;
    }
}
