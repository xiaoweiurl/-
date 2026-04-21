package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 登录响应
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 过期时间（毫秒）
     */
    private Long expiresIn;
    
    /**
     * 用户信息
     */
    private UserInfo user;
    
    /**
     * 用户信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private String id;
        private String username;
        private String email;
        private String avatar;
        private String role;
        private String membership;
    }
}
