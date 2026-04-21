package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.DashboardStatsResponse;
import com.imagemanager.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 仪表盘统计控制器
 *
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
@Tag(name = "仪表盘", description = "数据统计仪表盘接口")
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 获取仪表盘统计数据
     */
    @GetMapping("/stats")
    @Operation(summary = "获取仪表盘统计数据", description = "获取系统的各项统计数据，包括上传趋势、相册分布、标签统计等")
    public ApiResponse<DashboardStatsResponse> getDashboardStats(
            @Parameter(description = "统计周期：week(近7天)、month(近30天)、year(近一年)")
            @RequestParam(name = "period", defaultValue = "month") String period) {
        log.info("[Dashboard] 获取仪表盘统计数据，周期: {}", period);

        // 验证周期参数
        if (!period.matches("^(week|month|year)$")) {
            period = "month";
        }

        DashboardStatsResponse stats = dashboardService.getDashboardStats(period);
        return ApiResponse.success(stats);
    }

    /**
     * 获取热门资源
     */
    @GetMapping("/hot-resources")
    @Operation(summary = "获取热门资源", description = "获取最受欢迎的资源（按浏览、下载、收藏综合排序）")
    public ApiResponse<List<DashboardStatsResponse.HotResource>> getHotResources(
            @Parameter(description = "返回数量")
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        log.info("[Dashboard] 获取热门资源，数量: {}", limit);

        if (limit <= 0 || limit > 50) {
            limit = 10;
        }

        List<DashboardStatsResponse.HotResource> hotResources = dashboardService.getHotResources(limit);
        return ApiResponse.success(hotResources);
    }

    /**
     * 获取热门相册
     */
    @GetMapping("/hot-albums")
    @Operation(summary = "获取热门相册", description = "获取资源最多的相册")
    public ApiResponse<List<DashboardStatsResponse.HotAlbum>> getHotAlbums(
            @Parameter(description = "返回数量")
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        log.info("[Dashboard] 获取热门相册，数量: {}", limit);

        if (limit <= 0 || limit > 20) {
            limit = 5;
        }

        List<DashboardStatsResponse.HotAlbum> hotAlbums = dashboardService.getHotAlbums(limit);
        return ApiResponse.success(hotAlbums);
    }

    /**
     * 获取活跃度统计
     */
    @GetMapping("/activity")
    @Operation(summary = "获取活跃度统计", description = "获取今日的活跃度统计数据")
    public ApiResponse<DashboardStatsResponse.ActivityStats> getActivityStats() {
        log.info("[Dashboard] 获取活跃度统计");

        DashboardStatsResponse.ActivityStats activityStats = dashboardService.getActivityStats();
        return ApiResponse.success(activityStats);
    }
}
