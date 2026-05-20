package com.imagemanager.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分享链接DTO
 */
@Data
public class ShareLinkDTO {
    private String id;
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String shareCode;
    private String shareUrl;
    private String password;
    private LocalDateTime expireAt;
    private Integer maxViews;
    private Integer viewCount;
    private Integer downloadCount;
    private String createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private Boolean isExpired;
    private Boolean hasPassword;
    private Boolean isActive;
}
