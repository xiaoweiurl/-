package com.imagemanager.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces FRP 403: browser Origin {@code http://ai.bonasoma.com} forwarded to Java
 * while the whitelist is only localhost — CorsFilter returns 403 before session auth.
 */
class CorsOriginPolicyTest {

    @Test
    void whitelistMergesFrontendUrlAndDropsWildcard() {
        List<String> origins = CorsOriginPolicy.parseWhitelist(
                "http://localhost:5000, *", "http://ai.bonasoma.com");
        assertTrue(origins.contains("http://localhost:5000"));
        assertTrue(origins.contains("http://ai.bonasoma.com"));
        assertFalse(origins.contains("*"));
    }

    @Test
    void emptyWhitelistFallsBackToLocalhost() {
        List<String> origins = CorsOriginPolicy.parseWhitelist("  ", null);
        assertEquals(List.of("http://localhost:5000"), origins);
    }

    @Test
    void frpPublicOriginIsSameHostAsRequest() {
        assertTrue(CorsOriginPolicy.isSamePublicHost(
                "http://ai.bonasoma.com", "ai.bonasoma.com"));
        assertTrue(CorsOriginPolicy.isSamePublicHost(
                "http://ai.bonasoma.com", "ai.bonasoma.com:80"));
        assertFalse(CorsOriginPolicy.isSamePublicHost(
                "http://ai.bonasoma.com", "localhost:8080"));
    }

    @Test
    void unknownOriginNotOnWhitelistIsRejected() {
        List<String> whitelist = CorsOriginPolicy.parseWhitelist("http://localhost:5000", "http://localhost:5000");
        assertFalse(CorsOriginPolicy.isAllowed("http://ai.bonasoma.com", whitelist, "localhost:8080"));
        assertTrue(CorsOriginPolicy.isAllowed("http://localhost:5000", whitelist, "localhost:8080"));
        assertTrue(CorsOriginPolicy.isAllowed("http://ai.bonasoma.com", whitelist, "ai.bonasoma.com"));
    }

    @Test
    void forwardedFrpOriginAgainstLocalhostHostYieldsCors403() throws Exception {
        CorsConfiguration config = new AppCorsConfigurationSource(
                "http://localhost:5000", "http://localhost:5000")
                .getCorsConfiguration(request("http://ai.bonasoma.com", "localhost:8080"));

        DefaultCorsProcessor processor = new DefaultCorsProcessor();
        MockHttpServletRequest request = request("http://ai.bonasoma.com", "localhost:8080");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean valid = processor.processRequest(config, request, response);
        assertFalse(valid, "BFF-forwarded public Origin must not pass localhost-only CORS");
        assertEquals(403, response.getStatus());
    }

    @Test
    void sameHostFrpOriginIsAllowedEvenIfNotListed() throws Exception {
        AppCorsConfigurationSource source = new AppCorsConfigurationSource(
                "http://localhost:5000", "http://localhost:5000");
        MockHttpServletRequest request = request("http://ai.bonasoma.com", "ai.bonasoma.com");
        CorsConfiguration config = source.getCorsConfiguration(request);

        DefaultCorsProcessor processor = new DefaultCorsProcessor();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(processor.processRequest(config, request, response));
        assertTrue(config.getAllowedOrigins().contains("http://ai.bonasoma.com"));
    }

    @Test
    void noOriginIsNotACorsRequestSoAuthCanRun() throws Exception {
        // BFF strips Origin: CorsFilter must not 403; session filter then decides 401/200.
        CorsConfiguration config = new AppCorsConfigurationSource(
                "http://localhost:5000", "http://localhost:5000")
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/goods-library"));

        DefaultCorsProcessor processor = new DefaultCorsProcessor();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/goods-library");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(processor.processRequest(config, request, response));
        assertEquals(200, response.getStatus());
    }

    private static MockHttpServletRequest request(String origin, String host) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/goods-library");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.HOST, host);
        return request;
    }
}
