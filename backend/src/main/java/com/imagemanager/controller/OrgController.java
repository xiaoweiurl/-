package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.DingTalkContactCandidate;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.dto.OrgDepartmentNode;
import com.imagemanager.dto.OrgSyncResult;
import com.imagemanager.org.OrgRegistrationService;
import com.imagemanager.org.OrgSyncService;
import com.imagemanager.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉组织（办公/管理端）：同步、部门树、通讯录搜索。
 * 车间考勤不走本模块。
 */
@Slf4j
@RestController
@RequestMapping("/org")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "钉钉组织", description = "同步钉钉部门/通讯录（仅管理员）")
public class OrgController {

    private final OrgSyncService orgSyncService;
    private final OrgRegistrationService orgRegistrationService;
    private final AuthService authService;

    public OrgController(OrgSyncService orgSyncService,
                         OrgRegistrationService orgRegistrationService,
                         AuthService authService) {
        this.orgSyncService = orgSyncService;
        this.orgRegistrationService = orgRegistrationService;
        this.authService = authService;
    }

    @GetMapping("/status")
    @Operation(summary = "组织同步状态")
    public ApiResponse<Map<String, Object>> status(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) {
            return ApiResponse.error(denied.getCode(), denied.getMessage());
        }
        OrgSyncResult state = orgSyncService.status(null);
        Map<String, Object> data = new HashMap<>();
        data.put("configured", orgSyncService.isConfigured());
        data.put("sync", state);
        return ApiResponse.success(data);
    }

    @PostMapping("/sync")
    @Operation(summary = "同步钉钉组织", description = "拉取部门树与通讯录写入本地。未配置 AppKey/Secret 时返回明确错误。")
    public ApiResponse<OrgSyncResult> sync(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) {
            return ApiResponse.error(denied.getCode(), denied.getMessage());
        }
        try {
            return ApiResponse.success("同步完成", orgSyncService.sync());
        } catch (Exception e) {
            log.error("钉钉组织同步失败", e);
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/departments")
    @Operation(summary = "部门树", description = "返回完整部门树（根节点为钉钉根部门全称「宝娜斯集团有限公司」），每个节点含直属成员（姓名/职位/是否已注册）。同一人同时属于上下级时只出现在最具体部门。")
    public ApiResponse<List<OrgDepartmentNode>> departments(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) {
            return ApiResponse.error(denied.getCode(), denied.getMessage());
        }
        return ApiResponse.success(orgSyncService.departmentTree(null));
    }

    @GetMapping("/contacts")
    @Operation(summary = "按姓名搜索通讯录")
    public ApiResponse<List<DingTalkContactCandidate>> contacts(
            @RequestParam(required = false) String name,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        ApiResponse<Void> denied = checkAdminOrAbove(sessionId);
        if (denied != null) {
            return ApiResponse.error(denied.getCode(), denied.getMessage());
        }
        return ApiResponse.success(orgRegistrationService.searchContacts(null, name));
    }

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
            return ApiResponse.error(403, "仅管理员可管理钉钉组织");
        }
        return null;
    }
}
