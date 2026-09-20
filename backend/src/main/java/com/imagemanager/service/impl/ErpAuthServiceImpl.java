package com.imagemanager.service.impl;

import com.imagemanager.config.ErpProperties;
import com.imagemanager.service.ErpAuthService;
import com.imagemanager.service.ErpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ERP 登录态管理实现
 *
 * 凭证固定配置在后端（erp.uid / erp.password / erp.custom-id），无需用户手动输入；
 * 同步流程通过 ensureToken() 自动登录换取 token。
 * token 缓存在服务内存（单实例部署足够）；演示模式下生成模拟 token，
 * 不请求真实 ERP 服务器，保证功能可完整演示。
 */
@Slf4j
@Service
public class ErpAuthServiceImpl implements ErpAuthService {

    private final ErpClient erpClient;
    private final ErpProperties erpProperties;

    /** 内存缓存的 ERP 登录态 */
    private volatile String cachedToken;
    private volatile String cachedUid;
    private volatile String loginTime;
    private volatile boolean demoToken;

    public ErpAuthServiceImpl(ErpClient erpClient, ErpProperties erpProperties) {
        this.erpClient = erpClient;
        this.erpProperties = erpProperties;
    }

    @Override
    public Map<String, Object> login(String uid, String password, String customId) {
        // 凭证固定在后端配置，入参为空时使用配置默认值（无需用户手动配置）
        String effectiveUid = (uid == null || uid.isBlank()) ? erpProperties.getUid() : uid.trim();
        String effectivePassword = (password == null || password.isBlank()) ? erpProperties.getPassword() : password;
        String effectiveCustomId = (customId == null || customId.isBlank()) ? erpProperties.getCustomId() : customId.trim();
        if (effectiveUid == null || effectiveUid.isBlank()
                || effectivePassword == null || effectivePassword.isBlank()
                || effectiveCustomId == null || effectiveCustomId.isBlank()) {
            throw new IllegalArgumentException(
                    "ERP 凭证不完整：账号、密码或账套码为空。请设置环境变量 ERP_PASSWORD（及 ERP_UID / ERP_CUSTOM_ID），或写入 application-local.yml 的 erp.password");
        }

        String token;
        boolean demo = erpProperties.isDemoEnabled();
        if (demo) {
            // 演示模式：不请求真实 ERP，生成模拟 token
            token = "demo-" + UUID.randomUUID().toString().replace("-", "");
            log.info("[ERP登录] 演示模式，账号 {} 登录成功（模拟 token）", effectiveUid);
        } else {
            try {
                token = erpClient.login(effectiveUid, effectivePassword, effectiveCustomId);
                log.info("[ERP登录] 账号 {} 登录成功", effectiveUid);
            } catch (ErpClient.ErpNetworkException e) {
                // ERP 不可达时降级为演示 token，保证演示可用
                token = "demo-" + UUID.randomUUID().toString().replace("-", "");
                demo = true;
                log.warn("[ERP登录] 真实 ERP 不可达，降级为演示模式: {}", e.getMessage());
            }
        }
        this.cachedToken = token;
        this.cachedUid = effectiveUid;
        this.loginTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.demoToken = demo;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uid", cachedUid);
        out.put("loginTime", loginTime);
        out.put("demo", demoToken);
        out.put("message", demoToken ? "演示模式登录成功（未连接真实 ERP）" : "ERP 登录成功");
        return out;
    }

    @Override
    public String getToken() {
        return cachedToken;
    }

    @Override
    public synchronized String ensureToken() {
        if (cachedToken != null && !cachedToken.isBlank()) {
            return cachedToken;
        }
        // 无缓存 token：使用后端固定凭证自动登录（无需用户手动操作）
        login(null, null, null);
        return cachedToken;
    }

    @Override
    public void clearToken() {
        this.cachedToken = null;
        this.cachedUid = null;
        this.loginTime = null;
        this.demoToken = false;
    }

    @Override
    public Map<String, Object> getAuthState() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("loggedIn", cachedToken != null);
        out.put("uid", cachedUid);
        out.put("loginTime", loginTime);
        out.put("demo", demoToken);
        out.put("demoEnabled", erpProperties.isDemoEnabled());
        out.put("baseUrl", erpProperties.getBaseUrl());
        out.put("customId", erpProperties.getCustomId());
        return out;
    }

    @Override
    public void logout() {
        clearToken();
    }
}
