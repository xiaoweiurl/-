package com.imagemanager.service;

import java.util.Map;

/**
 * ERP 登录态管理（token 获取/缓存/失效清理）
 */
public interface ErpAuthService {

    /**
     * ERP 登录（账号密码换 token），成功后缓存 token
     *
     * @param uid      ERP 账号
     * @param password ERP 密码
     * @param customId 账套码（为空用配置默认值）
     * @return 登录态信息（token 脱敏返回）
     */
    Map<String, Object> login(String uid, String password, String customId);

    /**
     * 获取当前有效 token（无则 null）
     */
    String getToken();

    /**
     * 清除 token（401 / 授权失效时调用，要求重新登录）
     */
    void clearToken();

    /**
     * 当前登录态（uid/登录时间/是否已登录，token 不返回）
     */
    Map<String, Object> getAuthState();

    /**
     * 登出
     */
    void logout();
}
