package com.imagemanager.controller;

import com.imagemanager.dto.*;
import com.imagemanager.entity.Notification;
import com.imagemanager.entity.User;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.StorageService;
import com.imagemanager.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理控制器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "用户信息、通知、设置等接口")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private StorageService storageService;
    
    /**
     * 获取当前用户信息
     */
    @GetMapping
    @Operation(summary = "获取用户信息", description = "获取当前登录用户的详细信息")
    public ApiResponse<User> getCurrentUser() {
        log.info("获取当前用户信息");
        User user = userService.getCurrentUser();
        return ApiResponse.success(user);
    }
    
    /**
     * 获取用户统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取统计信息", description = "获取用户的存储空间使用情况等统计")
    public ApiResponse<Map<String, Object>> getUserStats() {
        log.info("获取用户统计信息");
        
        User user = userService.getCurrentUser();
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("storageUsed", user.getStorageUsed());
        stats.put("storageLimit", user.getStorageLimit());
        stats.put("storagePercent", (double) user.getStorageUsed() / user.getStorageLimit() * 100);
        stats.put("imageCount", userService.getImageCount());
        stats.put("albumCount", userService.getAlbumCount());
        stats.put("favoriteCount", userService.getFavoriteCount());
        
        return ApiResponse.success(stats);
    }
    
    /**
     * 更新用户资料
     */
    @PutMapping("/profile")
    @Operation(summary = "更新用户资料", description = "更新用户昵称、头像、简介等资料")
    public ApiResponse<User> updateProfile(
            @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        log.info("更新用户资料: nickname={}, email={}", request.getNickname(), request.getEmail());
        
        String userId = getCurrentUserId(httpRequest);
        authService.updateProfile(userId, request);
        
        User user = userService.getCurrentUser();
        return ApiResponse.success("更新成功", user);
    }
    
    /**
     * 部分更新用户资料
     */
    @PatchMapping("/profile")
    @Operation(summary = "部分更新用户资料", description = "部分更新用户资料字段")
    public ApiResponse<User> patchProfile(
            @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        log.info("部分更新用户资料");
        return updateProfile(request, httpRequest);
    }
    
    /**
     * 上传头像
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传头像", description = "上传用户头像图片")
    public ApiResponse<Map<String, String>> uploadAvatar(
            @Parameter(description = "头像文件") @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        log.info("上传头像: {}", file.getOriginalFilename());
        
        // 验证文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ApiResponse.error(400, "请上传图片文件");
        }
        
        // 验证文件大小（最大2MB）
        if (file.getSize() > 2 * 1024 * 1024) {
            return ApiResponse.error(400, "图片大小不能超过2MB");
        }
        
        try {
            String userId = getCurrentUserId(httpRequest);
            
            // 上传文件到 avatars 目录
            String avatarUrl = storageService.uploadFile(file, "avatars");
            log.info("头像上传成功: {}", avatarUrl);
            
            // 更新用户头像URL
            UpdateProfileRequest profileRequest = new UpdateProfileRequest();
            profileRequest.setAvatar(avatarUrl);
            authService.updateProfile(userId, profileRequest);
            
            Map<String, String> result = new HashMap<>();
            result.put("avatarUrl", avatarUrl);
            
            return ApiResponse.success("头像上传成功", result);
        } catch (Exception e) {
            log.error("上传头像失败", e);
            return ApiResponse.error(500, "上传头像失败: " + e.getMessage());
        }
    }
    
    /**
     * 修改密码
     */
    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "修改用户登录密码")
    public ApiResponse<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        log.info("修改密码");
        
        // 验证新密码确认
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return ApiResponse.error(400, "两次输入的密码不一致");
        }
        
        // 验证密码长度
        if (request.getNewPassword().length() < 6) {
            return ApiResponse.error(400, "密码长度至少6位");
        }
        
        String userId = getCurrentUserId(httpRequest);
        try {
            authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
            return ApiResponse.success("密码修改成功", null);
        } catch (Exception e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
    
    /**
     * 获取用户设置
     */
    @GetMapping("/settings")
    @Operation(summary = "获取用户设置", description = "获取用户的偏好设置")
    public ApiResponse<UserSettings> getUserSettings(HttpServletRequest httpRequest) {
        log.info("获取用户设置");
        
        String userId = getCurrentUserId(httpRequest);
        UserSettings settings = userService.getSettings(userId);
        return ApiResponse.success(settings);
    }
    
    /**
     * 更新用户设置
     */
    @PutMapping("/settings")
    @Operation(summary = "更新用户设置", description = "更新用户的偏好设置")
    public ApiResponse<UserSettings> updateUserSettings(
            @RequestBody UserSettings settings,
            HttpServletRequest httpRequest) {
        log.info("更新用户设置");
        
        String userId = getCurrentUserId(httpRequest);
        UserSettings updated = userService.updateSettings(userId, settings);
        return ApiResponse.success("设置已保存", updated);
    }
    
    /**
     * 部分更新用户设置
     */
    @PatchMapping("/settings")
    @Operation(summary = "部分更新用户设置", description = "部分更新用户的偏好设置")
    public ApiResponse<UserSettings> patchUserSettings(
            @RequestBody UserSettings settings,
            HttpServletRequest httpRequest) {
        log.info("部分更新用户设置");
        
        String userId = getCurrentUserId(httpRequest);
        UserSettings updated = userService.updateSettings(userId, settings);
        return ApiResponse.success("设置已保存", updated);
    }
    
    /**
     * 获取通知列表
     */
    @GetMapping("/notifications")
    @Operation(summary = "获取通知列表", description = "获取用户的所有通知")
    public ApiResponse<List<Notification>> getNotifications() {
        log.info("获取通知列表");
        List<Notification> notifications = userService.getNotifications();
        return ApiResponse.success(notifications);
    }
    
    /**
     * 创建通知
     */
    @PostMapping("/notifications")
    @Operation(summary = "创建通知", description = "创建一条新通知")
    public ApiResponse<Notification> createNotification(
            @RequestBody CreateNotificationRequest request) {
        log.info("创建通知：type={}, title={}", request.getType(), request.getTitle());
        Notification notification = userService.createNotification(request);
        return ApiResponse.success("通知创建成功", notification);
    }
    
    /**
     * 删除通知
     */
    @DeleteMapping("/notifications/{id}")
    @Operation(summary = "删除通知", description = "删除指定通知")
    public ApiResponse<Void> deleteNotification(
            @Parameter(description = "通知ID") @PathVariable String id) {
        log.info("删除通知：{}", id);
        userService.deleteNotification(id);
        return ApiResponse.success("通知已删除", null);
    }
    
    /**
     * 获取未读通知数量
     */
    @GetMapping("/notifications/unread-count")
    @Operation(summary = "获取未读数量", description = "获取未读通知的数量")
    public ApiResponse<Integer> getUnreadCount() {
        log.info("获取未读通知数量");
        Integer count = userService.getUnreadCount();
        return ApiResponse.success(count);
    }
    
    /**
     * 标记通知为已读
     */
    @PostMapping("/notifications/{id}/read")
    @Operation(summary = "标记已读", description = "将指定通知标记为已读")
    public ApiResponse<Void> markNotificationRead(
            @Parameter(description = "通知ID") @PathVariable String id) {
        log.info("标记通知为已读：{}", id);
        userService.markNotificationRead(id);
        return ApiResponse.success("已标记为已读", null);
    }
    
    /**
     * 标记所有通知为已读
     */
    @PostMapping("/notifications/read-all")
    @Operation(summary = "全部标记已读", description = "将所有通知标记为已读")
    public ApiResponse<Void> markAllNotificationsRead() {
        log.info("标记所有通知为已读");
        userService.markAllNotificationsRead();
        return ApiResponse.success("已全部标记为已读", null);
    }
    
    /**
     * 从请求中获取当前用户ID
     */
    private String getCurrentUserId(HttpServletRequest request) {
        // 优先从 X-Session-Id header 获取会话（前端传递方式）
        String sessionId = request.getHeader("X-Session-Id");
        
        // 如果 header 没有，再从 Cookie 获取
        if (sessionId == null && request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if ("session_id".equals(cookie.getName())) {
                    sessionId = cookie.getValue();
                    break;
                }
            }
        }
        
        if (sessionId == null) {
            throw new RuntimeException("未登录");
        }
        
        LoginResponse.UserInfo user = authService.validateSession(sessionId);
        if (user == null) {
            throw new RuntimeException("会话已过期");
        }
        
        return user.getId();
    }
}
