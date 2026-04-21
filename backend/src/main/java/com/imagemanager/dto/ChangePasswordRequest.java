package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 修改密码请求
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {
    
    /**
     * 当前密码
     */
    private String currentPassword;
    
    /**
     * 新密码
     */
    private String newPassword;
    
    /**
     * 确认新密码
     */
    private String confirmPassword;
}
