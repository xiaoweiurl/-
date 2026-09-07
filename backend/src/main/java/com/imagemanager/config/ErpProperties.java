package com.imagemanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部 ERP 系统接口配置（数据同步）
 *
 * 地址规则（按接口文档）：
 * - 登录接口：独立地址（erp.login-url），传入账号密码换取 token；为空时默认 base-url + login-path
 * - 业务接口：统一前缀 erp.base-url，所有业务接口路径均在此前缀后拼接
 * 基地址集中在 application.yml 的 erp.* 配置（可用环境变量覆盖），禁止硬编码到每个请求中。
 */
@Data
@Component
@ConfigurationProperties(prefix = "erp")
public class ErpProperties {

    /** 业务接口统一前缀，如 http://mpro42.ywhzsoft.com/netWf2024Unitive_Ent */
    private String baseUrl = "http://mpro42.ywhzsoft.com/netWf2024Unitive_Ent";

    /** 登录接口独立完整地址；为空时使用 baseUrl + loginPath */
    private String loginUrl = "";

    /** 登录接口相对路径（login-url 为空时拼接使用） */
    private String loginPath = "/Auth/checkLogin.aspx";

    /** 默认账套码（登录时前端可覆盖） */
    private String customId = "8D7C1BDE-C05F-4A11-BD96-D71D94D35633";

    /** 请求超时（毫秒） */
    private int timeout = 10000;

    /** 演示模式：true 时不请求真实 ERP，使用内置模拟数据 */
    private boolean demoEnabled = true;

    /**
     * 解析登录接口完整地址
     */
    public String resolveLoginUrl() {
        if (loginUrl != null && !loginUrl.isBlank()) {
            return loginUrl;
        }
        return stripTrailingSlash(baseUrl) + loginPath;
    }

    /**
     * 拼接业务接口完整地址（统一前缀 + 相对路径）
     */
    public String resolveApiUrl(String relativePath) {
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        return stripTrailingSlash(baseUrl) + path;
    }

    private String stripTrailingSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
