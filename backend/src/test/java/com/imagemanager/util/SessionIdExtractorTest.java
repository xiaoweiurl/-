package com.imagemanager.util;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionIdExtractorTest {

    @Test
    void cookieWinsWhenHeaderDiffers() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("session_id", "cookie-admin"));
        request.addHeader("X-Session-Id", "header-stale-user");

        assertEquals("cookie-admin", SessionIdExtractor.extract(request));
    }

    @Test
    void headerUsedWhenCookieAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Session-Id", "header-only");

        assertEquals("header-only", SessionIdExtractor.extract(request));
    }

    @Test
    void cookieUsedWhenHeaderAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("session_id", "cookie-only"));

        assertEquals("cookie-only", SessionIdExtractor.extract(request));
    }

    @Test
    void blankCookieFallsThroughToHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("session_id", "  "));
        request.addHeader("X-Session-Id", "header-fallback");

        assertEquals("header-fallback", SessionIdExtractor.extract(request));
    }

    @Test
    void cookieHeaderStringWhenParsedCookiesMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Cookie", "other=1; session_id=from-cookie-header; extra=2");
        request.addHeader("X-Session-Id", "stale-header");

        assertEquals("from-cookie-header", SessionIdExtractor.extract(request));
    }

    @Test
    void bearerUsedLast() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer token-from-auth");

        assertEquals("token-from-auth", SessionIdExtractor.extract(request));
    }

    @Test
    void cookieBeatsBearerAndHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("session_id", "cookie-session"));
        request.addHeader("X-Session-Id", "header-session");
        request.addHeader("Authorization", "Bearer bearer-session");

        assertEquals("cookie-session", SessionIdExtractor.extract(request));
    }

    @Test
    void queryParamIsIgnored() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("session_id", "from-query");

        assertNull(SessionIdExtractor.extract(request));
    }

    @Test
    void nullRequestReturnsNull() {
        assertNull(SessionIdExtractor.extract(null));
    }
}
