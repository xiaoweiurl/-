package com.imagemanager.dingtalk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.config.DingTalkProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 钉钉企业内部应用 HTTP 客户端。
 *
 * 选用的 OpenAPI（企业内部应用）：
 * <ul>
 *   <li>获取 token：POST https://api.dingtalk.com/v1.0/oauth2/accessToken
 *       （新 OpenAPI，body: appKey/appSecret）</li>
 *   <li>部门详情：POST https://oapi.dingtalk.com/topapi/v2/department/get</li>
 *   <li>子部门列表：POST https://oapi.dingtalk.com/topapi/v2/department/listsub
 *       （只返回下一级，需递归；跳过 dept_id &lt; 0 的家校通讯录）</li>
 *   <li>部门成员：POST https://oapi.dingtalk.com/topapi/v2/user/list（分页 cursor）</li>
 * </ul>
 * 权限：通讯录部门信息读权限、成员信息读权限（手机号可选）。
 */
@Slf4j
@Component
public class DingTalkClient {

    private static final long ROOT_DEPT_ID = 1L;
    private static final int USER_PAGE_SIZE = 100;
    private static final long TOKEN_SKEW_MS = 120_000L;

    private final DingTalkProperties properties;
    private final ObjectMapper objectMapper;
    private final Transport transport;

    private volatile String cachedToken;
    private volatile long tokenExpiresAtMs;

    public DingTalkClient(DingTalkProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, new JdkTransport(properties.getTimeout()));
    }

    DingTalkClient(DingTalkProperties properties, ObjectMapper objectMapper, Transport transport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String getAccessToken() {
        ensureConfigured();
        long now = System.currentTimeMillis();
        String token = cachedToken;
        if (token != null && now < tokenExpiresAtMs) {
            return token;
        }
        synchronized (this) {
            if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAtMs) {
                return cachedToken;
            }
            String fetched = fetchAccessToken();
            cachedToken = fetched;
            return fetched;
        }
    }

    /**
     * 拉取全量部门（含根部门 1），BFS 递归 listsub。
     */
    public List<DingDepartment> fetchAllDepartments() {
        String token = getAccessToken();
        Map<Long, DingDepartment> byId = new LinkedHashMap<>();

        DingDepartment root = getDepartment(token, ROOT_DEPT_ID);
        if (root != null && root.getDeptId() != null) {
            byId.put(root.getDeptId(), root);
        } else {
            byId.put(ROOT_DEPT_ID, DingDepartment.builder()
                    .deptId(ROOT_DEPT_ID)
                    .parentId(null)
                    .name("根部门")
                    .order(0)
                    .build());
        }

        Deque<Long> queue = new ArrayDeque<>();
        queue.add(ROOT_DEPT_ID);
        Set<Long> visited = new HashSet<>();
        visited.add(ROOT_DEPT_ID);

        while (!queue.isEmpty()) {
            Long parentId = queue.removeFirst();
            List<DingDepartment> children = listSubDepartments(token, parentId);
            for (DingDepartment child : children) {
                if (child.getDeptId() == null || child.getDeptId() < 0) {
                    continue;
                }
                byId.put(child.getDeptId(), child);
                if (visited.add(child.getDeptId())) {
                    queue.addLast(child.getDeptId());
                }
            }
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * 按部门拉取成员（同一 userid 可能出现在多部门，调用方需去重）。
     */
    public List<DingUser> fetchUsersInDepartments(Collection<Long> deptIds) {
        String token = getAccessToken();
        List<DingUser> users = new ArrayList<>();
        if (deptIds == null) {
            return users;
        }
        for (Long deptId : deptIds) {
            if (deptId == null || deptId < 0) {
                continue;
            }
            users.addAll(listUsers(token, deptId));
        }
        return users;
    }

    DingDepartment getDepartment(String token, long deptId) {
        JsonNode root = postOapi(token, "/topapi/v2/department/get",
                Map.of("dept_id", deptId, "language", "zh_CN"));
        JsonNode result = root.path("result");
        if (result.isMissingNode() || result.isNull()) {
            return null;
        }
        return parseDepartment(result);
    }

    List<DingDepartment> listSubDepartments(String token, long deptId) {
        JsonNode root = postOapi(token, "/topapi/v2/department/listsub",
                Map.of("dept_id", deptId, "language", "zh_CN"));
        JsonNode result = root.path("result");
        List<DingDepartment> list = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode node : result) {
                DingDepartment dept = parseDepartment(node);
                if (dept.getDeptId() != null) {
                    list.add(dept);
                }
            }
        }
        return list;
    }

    List<DingUser> listUsers(String token, long deptId) {
        List<DingUser> users = new ArrayList<>();
        long cursor = 0;
        boolean hasMore = true;
        int guard = 0;
        while (hasMore && guard++ < 1000) {
            JsonNode root = postOapi(token, "/topapi/v2/user/list",
                    Map.of("dept_id", deptId, "cursor", cursor, "size", USER_PAGE_SIZE,
                            "language", "zh_CN"));
            JsonNode result = root.path("result");
            JsonNode list = result.path("list");
            if (list.isArray()) {
                for (JsonNode node : list) {
                    DingUser user = parseUser(node);
                    if (user.getUserid() != null && !user.getUserid().isBlank()) {
                        users.add(user);
                    }
                }
            }
            hasMore = result.path("has_more").asBoolean(false);
            cursor = result.path("next_cursor").asLong(0);
        }
        return users;
    }

    private String fetchAccessToken() {
        String url = properties.resolveApiBaseUrl() + "/v1.0/oauth2/accessToken";
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "appKey", properties.getAppKey(),
                    "appSecret", properties.getAppSecret()));
            String response = transport.execute("POST", url, body, Map.of(
                    "Content-Type", "application/json"));
            JsonNode root = objectMapper.readTree(response);
            String token = text(root, "accessToken");
            if (token == null || token.isBlank()) {
                token = text(root, "access_token");
            }
            if (token == null || token.isBlank()) {
                String message = firstNonBlank(text(root, "message"), text(root, "errmsg"), response);
                throw new DingTalkException("获取钉钉 accessToken 失败: " + message);
            }
            int expireIn = root.path("expireIn").asInt(root.path("expires_in").asInt(7200));
            tokenExpiresAtMs = System.currentTimeMillis() + Math.max(60, expireIn) * 1000L - TOKEN_SKEW_MS;
            log.info("钉钉 accessToken 已刷新，有效期约 {} 秒", expireIn);
            return token;
        } catch (DingTalkException e) {
            throw e;
        } catch (Exception e) {
            throw new DingTalkException("获取钉钉 accessToken 失败: " + e.getMessage(), e);
        }
    }

    private JsonNode postOapi(String token, String path, Map<String, Object> payload) {
        String encoded = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String url = properties.resolveOapiBaseUrl() + path + "?access_token=" + encoded;
        try {
            String body = objectMapper.writeValueAsString(payload);
            String response = transport.execute("POST", url, body, Map.of(
                    "Content-Type", "application/json"));
            JsonNode root = objectMapper.readTree(response);
            int errcode = root.path("errcode").asInt(0);
            if (errcode != 0) {
                throw new DingTalkException("钉钉接口 " + path + " 失败: errcode=" + errcode
                        + " errmsg=" + root.path("errmsg").asText(""));
            }
            return root;
        } catch (DingTalkException e) {
            throw e;
        } catch (Exception e) {
            throw new DingTalkException("调用钉钉接口 " + path + " 失败: " + e.getMessage(), e);
        }
    }

    static DingDepartment parseDepartment(JsonNode node) {
        Long deptId = longVal(node, "dept_id");
        if (deptId == null) {
            deptId = longVal(node, "deptId");
        }
        Long parentId = longVal(node, "parent_id");
        if (parentId == null) {
            parentId = longVal(node, "parentId");
        }
        return DingDepartment.builder()
                .deptId(deptId)
                .parentId(parentId)
                .name(firstNonBlank(text(node, "name"), "未命名部门"))
                .order(node.path("order").isMissingNode() ? 0 : node.path("order").asInt(0))
                .build();
    }

    static DingUser parseUser(JsonNode node) {
        List<Long> deptIds = new ArrayList<>();
        JsonNode deptList = node.path("dept_id_list");
        if (!deptList.isArray()) {
            deptList = node.path("deptIdList");
        }
        if (deptList.isArray()) {
            for (JsonNode idNode : deptList) {
                if (idNode.isNumber() || idNode.isTextual()) {
                    deptIds.add(idNode.asLong());
                }
            }
        }
        boolean active = true;
        if (node.has("active") && !node.get("active").isNull()) {
            active = node.get("active").asBoolean(true);
        }
        return DingUser.builder()
                .userid(firstNonBlank(text(node, "userid"), text(node, "userId")))
                .unionid(firstNonBlank(text(node, "unionid"), text(node, "unionId")))
                .name(text(node, "name"))
                .title(firstNonBlank(text(node, "title"), text(node, "job_title")))
                .mobile(text(node, "mobile"))
                .email(firstNonBlank(text(node, "email"), text(node, "org_email")))
                .avatar(text(node, "avatar"))
                .deptIdList(deptIds)
                .active(active)
                .build();
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw DingTalkException.notConfigured();
        }
    }

    private static Long longVal(JsonNode node, String field) {
        JsonNode val = node.path(field);
        if (val.isMissingNode() || val.isNull()) {
            return null;
        }
        if (val.isNumber() || val.isTextual()) {
            return val.asLong();
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode val = node.get(field);
        if (val == null || val.isNull() || val.isMissingNode()) {
            return null;
        }
        String s = val.asText();
        return s == null || s.isBlank() ? null : s;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 可替换的 HTTP 传输，便于单测 mock。
     */
    public interface Transport {
        String execute(String method, String url, String jsonBody, Map<String, String> headers) throws IOException;
    }

    static final class JdkTransport implements Transport {
        private final HttpClient httpClient;
        private final Duration timeout;

        JdkTransport(int timeoutMs) {
            this.timeout = Duration.ofMillis(Math.max(1000, timeoutMs));
            this.httpClient = HttpClient.newBuilder()
                    .connectTimeout(this.timeout)
                    .build();
        }

        @Override
        public String execute(String method, String url, String jsonBody, Map<String, String> headers) throws IOException {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(timeout);
                if (headers != null) {
                    headers.forEach(builder::header);
                }
                if ("GET".equalsIgnoreCase(method) || jsonBody == null) {
                    builder.GET();
                } else {
                    builder.method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
                }
                HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                String body = response.body() == null ? "" : response.body();
                if (response.statusCode() >= 400) {
                    throw new DingTalkException("钉钉 HTTP " + response.statusCode() + ": " + truncate(body));
                }
                return body;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("钉钉请求被中断", e);
            }
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
