package com.imagemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册请求。钉钉路径以姓名为主，不要求用户名/邮箱/密码。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    /**
     * 姓名（钉钉通讯录匹配主键）
     */
    private String name;

    /**
     * 用户名（兼容旧客户端；钉钉路径可空，默认用姓名）
     */
    private String username;

    /**
     * 密码（钉钉路径忽略，固定为 123456）
     */
    private String password;

    /**
     * 邮箱（钉钉路径不要求）
     */
    private String email;

    /**
     * 所属公司（默认宝娜斯集团）
     */
    private String company;

    /**
     * 同名多人时用户选定的钉钉 userid
     */
    private String dingtalkUserid;
}
