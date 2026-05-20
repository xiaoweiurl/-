package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.*;
import com.imagemanager.entity.*;
import com.imagemanager.repository.*;
import com.imagemanager.service.ShareService;
import com.imagemanager.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareLinkRepository shareLinkRepository;
    private final ShareAccessLogRepository shareAccessLogRepository;
    private final AlbumRepository albumRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    @Transactional
    public ShareLinkDTO createShare(CreateShareRequest request, String userId, String baseUrl) {
        // 验证资源存在
        String resourceName = getResourceName(request.getResourceType(), request.getResourceId());
        if (resourceName == null) {
            throw new RuntimeException("资源不存在");
        }

        // 检查用户是否已有该资源的分享链接
        List<ShareLink> existingLinks = shareLinkRepository.findByResourceIdAndDeletedFalse(request.getResourceId());
        for (ShareLink link : existingLinks) {
            if (link.getCreatedBy().equals(userId) && !link.isExpired()) {
                // 返回已存在的链接
                return toDTO(link, baseUrl);
            }
        }

        ShareLink shareLink = new ShareLink();
        shareLink.setResourceType(request.getResourceType());
        shareLink.setResourceId(request.getResourceId());
        shareLink.setShareCode(generateShareCode());
        shareLink.setPassword(request.getPassword());
        shareLink.setMaxViews(request.getMaxViews());

        // 设置过期时间
        if (request.getExpireDays() != null && request.getExpireDays() > 0) {
            shareLink.setExpireAt(LocalDateTime.now().plusDays(request.getExpireDays()));
        }

        shareLink.setCreatedBy(userId);
        shareLinkRepository.save(shareLink);

        // 记录审计日志
        auditService.log(AuditLog.ActionType.CREATE_SHARE, request.getResourceType(), 
                request.getResourceId(), resourceName, null, userId);

        return toDTO(shareLink, baseUrl);
    }

    @Override
    @Transactional
    public Map<String, Object> accessShare(String shareCode, String password, String ip, String userAgent, String referer) {
        Map<String, Object> result = new HashMap<>();

        ShareLink shareLink = shareLinkRepository.findByShareCodeAndDeletedFalse(shareCode)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));

        // 检查是否过期
        if (shareLink.isExpired()) {
            result.put("error", "分享链接已过期");
            return result;
        }

        // 检查访问次数限制
        if (shareLink.isViewLimitReached()) {
            result.put("error", "分享链接访问次数已达上限");
            return result;
        }

        // 检查密码
        if (shareLink.isPasswordProtected()) {
            if (password == null || !password.equals(shareLink.getPassword())) {
                result.put("requirePassword", true);
                result.put("error", "请输入正确的访问密码");
                return result;
            }
        }

        // 记录访问
        ShareAccessLog accessLog = new ShareAccessLog();
        accessLog.setShareLinkId(shareLink.getId());
        accessLog.setAction("view");
        accessLog.setIpAddress(ip);
        accessLog.setUserAgent(userAgent);
        accessLog.setReferer(referer);
        shareAccessLogRepository.save(accessLog);

        // 更新访问计数
        shareLink.setViewCount(shareLink.getViewCount() + 1);
        shareLinkRepository.save(shareLink);

        // 获取资源内容
        Object resourceContent = getResourceContent(shareLink.getResourceType(), shareLink.getResourceId());
        
        result.put("success", true);
        result.put("resourceType", shareLink.getResourceType());
        result.put("resourceContent", resourceContent);
        result.put("shareLinkId", shareLink.getId());

        return result;
    }

    @Override
    public ShareLinkDTO getShareDetail(String shareLinkId, String userId) {
        ShareLink shareLink = shareLinkRepository.findById(shareLinkId)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));

        // 检查权限
        if (!shareLink.getCreatedBy().equals(userId)) {
            throw new RuntimeException("无权访问此分享链接");
        }

        return toDTO(shareLink, null);
    }

    @Override
    public Page<ShareLinkDTO> getUserShares(String userId, String resourceType, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<ShareLink> shares;

        if (resourceType != null && !resourceType.isEmpty()) {
            shares = shareLinkRepository.findByResourceTypeAndCreatedByAndDeletedFalse(resourceType, userId, pageable);
        } else {
            shares = shareLinkRepository.findByCreatedByOrderByCreatedAtDesc(userId, pageable);
        }

        return shares.map(share -> toDTO(share, null));
    }

    @Override
    @Transactional
    public void deleteShare(String shareLinkId, String userId) {
        ShareLink shareLink = shareLinkRepository.findById(shareLinkId)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));

        if (!shareLink.getCreatedBy().equals(userId)) {
            throw new RuntimeException("无权删除此分享链接");
        }

        shareLink.setDeleted(true);
        shareLinkRepository.save(shareLink);
    }

    @Override
    public Map<String, Object> getResourceShareStats(String resourceId) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("shareCount", shareLinkRepository.countByResourceId(resourceId));
        stats.put("totalViews", shareLinkRepository.sumViewCountByResourceId(resourceId));
        stats.put("totalDownloads", shareLinkRepository.sumDownloadCountByResourceId(resourceId));
        
        return stats;
    }

    @Override
    public Map<String, Object> getShareAccessStats(String shareLinkId, String period) {
        Map<String, Object> stats = new HashMap<>();
        
        LocalDateTime since;
        switch (period != null ? period : "week") {
            case "day":
                since = LocalDateTime.now().minusDays(1);
                break;
            case "month":
                since = LocalDateTime.now().minusMonths(1);
                break;
            default:
                since = LocalDateTime.now().minusWeeks(1);
        }

        stats.put("dailyAccess", shareAccessLogRepository.getDailyAccessStats(shareLinkId, since));
        stats.put("topIpAddresses", shareAccessLogRepository.getTopIpAddresses(shareLinkId, PageRequest.of(0, 10)));
        stats.put("totalViews", shareAccessLogRepository.countByShareLinkIdAndAction(shareLinkId, "view"));
        stats.put("totalDownloads", shareAccessLogRepository.countByShareLinkIdAndAction(shareLinkId, "download"));

        return stats;
    }

    @Override
    public String generateShareCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        // 确保唯一
        while (shareLinkRepository.findByShareCodeAndDeletedFalse(code.toString()).isPresent()) {
            code = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                code.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
            }
        }
        return code.toString();
    }

    @Override
    public boolean isShareValid(ShareLink shareLink) {
        return !shareLink.getDeleted() && !shareLink.isExpired() && !shareLink.isViewLimitReached();
    }

    private String getResourceName(String resourceType, String resourceId) {
        return switch (resourceType) {
            case "album" -> albumRepository.findById(resourceId).map(Album::getName).orElse(null);
            case "image" -> imageRepository.findById(resourceId).map(Image::getTitle).orElse(null);
            default -> null;
        };
    }

    private Object getResourceContent(String resourceType, String resourceId) {
        return switch (resourceType) {
            case "album" -> {
                Album album = albumRepository.findById(resourceId).orElse(null);
                if (album != null) {
                    Map<String, Object> content = new HashMap<>();
                    content.put("album", album);
                    content.put("images", imageRepository.findMainImagesByAlbumIdAndDeletedFalse(resourceId));
                    yield content;
                }
                yield null;
            }
            case "image" -> imageRepository.findById(resourceId).orElse(null);
            default -> null;
        };
    }

    private ShareLinkDTO toDTO(ShareLink shareLink, String baseUrl) {
        ShareLinkDTO dto = new ShareLinkDTO();
        dto.setId(shareLink.getId());
        dto.setResourceType(shareLink.getResourceType());
        dto.setResourceId(shareLink.getResourceId());
        dto.setResourceName(getResourceName(shareLink.getResourceType(), shareLink.getResourceId()));
        dto.setShareCode(shareLink.getShareCode());
        dto.setPassword(shareLink.getPassword() != null ? "******" : null);
        dto.setExpireAt(shareLink.getExpireAt());
        dto.setMaxViews(shareLink.getMaxViews());
        dto.setViewCount(shareLink.getViewCount());
        dto.setDownloadCount(shareLink.getDownloadCount());
        dto.setCreatedBy(shareLink.getCreatedBy());
        dto.setCreatedAt(shareLink.getCreatedAt());
        dto.setIsExpired(shareLink.isExpired());
        dto.setHasPassword(shareLink.isPasswordProtected());
        dto.setIsActive(isShareValid(shareLink));

        // 生成分享URL
        if (baseUrl != null) {
            dto.setShareUrl(baseUrl + "/share/" + shareLink.getShareCode());
        }

        // 获取创建者名称
        userRepository.findById(shareLink.getCreatedBy())
                .ifPresent(user -> dto.setCreatedByName(user.getUsername()));

        return dto;
    }
}
