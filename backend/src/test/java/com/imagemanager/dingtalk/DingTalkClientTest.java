package com.imagemanager.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.config.DingTalkProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkClientTest {

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
    void startWithoutCredentialsThrowsClearErrorOnToken() {
        properties.setAppKey("");
        properties.setAppSecret("");
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (m, u, b, h) -> {
            throw new AssertionError("should not call HTTP when unconfigured");
        });
        DingTalkException ex = assertThrows(DingTalkException.class, client::getAccessToken);
        assertTrue(ex.getMessage().contains("DINGTALK_APP_KEY"));
    }

    @Test
    void fetchAccessTokenParsesNewOpenApiResponse() {
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            assertTrue(url.endsWith("/v1.0/oauth2/accessToken"));
            assertTrue(body.contains("\"appKey\":\"key\""));
            return "{\"accessToken\":\"tok-1\",\"expireIn\":7200}";
        });
        assertEquals("tok-1", client.getAccessToken());
        assertEquals("tok-1", client.getAccessToken());
    }

    @Test
    void fetchAllDepartmentsWalksListsubAndSkipsSchoolDept() {
        AtomicInteger listsubCalls = new AtomicInteger();
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            if (url.contains("/topapi/v2/department/get")) {
                return "{\"errcode\":0,\"result\":{\"dept_id\":1,\"name\":\"宝娜斯集团\",\"parent_id\":0}}";
            }
            if (url.contains("/topapi/v2/department/listsub")) {
                int n = listsubCalls.getAndIncrement();
                if (body.contains("\"dept_id\":1") || n == 0) {
                    return "{\"errcode\":0,\"result\":["
                            + "{\"dept_id\":2,\"name\":\"技术部\",\"parent_id\":1},"
                            + "{\"dept_id\":-7,\"name\":\"家校通讯录\",\"parent_id\":1}"
                            + "]}";
                }
                return "{\"errcode\":0,\"result\":[]}";
            }
            throw new IllegalStateException("unexpected " + url);
        });
        List<DingDepartment> depts = client.fetchAllDepartments();
        assertEquals(2, depts.size());
        assertTrue(depts.stream().anyMatch(d -> Long.valueOf(1L).equals(d.getDeptId())));
        assertTrue(depts.stream().anyMatch(d -> Long.valueOf(2L).equals(d.getDeptId()) && "技术部".equals(d.getName())));
        assertTrue(depts.stream().noneMatch(d -> Long.valueOf(-7L).equals(d.getDeptId())));
    }

    @Test
    void listUsersPaginates() {
        AtomicInteger pages = new AtomicInteger();
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            if (url.contains("/topapi/v2/user/list")) {
                int page = pages.getAndIncrement();
                if (page == 0) {
                    return "{\"errcode\":0,\"result\":{\"has_more\":true,\"next_cursor\":100,\"list\":["
                            + "{\"userid\":\"u1\",\"unionid\":\"un1\",\"name\":\"张三\",\"title\":\"工程师\","
                            + "\"dept_id_list\":[2],\"email\":\"a@b.com\",\"active\":true}]}}";
                }
                return "{\"errcode\":0,\"result\":{\"has_more\":false,\"next_cursor\":0,\"list\":["
                        + "{\"userid\":\"u2\",\"name\":\"李四\",\"title\":\"经理\",\"dept_id_list\":[2]}]}}";
            }
            throw new IllegalStateException(url);
        });
        List<DingUser> users = client.fetchUsersInDepartments(List.of(2L));
        assertEquals(2, users.size());
        assertEquals("张三", users.get(0).getName());
        assertEquals("工程师", users.get(0).getTitle());
        assertEquals("u2", users.get(1).getUserid());
        assertTrue(users.get(0).getDeptIdList().contains(2L));
        assertTrue(users.get(1).getDeptIdList().contains(2L));
    }

    @Test
    void listUsersAddsQueriedDeptWhenDeptIdListMissingOrRootOnly() {
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            if (url.contains("/topapi/v2/user/list")) {
                return "{\"errcode\":0,\"result\":{\"has_more\":false,\"list\":["
                        + "{\"userid\":\"u1\",\"name\":\"张三\",\"dept_id_list\":[]},"
                        + "{\"userid\":\"u2\",\"name\":\"李四\",\"dept_id_list\":[1]}"
                        + "]}}";
            }
            throw new IllegalStateException(url);
        });
        List<DingUser> users = client.fetchUsersInDepartments(List.of(88L));
        assertEquals(2, users.size());
        assertTrue(users.get(0).getDeptIdList().contains(88L));
        assertTrue(users.get(1).getDeptIdList().contains(88L));
        assertTrue(users.get(1).getDeptIdList().contains(1L));
    }

    @Test
    void oapiErrorSurfacesErrmsg() {
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            return "{\"errcode\":88,\"errmsg\":\"无权限\"}";
        });
        DingTalkException ex = assertThrows(DingTalkException.class, () -> client.getDepartment("tok", 1L));
        assertTrue(ex.getMessage().contains("无权限"));
    }

    @Test
    void sendWorkNoticePostsActionCardWithAgentId() {
        properties.setAgentId("123456");
        AtomicInteger calls = new AtomicInteger();
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            if (url.contains("/topapi/message/corpconversation/asyncsend_v2")) {
                calls.incrementAndGet();
                assertTrue(body.contains("\"agent_id\":123456"));
                assertTrue(body.contains("\"userid_list\":\"u-zhang\""));
                assertTrue(body.contains("\"msgtype\":\"action_card\""));
                assertTrue(body.contains("您被指定为打样员"));
                assertTrue(body.contains("/sampler/9"));
                assertTrue(body.contains("\"to_all_user\":false"));
                return "{\"errcode\":0,\"errmsg\":\"ok\",\"task_id\":88}";
            }
            throw new IllegalStateException(url);
        });
        long taskId = client.sendWorkNotice("u-zhang",
                DingTalkWorkNotice.actionCard("您被指定为打样员", "正文", "填写打样表单",
                        "http://localhost:5000/sampler/9"));
        assertEquals(88L, taskId);
        assertEquals(1, calls.get());
    }

    @Test
    void sendWorkNoticeUsesTextWhenNoLink() {
        properties.setAgentId("99");
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            assertTrue(url.contains("/topapi/message/corpconversation/asyncsend_v2"));
            assertTrue(body.contains("\"msgtype\":\"text\""));
            return "{\"errcode\":0,\"task_id\":1}";
        });
        assertEquals(1L, client.sendWorkNotice("u1", DingTalkWorkNotice.text("标题", "内容")));
    }

    @Test
    void sendWorkNoticeWithoutAgentIdThrowsClearError() {
        properties.setAgentId("");
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (m, u, b, h) -> {
            throw new AssertionError("should not call HTTP when agent-id missing");
        });
        DingTalkException ex = assertThrows(DingTalkException.class,
                () -> client.sendWorkNotice("u1", DingTalkWorkNotice.text("t", "b")));
        assertTrue(ex.getMessage().contains("DINGTALK_AGENT_ID"));
    }

    @Test
    void sendWorkNoticeSurfacesErrmsg() {
        properties.setAgentId("1");
        DingTalkClient client = new DingTalkClient(properties, objectMapper, (method, url, body, headers) -> {
            if (url.contains("/v1.0/oauth2/accessToken")) {
                return "{\"accessToken\":\"tok\",\"expireIn\":7200}";
            }
            return "{\"errcode\":40035,\"errmsg\":\"不合法的agent_id\"}";
        });
        DingTalkException ex = assertThrows(DingTalkException.class,
                () -> client.sendWorkNotice("u1", DingTalkWorkNotice.text("t", "b")));
        assertTrue(ex.getMessage().contains("不合法的agent_id"));
    }
}
