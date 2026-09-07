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
        if (uid == null || uid.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("ERP 账号与密码不能为空");
        }
        String token;
        boolean demo = erpProperties.isDemoEnabled();
        if (demo) {
            // 演示模式：不请求真实 ERP，生成模拟 token
            token = "demo-" + UUID.randomUUID().toString().replace("-", "");
            log.info("[ERP登录] 演示模式，账号 {} 登录成功（模拟 token）", uid);
        } else {
            try {
                token = erpClient.login(uid.trim(), password, customId);
            } catch (ErpClient.ErpNetworkException e) {
                // ERP 不可达时降级为演示 token，保证演示可用
                token = "demo-" + UUID.randomUUID().toString().replace("-", "");
                demo = true;
                log.warn("[ERP登录] 真实 ERP 不可达，降级为演示模式: {}", e.getMessage());
            }
        }
        this.cachedToken = token;
        this.cachedUid = uid.trim();
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
        log.info("[ERP登录] 已登出");
    }
}
