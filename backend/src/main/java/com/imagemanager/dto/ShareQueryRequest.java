package com.imagemanager.dto;

import lombok.Data;

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
