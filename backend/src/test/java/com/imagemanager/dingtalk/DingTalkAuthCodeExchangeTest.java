package com.imagemanager.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.config.DingTalkProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkAuthCodeExchangeTest {

    private DingTalkProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new DingTalkProperties();
        properties.setAppKey("key");
        properties.setAppSecret("secret");
        properties.setApiBaseUrl("https://api.dingtalk.com");
        properties.setOapiBaseUrl("https://oapi.dingtalk.com");
        objectMapper = new ObjectMapper();
    }

    @Test
    void getUserByAuthCodePostsCodeAndParsesUserid() {
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            assertTrue(url.contains("/topapi/v2/user/getuserinfo"));
            assertTrue(body.contains("\"code\":\"auth-abc\""));
            return "{\"errcode\":0,\"result\":{"
                    + "\"userid\":\"u-li\","
                    + "\"name\":\"李四\","
                    + "\"unionid\":\"un-1\","
                    + "\"device_id\":\"dev\""
                    + "}}";
        });
        DingTalkAuthUser user = client.getUserByAuthCode("auth-abc");
        assertEquals("u-li", user.getUserid());
        assertEquals("李四", user.getName());
        assertEquals("un-1", user.getUnionid());
    }

    @Test
    void getUserByAuthCodeBlankThrows() {
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (m, u, b, h) -> {
            throw new AssertionError("should not call HTTP");
        });
        assertThrows(DingTalkException.class, () -> client.getUserByAuthCode("  "));
    }

    @Test
    void getUserByAuthCodeMissingUseridThrows() {
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            return "{\"errcode\":0,\"result\":{}}";
        });
        DingTalkException ex = assertThrows(DingTalkException.class, () -> client.getUserByAuthCode("code"));
        assertTrue(ex.getMessage().contains("userid"));
    }

    @Test
    void getUserByAuthCodeSurfacesErrmsg() {
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            return "{\"errcode\":40078,\"errmsg\":\"不合法的authCode\"}";
        });
        DingTalkException ex = assertThrows(DingTalkException.class, () -> client.getUserByAuthCode("bad"));
        assertTrue(ex.getMessage().contains("不合法的authCode"));
    }

    @Test
    void getJsapiTicketUsesGetAndCaches() {
        java.util.concurrent.atomic.AtomicInteger ticketCalls = new java.util.concurrent.atomic.AtomicInteger();
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            assertEquals("GET", method);
            assertTrue(url.contains("/get_jsapi_ticket"));
            ticketCalls.incrementAndGet();
            return "{\"errcode\":0,\"ticket\":\"js-ticket\",\"expires_in\":7200}";
        });
        assertEquals("js-ticket", client.getJsapiTicket());
        assertEquals("js-ticket", client.getJsapiTicket());
        assertEquals(1, ticketCalls.get());
    }
}
