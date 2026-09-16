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

    /** 应用 AgentId，Phase 2 工作通知使用；Phase 1 仅保存 */
    private String agentId = "";

    /** 企业 corpId，可选 */
    private String corpId = "";

    /** 请求超时（毫秒） */
    private int timeout = 10000;

    /** 同步写入的公司标识，与 users.company 对齐 */
    private String company = "宝娜斯集团";

    public boolean isConfigured() {
        return appKey != null && !appKey.isBlank()
                && appSecret != null && !appSecret.isBlank();
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
