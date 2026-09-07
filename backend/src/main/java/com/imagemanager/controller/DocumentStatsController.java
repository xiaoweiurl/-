package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.service.DocumentStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 三单据（报价单/销售单/工艺单）统计控制器
 * 数据范围严格限定三类单据，为供应链仪表盘提供指标。
 */
@Slf4j
@RestController
@RequestMapping("/document-stats")
@Tag(name = "三单据统计", description = "报价单/销售单/工艺单维度统计（仪表盘）")
public class DocumentStatsController {

    private final DocumentStatsService documentStatsService;

    public DocumentStatsController(DocumentStatsService documentStatsService) {
        this.documentStatsService = documentStatsService;
    }

    @GetMapping("/overview")
    @Operation(summary = "概览 6 卡片", description = "报价金额/报价笔数/销售金额/订单数/在制工艺数/合格率")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.success("获取成功", documentStatsService.overview());
    }

    @GetMapping("/trend")
    @Operation(summary = "报价&销售金额趋势", description = "按日期聚合近 N 天趋势（折线图）")
    public ApiResponse<Map<String, Object>> trend(
            @RequestParam(value = "days", defaultValue = "30") int days) {
        return ApiResponse.success("获取成功", documentStatsService.trend(days));
    }

    @GetMapping("/gongyidan-status")
    @Operation(summary = "工艺单状态分布", description = "按货号类型 hhtype 分组（环形图）")
    public ApiResponse<List<Map<String, Object>>> gongyidanStatus() {
        return ApiResponse.success("获取成功", documentStatsService.gongyidanStatus());
    }

    @GetMapping("/recent-quotations")
    @Operation(summary = "最近报价单", description = "按报价日期倒序")
    public ApiResponse<List<Map<String, Object>>> recentQuotations(
            @RequestParam(value = "limit", defaultValue = "8") int limit) {
        return ApiResponse.success("获取成功", documentStatsService.recentQuotations(limit));
    }

    @GetMapping("/recent-gongyidan")
    @Operation(summary = "最近工艺单", description = "按现有系统关联逻辑带出原料BOM/机台产能/工序/工价条数")
    public ApiResponse<List<Map<String, Object>>> recentGongyidan(
            @RequestParam(value = "limit", defaultValue = "8") int limit) {
        return ApiResponse.success("获取成功", documentStatsService.recentGongyidan(limit));
    }
}
