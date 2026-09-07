package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.ErpAuthService;
import com.imagemanager.service.ErpClient;
import com.imagemanager.service.ErpSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ERP 数据同步控制器
 *
 * 权限：仅管理员及以上角色（admin / superadmin）可访问。
 * 认证：请求头 X-Session-Id（系统会话）；ERP 业务请求由服务端自动携带 ERP token。
 * 401（ERP 未登录/token 失效）时前端跳转 ERP 登录。
 */
@Slf4j
@RestController
@RequestMapping("/erp-sync")
@Tag(name = "ERP 数据同步", description = "外部 ERP 系统数据增量同步（仅管理员及以上）")
public class ErpSyncController {

    private final ErpSyncService erpSyncService;
    private final ErpAuthService erpAuthService;
    private final AuthService authService;

    public ErpSyncController(ErpSyncService erpSyncService,
                             ErpAuthService erpAuthService,
                             AuthService authService) {
        this.erpSyncService = erpSyncService;
        this.erpAuthService = erpAuthService;
        this.authService = authService;
    }

    /** 权限校验：仅管理员及以上（admin / superadmin） */
    private ApiResponse<Void> checkAdminOrAbove(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ApiResponse.error(401, "未登录");
        }
        LoginResponse.UserInfo user = authService.validateSession(sessionId);
        if (user == null) {
            return ApiResponse.error(401, "会话已过期，请重新登录");
        }
        String role = user.getRole();
        if (!"admin".equalsIgnoreCase(role) && !"superadmin".equalsIgnoreCase(role)) {
            return ApiResponse.error(403, "仅管理员及以上角色可访问 ERP 数据同步功能");
        }
        return null;
    }

    // ==================== ERP 登录态 ====================

    @PostMapping("/login")
    @Operation(summary = "ERP 登录", description = "使用后端固定凭证换取 token（登录接口独立地址），token 缓存于服务端；body 可为空")
    public ApiResponse<Map<String, Object>> erpLogin(
            @RequestBody(required = false) Map<String, String> request,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) return ApiResponse.error(denied.getCode(), denied.getMessage());
        try {
            Map<String, Object> result = erpAuthService.login(
                    request != null ? request.get("uid") : null,
                    request != null ? request.get("password") : null,
                    request != null ? request.get("customId") : null);
            return ApiResponse.success("ERP 登录成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (ErpClient.ErpAuthException e) {
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("[ERP登录] 失败", e);
            return ApiResponse.error(500, "ERP 登录失败: " + e.getMessage());
        }
    }

    @GetMapping("/auth-state")
    @Operation(summary = "ERP 登录态", description = "返回是否已登录/账号/登录时间/演示模式（不返回 token）")
    public ApiResponse<Map<String, Object>> authState(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) return ApiResponse.error(denied.getCode(), denied.getMessage());
        return ApiResponse.success("获取成功", erpAuthService.getAuthState());
    }

    @PostMapping("/logout")
    @Operation(summary = "ERP 登出", description = "清除服务端缓存的 ERP token")
    public ApiResponse<Void> erpLogout(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) return ApiResponse.error(denied.getCode(), denied.getMessage());
        erpAuthService.logout();
        return ApiResponse.success("已登出", null);
    }

    // ==================== 同步 ====================

    @GetMapping("/status")
    @Operation(summary = "同步状态概览", description = "各模块游标/累计记录/最后同步状态 + 汇总统计")
    public ApiResponse<Map<String, Object>> status(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) return ApiResponse.error(denied.getCode(), denied.getMessage());
        return ApiResponse.success("获取成功", erpSyncService.getStatus());
    }

    @PostMapping("/sync/{moduleKey}")
    @Operation(summary = "同步单个模块", description = "增量同步：按「数据库最新时间→当前时间」过滤数据")
    public ApiResponse<Map<String, Object>> syncModule(
            @PathVariable("moduleKey") String moduleKey,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) return ApiResponse.error(denied.getCode(), denied.getMessage());
        try {
            return ApiResponse.success("同步完成", erpSyncService.syncModule(moduleKey));
        } catch (ErpClient.ErpAuthException e) {
            // 401：ERP 未登录或 token 失效，前端跳 ERP 登录
            return ApiResponse.error(401, e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[ERP同步] 模块 {} 同步异常", moduleKey, e);
            return ApiResponse.error(500, "同步异常: " + e.getMessage());
        }
    }

    @PostMapping("/sync-all")
    @Operation(summary = "同步全部模块", description = "串行执行 7 个模块的增量同步，返回各模块日志")
    public ApiResponse<List<Map<String, Object>>> syncAll(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) return ApiResponse.error(denied.getCode(), denied.getMessage());
        try {
            return ApiResponse.success("全部同步完成", erpSyncService.syncAll());
        } catch (ErpClient.ErpAuthException e) {
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("[ERP同步] 全量同步异常", e);
            return ApiResponse.error(500, "同步异常: " + e.getMessage());
        }
    }

    // ==================== 日志 ====================

    @GetMapping("/logs")
    @Operation(summary = "同步日志列表", description = "按时间倒序返回同步日志（增量范围/记录数/状态/耗时）")
    public ApiResponse<List<Map<String, Object>>> logs(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) return ApiResponse.error(denied.getCode(), denied.getMessage());
        return ApiResponse.success("获取成功", erpSyncService.getLogs(limit));
    }

    @DeleteMapping("/logs")
    @Operation(summary = "清空同步日志", description = "删除全部同步日志（不影响同步状态游标）")
    public ApiResponse<Void> clearLogs(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) return ApiResponse.error(denied.getCode(), denied.getMessage());
        erpSyncService.clearLogs();
        return ApiResponse.success("已清空", null);
    }
}
