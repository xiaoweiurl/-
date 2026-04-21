package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.CreateUserRequest;
import com.imagemanager.dto.UpdateUserRequest;
import com.imagemanager.entity.User;
import com.imagemanager.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理员控制器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@Tag(name = "管理员接口", description = "管理员专用接口，需要管理员权限")
public class AdminController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 获取所有用户列表
     */
    @GetMapping("/users")
    @Operation(summary = "获取用户列表", description = "获取所有用户列表（仅管理员）")
    public ApiResponse<List<UserInfo>> getAllUsers() {
        log.info("管理员获取用户列表");
        
        try {
            List<User> users = userService.getAllUsers();
            List<UserInfo> userInfos = users.stream()
                    .map(this::convertToUserInfo)
                    .collect(Collectors.toList());
            
            log.info("获取用户列表成功，共 {} 个用户", userInfos.size());
            return ApiResponse.success(userInfos);
        } catch (Exception e) {
            log.error("获取用户列表失败", e);
            throw e;
        }
    }
    
    /**
     * 获取用户详情
     */
    @GetMapping("/users/{id}")
    @Operation(summary = "获取用户详情", description = "获取指定用户的详细信息（仅管理员）")
    public ApiResponse<UserInfo> getUserById(@PathVariable String id) {
        log.info("管理员获取用户详情：{}", id);
        
        User user = userService.getUserById(id);
        return ApiResponse.success(convertToUserInfo(user));
    }
    
    /**
     * 创建新用户
     */
    @PostMapping("/users")
    @Operation(summary = "创建用户", description = "创建新用户（仅管理员）")
    public ApiResponse<UserInfo> createUser(@RequestBody CreateUserRequest request) {
        log.info("管理员创建用户：{}", request.getUsername());
        
        try {
            User user = userService.createUser(request);
            return ApiResponse.success("用户创建成功", convertToUserInfo(user));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
    
    /**
     * 更新用户信息
     */
    @PutMapping("/users/{id}")
    @Operation(summary = "更新用户", description = "更新用户信息（仅管理员）")
    public ApiResponse<UserInfo> updateUser(
            @PathVariable String id,
            @RequestBody UpdateUserRequest request) {
        log.info("管理员更新用户：{}", id);
        
        try {
            User user = userService.updateUser(id, request);
            return ApiResponse.success("用户更新成功", convertToUserInfo(user));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    @Operation(summary = "删除用户", description = "删除指定用户（仅管理员）")
    public ApiResponse<Void> deleteUser(@PathVariable String id) {
        log.info("管理员删除用户：{}", id);
        
        try {
            userService.deleteUser(id);
            return ApiResponse.success("用户删除成功", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
    
    /**
     * 重置用户密码
     */
    @PostMapping("/users/{id}/reset-password")
    @Operation(summary = "重置密码", description = "重置用户密码（仅管理员）")
    public ApiResponse<Void> resetPassword(
            @PathVariable String id,
            @RequestBody Map<String, String> request) {
        log.info("管理员重置用户密码：{}", id);
        
        String newPassword = request.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return ApiResponse.error(400, "密码长度至少6位");
        }
        
        try {
            userService.resetPassword(id, newPassword);
            return ApiResponse.success("密码重置成功", null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }
    
    /**
     * 获取系统统计
     */
    @GetMapping("/stats")
    @Operation(summary = "获取系统统计", description = "获取系统整体统计数据（仅管理员）")
    public ApiResponse<SystemStats> getSystemStats() {
        log.info("管理员获取系统统计");
        
        SystemStats stats = new SystemStats();
        stats.setUserCount(userService.getAllUsers().size());
        stats.setImageCount(userService.getImageCount());
        stats.setAlbumCount(userService.getAlbumCount());
        
        return ApiResponse.success(stats);
    }
    
    /**
     * 用户信息（脱敏）
     */
    @lombok.Data
    public static class UserInfo {
        private String id;
        private String username;
        private String email;
        private String avatar;
        private String nickname;
        private String phone;
        private String bio;
        private String role;
        private String membership;
        private String createdAt;
        private String lastLoginAt;
    }
    
    /**
     * 系统统计
     */
    @lombok.Data
    public static class SystemStats {
        private Integer userCount;
        private Integer imageCount;
        private Integer albumCount;
    }
    
    /**
     * 转换 User 为 UserInfo
     */
    private UserInfo convertToUserInfo(User user) {
        UserInfo info = new UserInfo();
        info.setId(user.getId());
        info.setUsername(user.getUsername());
        info.setEmail(user.getEmail());
        info.setAvatar(user.getAvatarUrl());
        info.setNickname(user.getNickname());
        info.setPhone(user.getPhone());
        info.setBio(user.getBio());
        info.setRole(user.getRole());
        info.setMembership(user.getMembership());
        info.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        info.setLastLoginAt(user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
        return info;
    }
}
