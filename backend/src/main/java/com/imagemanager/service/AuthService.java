package com.imagemanager.service;

import com.imagemanager.dto.LoginRequest;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.dto.RegisterRequest;
import com.imagemanager.dto.UpdateProfileRequest;
import com.imagemanager.dto.UserSettings;
import com.imagemanager.entity.User;

/**
 * 认证服务接口
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
public interface AuthService {
    
    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);

    /**
     * 为已存在的本地用户签发会话（钉钉免登等）。{@code forceKick=true} 时踢掉旧会话，避免 409。
     */
    LoginResponse issueSession(User user, boolean rememberMe, boolean forceKick);

    /**
     * 通讯录成员尚未注册中台账号时，签发仅能填写指定商品打样表单的短会话。
     */
    LoginResponse issueSamplerSession(String dingUserId, String displayName, String company, long goodsId);
    
    /**
     * 用户注册
     */
    LoginResponse register(RegisterRequest request);
    
    /**
     * 用户登出
     */
    void logout(String sessionId);
    
    /**
     * 验证会话
     */
    LoginResponse.UserInfo validateSession(String sessionId);
    
    /**
     * 更新用户资料
     */
    void updateProfile(String userId, UpdateProfileRequest request);
    
    /**
     * 修改密码
     */
    void changePassword(String userId, String currentPassword, String newPassword);
    
    /**
     * 获取用户设置
     */
    UserSettings getUserSettings(String userId);
    
    /**
     * 更新用户设置
     */
    void updateUserSettings(String userId, UserSettings settings);

    /**
     * 删除用户所有会话
     */
    void deleteAllUserSessions(String userId);

    /**
     * 绑定公司到用户（仅首次，已绑定不可更改）
     * @return true=绑定成功, false=已绑定不可更改
     */
    boolean bindCompany(String userId, String company);
}
