package com.imagemanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 钉钉企业内部应用配置。
 * 凭证通过环境变量注入；未配置时应用仍可启动，同步接口返回明确错误。
 */
@Data
@Component
@ConfigurationProperties(prefix = "dingtalk")
public class DingTalkProperties {

    /** 新 OpenAPI 网关，用于获取 accessToken */
    private String apiBaseUrl = "https://api.dingtalk.com";

    /** 旧 oapi 网关，通讯录 topapi v2 仍走此域名 */
    private String oapiBaseUrl = "https://oapi.dingtalk.com";

    /** 企业内部应用 AppKey（DINGTALK_APP_KEY） */
    private String appKey = "";

    /** 企业内部应用 AppSecret（DINGTALK_APP_SECRET） */
    private String appSecret = "";

    /** 应用 AgentId（DINGTALK_AGENT_ID），工作通知必填；未配置则工作通知功能关闭 */
    private String agentId = "";

    /** 企业 corpId，可选 */
    private String corpId = "";

    /**
     * 工作通知按钮是否包一层 {@code dingtalk://}（openapp / page/link）。
     * 默认 false：{@code single_url} 直接用 HTTP(S) 表单地址，避免未配安全域名时
     * 「后续页面非钉钉提供」。仅当同时配置了 corpId 时包装才生效。
     */
    private boolean workNoticeProtocolLinks = false;

    /** 请求超时（毫秒） */
    private int timeout = 10000;

    /** 同步写入的公司标识，与 users.company 对齐 */
    private String company = "宝娜斯集团";

    public boolean isConfigured() {
        return appKey != null && !appKey.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }

    /** AgentId 已配置（非空且可解析为数字）。 */
    public boolean hasAgentId() {
        return resolveAgentId() != null;
    }

    /**
     * 是否用 {@code dingtalk://} 包装工作通知链接：开关打开且 corpId 非空。
     * 打开后须把 FRONTEND_URL 主机写入该应用的 H5 可信域名 / 安全域名。
     */
    public boolean shouldWrapWorkNoticeProtocolLinks() {
        return workNoticeProtocolLinks && corpId != null && !corpId.isBlank();
    }

    /**
     * 工作通知是否启用：AppKey/Secret + AgentId 均已配置。
     * 未启用时应用仍可启动，发送路径记录日志后跳过，不抛异常。
     */
    public boolean isWorkNoticeEnabled() {
        return isConfigured() && hasAgentId();
    }

    /**
     * 解析 AgentId。钉钉接口要求数字；空白或非数字返回 null（功能关闭）。
     */
    public Long resolveAgentId() {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(agentId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String resolveApiBaseUrl() {
        return stripTrailingSlash(apiBaseUrl);
    }

    public String resolveOapiBaseUrl() {
        return stripTrailingSlash(oapiBaseUrl);
    }

    private String stripTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
