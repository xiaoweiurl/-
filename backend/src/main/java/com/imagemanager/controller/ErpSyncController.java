package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.ErpAuthService;
import com.imagemanager.service.ErpClient;
import com.imagemanager.service.ErpSyncService;
import com.imagemanager.util.SessionIdExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ERP 数据同步控制器
 *
 * 权限：仅管理员及以上角色（admin / superadmin）可访问。
 * 认证：与 BFF 一致，Cookie {@code session_id} 优先于 {@code X-Session-Id}（见 {@link SessionIdExtractor}）。
 * ERP 业务请求由服务端自动携带 ERP token。
 * 系统会话 401/403 以真实 HTTP 状态返回（body 仍为 {@link ApiResponse}）。
 * ERP 未登录/token 失效仍以业务码 401 返回（HTTP 200），前端跳转 ERP 登录。
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

    /**
     * 权限校验：仅管理员及以上（admin / superadmin）。
     * 失败时 HTTP 状态与业务 code 一致（401/403），body 保持 success/code/message。
     */
    private <T> ResponseEntity<ApiResponse<T>> denyIfNotAdmin(HttpServletRequest request) {
        String sessionId = SessionIdExtractor.extract(request);
        if (sessionId == null || sessionId.isBlank()) {
            log.warn("[ERP同步] 权限校验失败: 401 未登录（缺少会话）");
            return statusError(401, "未登录");
        }
        LoginResponse.UserInfo user = authService.validateSession(sessionId);
        if (user == null) {
            String prefix = sessionId.length() > 8 ? sessionId.substring(0, 8) : sessionId;
            log.warn("[ERP同步] 权限校验失败: 401 会话无效, sessionId={}***", prefix);
            return statusError(401, "会话已过期，请重新登录");
        }
        String role = user.getRole();
        if (!"admin".equalsIgnoreCase(role) && !"superadmin".equalsIgnoreCase(role)) {
            log.warn("[ERP同步] 权限校验失败: 403 角色不足, user={}, role={}", user.getUsername(), role);
            return statusError(403, "仅管理员及以上角色可访问 ERP 数据同步功能");
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> ResponseEntity<ApiResponse<T>> statusError(int code, String message) {
        return ResponseEntity.status(code).body((ApiResponse<T>) ApiResponse.error(code, message));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(ApiResponse<T> body) {
        return ResponseEntity.ok(body);
    }

    // ==================== ERP 登录态 ====================

    @PostMapping("/login")
    @Operation(summary = "ERP 登录", description = "使用后端固定凭证换取 token（登录接口独立地址），token 缓存于服务端；body 可为空")
    public ResponseEntity<ApiResponse<Map<String, Object>>> erpLogin(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        ResponseEntity<ApiResponse<Map<String, Object>>> denied = denyIfNotAdmin(request);
        if (denied != null) {
            return denied;
        }
        try {
            Map<String, Object> result = erpAuthService.login(
                    body != null ? body.get("uid") : null,
                    body != null ? body.get("password") : null,
                    body != null ? body.get("customId") : null);
            return ok(ApiResponse.success("ERP 登录成功", result));
        } catch (IllegalArgumentException e) {
            return ok(ApiResponse.error(400, e.getMessage()));
        } catch (ErpClient.ErpAuthException e) {
            return ok(ApiResponse.error(401, e.getMessage()));
        } catch (Exception e) {
            log.error("[ERP登录] 失败", e);
            return ok(ApiResponse.error(500, "ERP 登录失败: " + e.getMessage()));
        }
    }

    @GetMapping("/auth-state")
    @Operation(summary = "ERP 登录态", description = "返回是否已登录/账号/登录时间/演示模式（不返回 token）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> authState(HttpServletRequest request) {
        ResponseEntity<ApiResponse<Map<String, Object>>> denied = denyIfNotAdmin(request);
        if (denied != null) {
            return denied;
        }
        return ok(ApiResponse.success("获取成功", erpAuthService.getAuthState()));
    }

    @PostMapping("/logout")
    @Operation(summary = "ERP 登出", description = "清除服务端缓存的 ERP token")
    public ResponseEntity<ApiResponse<Void>> erpLogout(HttpServletRequest request) {
        ResponseEntity<ApiResponse<Void>> denied = denyIfNotAdmin(request);
        if (denied != null) {
            return denied;
        }
        erpAuthService.logout();
        return ok(ApiResponse.success("已登出", null));
    }

    // ==================== 同步 ====================

    @GetMapping("/status")
    @Operation(summary = "同步状态概览", description = "各模块游标/累计记录/最后同步状态 + 汇总统计")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(HttpServletRequest request) {
        ResponseEntity<ApiResponse<Map<String, Object>>> denied = denyIfNotAdmin(request);
        if (denied != null) {
            return denied;
        }
        return ok(ApiResponse.success("获取成功", erpSyncService.getStatus()));
    }

    @PostMapping("/sync/{moduleKey}")
    @Operation(summary = "同步单个模块", description = "增量同步：按「数据库最新时间→当前时间」过滤数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncModule(
            @PathVariable("moduleKey") String moduleKey,
            HttpServletRequest request) {
        ResponseEntity<ApiResponse<Map<String, Object>>> denied = denyIfNotAdmin(request);
        if (denied != null) {
            return denied;
        }
        try {
            return ok(ApiResponse.success("同步完成", erpSyncService.syncModule(moduleKey)));
        } catch (ErpClient.ErpAuthException e) {
            // 401：ERP 未登录或 token 失效，前端跳 ERP 登录（业务码，非系统会话）
            return ok(ApiResponse.error(401, e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ok(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("[ERP同步] 模块 {} 同步异常", moduleKey, e);
            return ok(ApiResponse.error(500, "同步异常: " + e.getMessage()));
        }
    }

    @PostMapping("/sync-all")
    @Operation(summary = "同步全部模块", description = "串行执行 7 个模块的增量同步，返回各模块日志")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> syncAll(HttpServletRequest request) {
        ResponseEntity<ApiResponse<List<Map<String, Object>>>> denied = denyIfNotAdmin(request);
        if (denied != null) {
            return denied;
        }
        try {
            return ok(ApiResponse.success("全部同步完成", erpSyncService.syncAll()));
        } catch (ErpClient.ErpAuthException e) {
            return ok(ApiResponse.error(401, e.getMessage()));
        } catch (Exception e) {
            log.error("[ERP同步] 全量同步异常", e);
            return ok(ApiResponse.error(500, "同步异常: " + e.getMessage()));
        }
    }

    // ==================== 日志 ====================

    @GetMapping("/logs")
    @Operation(summary = "同步日志列表", description = "按时间倒序返回同步日志（增量范围/记录数/状态/耗时）")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> logs(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        ResponseEntity<ApiResponse<List<Map<String, Object>>>> denied = denyIfNotAdmin(request);
        if (denied != null) {
            return denied;
        }
        return ok(ApiResponse.success("获取成功", erpSyncService.getLogs(limit)));
    }

    @DeleteMapping("/logs")
    @Operation(summary = "清空同步日志", description = "删除全部同步日志（不影响同步状态游标）")
    public ResponseEntity<ApiResponse<Void>> clearLogs(HttpServletRequest request) {
        ResponseEntity<ApiResponse<Void>> denied = denyIfNotAdmin(request);
        if (denied != null) {
            return denied;
        }
        erpSyncService.clearLogs();
        return ok(ApiResponse.success("已清空", null));
    }
}
