package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.service.OpsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统运维中心控制器
 * 提供API监控、错误追踪、性能指标、操作审计、备份管理接口
 */
@Slf4j
@RestController
@RequestMapping("/ops")
@RequiredArgsConstructor
@Tag(name = "系统运维", description = "运维中心：API监控、错误追踪、性能指标")
public class OpsController {

    private final OpsService opsService;

    /**
     * 获取API调用指标
     */
    @GetMapping("/metrics")
    @Operation(summary = "获取API调用指标", description = "获取API调用统计、24h趋势、端点排行、系统资源")
    public ApiResponse<Map<String, Object>> getMetrics(
            @RequestParam(defaultValue = "24h") String period,
            HttpServletRequest request) {
        log.info("[Ops] 获取API指标, period={}", period);
        return ApiResponse.success(opsService.getMetrics(period, request));
    }

    /**
     * 获取错误列表
     */
    @GetMapping("/errors")
    @Operation(summary = "获取错误列表", description = "获取系统错误列表，支持分页和筛选")
    public ApiResponse<Map<String, Object>> getErrors(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Boolean resolved,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        log.info("[Ops] 获取错误列表, severity={}, resolved={}, page={}", severity, resolved, page);
        return ApiResponse.success(opsService.getErrors(severity, resolved, page, pageSize, request));
    }

    /**
     * 标记错误已解决
     */
    @PatchMapping("/errors/{id}/resolve")
    @Operation(summary = "标记错误已解决")
    public ApiResponse<Map<String, Object>> resolveError(
            @PathVariable String id,
            HttpServletRequest request) {
        log.info("[Ops] 标记错误已解决, id={}", id);
        return ApiResponse.success(opsService.resolveError(id, request));
    }

    /**
     * 获取性能指标
     */
    @GetMapping("/performance")
    @Operation(summary = "获取性能指标", description = "获取服务状态、慢查询、运行时指标")
    public ApiResponse<Map<String, Object>> getPerformance(
            @RequestParam(defaultValue = "1h") String period,
            HttpServletRequest request) {
        log.info("[Ops] 获取性能指标, period={}", period);
        return ApiResponse.success(opsService.getPerformance(period, request));
    }

    /**
     * 获取备份列表
     */
    @GetMapping("/backups")
    @Operation(summary = "获取备份列表")
    public ApiResponse<Map<String, Object>> getBackups(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        log.info("[Ops] 获取备份列表, page={}", page);
        return ApiResponse.success(opsService.getBackups(page, pageSize, request));
    }

    /**
     * 创建备份
     */
    @PostMapping("/backups")
    @Operation(summary = "创建备份")
    public ApiResponse<Map<String, Object>> createBackup(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        String type = (String) body.getOrDefault("type", "full");
        String description = (String) body.getOrDefault("description", "");
        log.info("[Ops] 创建备份, type={}", type);
        return ApiResponse.success(opsService.createBackup(type, description, request));
    }

    /**
     * 记录API调用（由拦截器或中间件调用）
     */
    @PostMapping("/metrics/record")
    @Operation(summary = "记录API调用", description = "内部接口，记录一次API调用的指标数据")
    public ApiResponse<Void> recordMetric(@RequestBody Map<String, Object> metric) {
        opsService.recordMetric(metric);
        return ApiResponse.success(null);
    }
}
