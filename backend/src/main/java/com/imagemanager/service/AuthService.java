package com.imagemanager.service;

import com.imagemanager.dto.LoginRequest;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.dto.RegisterRequest;
import com.imagemanager.dto.UpdateProfileRequest;
import com.imagemanager.dto.UserSettings;

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
