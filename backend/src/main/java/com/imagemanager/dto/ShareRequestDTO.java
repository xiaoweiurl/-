package com.imagemanager.dto;

import lombok.Data;
import java.time.LocalDateTime;

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

/**
 * 访问分享链接请求
 */
@Data
public class AccessShareRequest {
    private String shareCode;
    private String password; // 如果有密码保护
}

/**
 * 分享链接查询请求
 */
@Data
public class ShareQueryRequest {
    private String userId;
    private String resourceType;
    private String resourceId;
    private Boolean includeExpired = false;
    private Integer page = 1;
    private Integer pageSize = 20;
}

/**
 * 分享统计请求
 */
@Data
public class ShareStatsRequest {
    private String shareLinkId;
    private String period; // day, week, month
}
