package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 注册请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 密码
     */
    private String password;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 所属公司（宝娜斯/盈云）
     */
    private String company;
}
