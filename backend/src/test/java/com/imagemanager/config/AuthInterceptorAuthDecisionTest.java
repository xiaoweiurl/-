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

    private static MockHttpServletRequest goodsLibraryRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/goods-library");
        request.setContextPath("/api");
        request.setRequestURI("/api/goods-library");
        return request;
    }
}
