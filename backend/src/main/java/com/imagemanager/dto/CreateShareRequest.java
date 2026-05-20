package com.imagemanager.dto;

import lombok.Data;

/**
 * 创建分享链接请求
 */
@Data
public class CreateShareRequest {
    private String resourceType; // album, image, document
    private String resourceId;
    private String password; // 可选
    private Integer expireDays = 7; // 过期天数，默认7天
    private Integer maxViews = -1; // 最大访问次数，-1表示无限制
}
