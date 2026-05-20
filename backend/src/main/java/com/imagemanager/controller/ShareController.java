package com.imagemanager.controller;

import com.imagemanager.dto.*;
import com.imagemanager.service.ShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    /**
     * 创建分享链接
     */
    @PostMapping
    public ResponseEntity<ShareLinkDTO> createShare(
            @Valid @RequestBody CreateShareRequest request,
            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        String baseUrl = getBaseUrl(httpRequest);
        ShareLinkDTO share = shareService.createShare(request, userId, baseUrl);
        return ResponseEntity.ok(share);
    }

    /**
     * 获取我的分享列表
     */
    @GetMapping("/my")
    public ResponseEntity<Page<ShareLinkDTO>> getMyShares(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        // 调用接口中定义的方法
        Page<ShareLinkDTO> shares = shareService.getUserShares(userId, resourceType, page, pageSize);
        return ResponseEntity.ok(shares);
    }

    /**
     * 获取分享详情（通过ID）
     */
    @GetMapping("/{id}")
    public ResponseEntity<ShareLinkDTO> getShareById(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        // 调用接口中定义的方法
        ShareLinkDTO share = shareService.getShareDetail(id, userId);
        return ResponseEntity.ok(share);
    }

    /**
     * 通过 shareCode 删除分享
     */
    @DeleteMapping("/code/{shareCode}")
    public ResponseEntity<Void> deleteShareByCode(
            @PathVariable String shareCode,
            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        shareService.deleteShareByCode(shareCode, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 通过 ID 删除分享
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShare(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        shareService.deleteShare(id, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 公开访问分享链接
     */
    @GetMapping("/access/{shareCode}")
    public ResponseEntity<Map<String, Object>> accessShare(
            @PathVariable String shareCode,
            @RequestParam(required = false) String password,
            HttpServletRequest httpRequest) {
        String visitorIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String referer = httpRequest.getHeader("Referer");
        // 调用接口中定义的方法
        Map<String, Object> result = shareService.accessShare(shareCode, password, visitorIp, userAgent, referer);
        return ResponseEntity.ok(result);
    }

    /**
     * 验证分享密码
     */
    @PostMapping("/access")
    public ResponseEntity<Map<String, Object>> verifySharePassword(
            @RequestBody AccessShareRequest request,
            HttpServletRequest httpRequest) {
        String visitorIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String referer = httpRequest.getHeader("Referer");
        // 调用接口中定义的方法
        Map<String, Object> result = shareService.accessShare(request.getShareCode(), request.getPassword(), visitorIp, userAgent, referer);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取资源分享统计
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getShareStats(
            @RequestParam(required = false) String resourceId,
            HttpServletRequest httpRequest) {
        if (resourceId != null && !resourceId.isEmpty()) {
            // 调用接口中定义的方法
            Map<String, Object> stats = shareService.getResourceShareStats(resourceId);
            return ResponseEntity.ok(stats);
        }
        return ResponseEntity.ok(Map.of("message", "请提供 resourceId"));
    }

    /**
     * 获取分享访问统计
     */
    @GetMapping("/{id}/access-stats")
    public ResponseEntity<Map<String, Object>> getAccessStats(
            @PathVariable String id,
            @RequestParam(defaultValue = "week") String period,
            HttpServletRequest httpRequest) {
        // 调用接口中定义的方法
        Map<String, Object> stats = shareService.getShareAccessStats(id, period);
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取基础URL
     */
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        
        StringBuilder baseUrl = new StringBuilder();
        baseUrl.append(scheme).append("://").append(serverName);
        
        if ((scheme.equals("http") && serverPort != 80) || 
            (scheme.equals("https") && serverPort != 443)) {
            baseUrl.append(":").append(serverPort);
        }
        
        return baseUrl.toString();
    }
}
