package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 登录请求
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 记住我
     */
    private Boolean rememberMe;

    /**
     * 所属公司（系统统一为宝娜斯，此字段忽略）
     */
    private String company;

    /**
     * 强制登录（踢掉已有会话）
     */
    private Boolean forceLogin;
}
