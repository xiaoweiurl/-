package com.imagemanager.service;

import com.imagemanager.dto.*;
import com.imagemanager.entity.ShareLink;
import org.springframework.data.domain.Page;

import java.util.Map;

/**
 * 分享服务接口
 */
public interface ShareService {
    
    /**
     * 创建分享链接
     */
    ShareLinkDTO createShare(CreateShareRequest request, String userId, String baseUrl);
    
    /**
     * 访问分享链接
     */
    Map<String, Object> accessShare(String shareCode, String password, String ip, String userAgent, String referer);
    
    /**
     * 获取分享链接详情
     */
    ShareLinkDTO getShareDetail(String shareLinkId, String userId);
    
    /**
     * 获取用户的分享链接列表
     */
    Page<ShareLinkDTO> getUserShares(String userId, String resourceType, int page, int pageSize);
    
    /**
     * 删除分享链接
     */
    void deleteShare(String shareLinkId, String userId);
    
    /**
     * 获取资源的分享统计
     */
    Map<String, Object> getResourceShareStats(String resourceId);
    
    /**
     * 获取分享链接的访问统计
     */
    Map<String, Object> getShareAccessStats(String shareLinkId, String period);
    
    /**
     * 生成分享码
     */
    String generateShareCode();
    
    /**
     * 检查分享链接是否有效
     */
    boolean isShareValid(ShareLink shareLink);
}
