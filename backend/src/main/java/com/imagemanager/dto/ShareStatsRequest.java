package com.imagemanager.dto;

import lombok.Data;

/**
 * 分享统计请求
 */
@Data
public class ShareStatsRequest {
    private String shareLinkId;
    private String period; // day, week, month
}
