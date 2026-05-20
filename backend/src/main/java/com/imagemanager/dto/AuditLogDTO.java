package com.imagemanager.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志DTO
 */
@Data
public class AuditLogDTO {
    private String id;
    private String userId;
    private String username;
    private String action;
    private String actionDisplay;
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String details;
    private String ipAddress;
    private String userAgent;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private String timeAgo;
    
    /**
     * 获取操作显示名称
     */
    public String getActionDisplay() {
        if (action == null) return "";
        return switch (action) {
            case "login" -> "登录";
            case "logout" -> "登出";
            case "register" -> "注册";
            case "change_password" -> "修改密码";
            case "update_profile" -> "更新资料";
            case "upload_image" -> "上传图片";
            case "delete_image" -> "删除图片";
            case "restore_image" -> "恢复图片";
            case "download_image" -> "下载图片";
            case "edit_image" -> "编辑图片";
            case "move_image" -> "移动图片";
            case "batch_delete" -> "批量删除";
            case "batch_download" -> "批量下载";
            case "create_album" -> "创建相册";
            case "delete_album" -> "删除相册";
            case "update_album" -> "更新相册";
            case "share_album" -> "分享相册";
            case "create_share" -> "创建分享";
            case "access_share" -> "访问分享";
            case "download_share" -> "下载分享";
            case "backup_data" -> "备份数据";
            case "restore_data" -> "恢复数据";
            case "update_settings" -> "更新设置";
            case "manage_user" -> "管理用户";
            default -> action;
        };
    }
}
