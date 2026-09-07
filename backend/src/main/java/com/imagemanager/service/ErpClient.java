package com.imagemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.config.ErpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * ERP 系统 HTTP 客户端（统一请求拦截器）
 *
 * 职责：
 * - 基地址从 ErpProperties 读取（全局可配置常量，禁止硬编码）
 * - 所有业务请求 Header 自动携带 Authorization: Bearer {token}
 * - 统一解析 ERP 响应包裹 {code, message, result}：
 *     code=1 成功 / 0 失败 / -100 鉴权失败 / -101 授权超时 / -200 无效账套码
 * - 401 / -100 / -101 统一抛出 ErpAuthException（调用方据此清除 token 并要求重新登录）
 * - 网络不可达抛出 ErpNetworkException（同步服务据此降级为演示数据）
 */
@Slf4j
@Component
public class ErpClient {

    /** ERP 鉴权失败（token 无效/过期，需重新登录） */
    public static class ErpAuthException extends RuntimeException {
        public ErpAuthException(String message) { super(message); }
    }

    /** ERP 网络不可达（连接超时/拒绝/DNS 失败） */
    public static class ErpNetworkException extends RuntimeException {
        public ErpNetworkException(String message, Throwable cause) { super(message, cause); }
    }

    /** ERP 业务失败（code=0 或 -200） */
    public static class ErpBizException extends RuntimeException {
        public ErpBizException(String message) { super(message); }
    }

    private final ErpProperties erpProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ErpClient(ErpProperties erpProperties) {
        this.erpProperties = erpProperties;
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(erpProperties.getTimeout());
        factory.setReadTimeout(erpProperties.getTimeout());
        return new RestTemplate(factory);
    }

    /**
     * ERP 登录（独立登录地址，账号密码换 token）
     *
     * @param uid      ERP 账号
     * @param password ERP 密码
     * @param customId 账套码（为空时用配置默认值）
     * @return token 字符串
     */
    public String login(String uid, String password, String customId) {
        String cid = (customId == null || customId.isBlank()) ? erpProperties.getCustomId() : customId;
        String url = erpProperties.resolveLoginUrl()
                + "?uid=" + encode(uid)
                + "&password=" + encode(password)
                + "&customId=" + encode(cid);
        JsonNode body = executeGet(url, null);
        int code = body.path("code").asInt(0);
        if (code == 1) {
            String token = body.path("result").asText(null);
            if (token == null || token.isBlank()) {
                throw new ErpBizException("ERP 登录成功但未返回 token");
            }
            return token;
        }
        String message = body.path("message").asText("登录失败");
        if (code == -100 || code == -101) {
            throw new ErpAuthException("ERP 登录鉴权失败: " + message);
        }
        throw new ErpBizException("ERP 登录失败: " + message);
    }

    /**
     * 调用业务接口（统一前缀拼接 + Bearer token 注入）
     *
     * @param relativePath 业务接口相对路径（如 Technology/NGyMainQuery.aspx）
     * @param params       查询参数
     * @param token        ERP token（Header: Authorization: Bearer {token}）
     * @return result 节点（通常为数组）
     */
    public JsonNode callApi(String relativePath, Map<String, String> params, String token) {
        if (token == null || token.isBlank()) {
            throw new ErpAuthException("ERP 未登录，缺少 token");
        }
        StringBuilder url = new StringBuilder(erpProperties.resolveApiUrl(relativePath));
        if (params != null && !params.isEmpty()) {
            url.append("?");
            params.forEach((k, v) -> {
                if (v != null) {
                    url.append(encode(k)).append("=").append(encode(v)).append("&");
                }
            });
            url.setLength(url.length() - 1);
        }
        JsonNode body = executeGet(url.toString(), token);
        int code = body.path("code").asInt(0);
        if (code == 1) {
            return body.path("result");
        }
        String message = body.path("message").asText("请求失败");
        if (code == -100 || code == -101) {
            // 鉴权失败/授权超时：token 失效，需重新登录
            throw new ErpAuthException("ERP 授权已失效: " + message);
        }
        if (code == -200) {
            throw new ErpBizException("无效账套码: " + message);
        }
        throw new ErpBizException("ERP 接口返回失败: " + message);
    }

    /**
     * 执行 GET 请求（拦截器：token 注入 / 401 处理 / 网络异常分类）
     */
    private JsonNode executeGet(String url, String token) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            if (token != null && !token.isBlank()) {
                // 所有业务接口 Header 携带 Authorization: Bearer {token}
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            }
            ResponseEntity<String> resp = buildRestTemplate()
                    .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (resp.getStatusCode().value() == 401) {
                throw new ErpAuthException("ERP 返回 401 未授权，请重新登录");
            }
            String bodyText = resp.getBody();
            if (bodyText == null || bodyText.isBlank()) {
                throw new ErpBizException("ERP 返回空响应");
            }
            return objectMapper.readTree(bodyText);
        } catch (ErpAuthException | ErpBizException e) {
            throw e;
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401) {
                throw new ErpAuthException("ERP 返回 401 未授权，请重新登录");
            }
            throw new ErpBizException("ERP HTTP 错误: " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            // 连接超时/拒绝/不可达
            throw new ErpNetworkException("ERP 服务器不可达: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ErpBizException("ERP 响应解析失败: " + e.getMessage());
        }
    }

    private String encode(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
}
