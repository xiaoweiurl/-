package com.imagemanager.dto;

import lombok.Data;

/**
 * 访问分享链接请求
 */
@Data
public class AccessShareRequest {
    private String shareCode;
    private String password; // 如果有密码保护
}
