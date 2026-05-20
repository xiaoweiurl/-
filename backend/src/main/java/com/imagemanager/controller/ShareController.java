package com.imagemanager.controller;

import com.imagemanager.dto.*;
import com.imagemanager.service.ShareService;
import com.imagemanager.service.AuditService;
import com.imagemanager.entity.User;
import com.imagemanager.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 分享控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final AuditService auditService;
    private final UserRepository userRepository;

    /**
     * 创建分享链接
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createShare(
            @RequestBody CreateShareRequest request,
            HttpSession session,
            HttpServletRequest httpRequest) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String userId = (String) session.getAttribute("userId");
            if (userId == null) {
                result.put("error", "未登录");
                return ResponseEntity.status(401).body(result);
            }

            String baseUrl = getBaseUrl(httpRequest);
            ShareLinkDTO shareLink = shareService.createShare(request, userId, baseUrl);
            
            result.put("success", true);
            result.put("data", shareLink);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Create share failed", e);
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 访问分享链接 (POST方式)
     */
    @PostMapping("/access")
    public ResponseEntity<Map<String, Object>> accessShare(
            @RequestBody AccessShareRequest request,
            HttpServletRequest httpRequest) {
        
        try {
            String ip = getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            String referer = httpRequest.getHeader("Referer");
            
            Map<String, Object> result = shareService.accessShare(
                    request.getShareCode(), 
                    request.getPassword(), 
                    ip, userAgent, referer);
            
            if (result.containsKey("error")) {
                return ResponseEntity.badRequest().body(result);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Access share failed", e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 访问分享链接 (GET方式，用于公开访问页面)
     */
    @GetMapping("/access/{shareCode}")
    public ResponseEntity<Map<String, Object>> accessShareByCode(
            @PathVariable String shareCode,
            @RequestParam(required = false) String password,
            HttpServletRequest httpRequest) {
        
        try {
            String ip = getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            String referer = httpRequest.getHeader("Referer");
            
            Map<String, Object> result = shareService.accessShare(
                    shareCode, 
                    password != null ? password : "", 
                    ip, userAgent, referer);
            
            if (result.containsKey("error")) {
                if (result.get("error").equals("需要密码")) {
                    result.put("needPassword", true);
                }
                return ResponseEntity.badRequest().body(result);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Access share failed", e);
            Map<String, Object> result = new HashMap<>();
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取分享链接详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getShareDetail(
            @PathVariable String id,
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String userId = (String) session.getAttribute("userId");
            if (userId == null) {
                result.put("error", "未登录");
                return ResponseEntity.status(401).body(result);
            }

            ShareLinkDTO shareLink = shareService.getShareDetail(id, userId);
            
            result.put("success", true);
            result.put("data", shareLink);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Get share detail failed", e);
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取用户的分享链接列表
     */
    @GetMapping("/my")
    public ResponseEntity<Map<String, Object>> getMyShares(
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String userId = (String) session.getAttribute("userId");
            if (userId == null) {
                result.put("error", "未登录");
                return ResponseEntity.status(401).body(result);
            }

            Page<ShareLinkDTO> shares = shareService.getUserShares(userId, resourceType, page, pageSize);
            
            result.put("success", true);
            result.put("data", shares.getContent());
            result.put("total", shares.getTotalElements());
            result.put("page", page);
            result.put("pageSize", pageSize);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Get my shares failed", e);
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 删除分享链接
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteShare(
            @PathVariable String id,
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String userId = (String) session.getAttribute("userId");
            if (userId == null) {
                result.put("error", "未登录");
                return ResponseEntity.status(401).body(result);
            }

            shareService.deleteShare(id, userId);
            
            result.put("success", true);
            result.put("message", "分享链接已删除");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Delete share failed", e);
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 获取分享统计
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getShareStats(
            @PathVariable String id,
            @RequestParam(defaultValue = "week") String period,
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            String userId = (String) session.getAttribute("userId");
            if (userId == null) {
                result.put("error", "未登录");
                return ResponseEntity.status(401).body(result);
            }

            Map<String, Object> stats = shareService.getShareAccessStats(id, period);
            
            result.put("success", true);
            result.put("data", stats);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Get share stats failed", e);
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        
        StringBuilder url = new StringBuilder();
        url.append(scheme).append("://").append(serverName);
        
        if ((scheme.equals("http") && serverPort != 80) || 
            (scheme.equals("https") && serverPort != 443)) {
            url.append(":").append(serverPort);
        }
        
        return url.toString();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
