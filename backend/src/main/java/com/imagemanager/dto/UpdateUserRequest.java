package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 更新用户请求
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 个人简介
     */
    private String bio;
    
    /**
     * 角色（admin, user）
     */
    private String role;
    
    /**
     * 会员类型（free, pro, premium）
     */
    private String membership;
    
    /**
     * 头像URL
     */
    private String avatarUrl;
}
