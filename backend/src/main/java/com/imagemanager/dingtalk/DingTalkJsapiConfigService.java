package com.imagemanager.dingtalk;

import com.imagemanager.config.DingTalkProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 钉钉 H5 {@code dd.config} 参数（corpId / agentId / clientId=AppKey / 签名）。不暴露 AppSecret。
 */
@Slf4j
@Service
public class DingTalkJsapiConfigService {

    private final DingTalkClient dingTalkClient;
    private final DingTalkProperties properties;
    private final String frontendUrl;

    public DingTalkJsapiConfigService(DingTalkClient dingTalkClient,
                                      DingTalkProperties properties,
                                      @Value("${app.frontend.url:http://localhost:5000}") String frontendUrl) {
        this.dingTalkClient = dingTalkClient;
        this.properties = properties;
        this.frontendUrl = frontendUrl;
    }

    public Map<String, Object> build(String pageUrl) {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean configured = properties.isConfigured();
        data.put("configured", configured);
        data.put("corpId", properties.getCorpId() == null ? "" : properties.getCorpId().trim());
        Long agentId = properties.resolveAgentId();
        data.put("agentId", agentId == null ? "" : String.valueOf(agentId));
        data.put("clientId", properties.getAppKey() == null ? "" : properties.getAppKey().trim());
        if (configured && !properties.hasCorpId()) {
            log.warn("钉钉 JSAPI 配置 corpId 为空（未设置 DINGTALK_CORP_ID）。"
                    + "H5 免登无法换取有效 authCode；工作通知也无法包 dingtalk://openapp。");
        }
        if (!configured) {
            return data;
        }
        String url = stripHash(pageUrl);
        if (url == null || !isAllowedPageUrl(url)) {
            return data;
        }
        String ticket = dingTalkClient.getJsapiTicket();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        if (nonce.length() > 16) {
            nonce = nonce.substring(0, 16);
        }
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        data.put("nonceStr", nonce);
        data.put("timeStamp", timestamp);
        data.put("signature", DingTalkJsapiSign.sign(ticket, nonce, timestamp, url));
        data.put("url", url);
        return data;
    }

    static String stripHash(String pageUrl) {
        if (pageUrl == null || pageUrl.isBlank()) {
            return null;
        }
        String trimmed = pageUrl.trim();
        int hash = trimmed.indexOf('#');
        return hash >= 0 ? trimmed.substring(0, hash) : trimmed;
    }

    boolean isAllowedPageUrl(String url) {
        URI page;
        try {
            page = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String scheme = page.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            return false;
        }
        String host = page.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)) {
            return true;
        }
        String frontendHost = frontendHost();
        return frontendHost != null && frontendHost.equalsIgnoreCase(host);
    }

    private String frontendHost() {
        if (frontendUrl == null || frontendUrl.isBlank()) {
            return null;
        }
        try {
            return URI.create(frontendUrl.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
