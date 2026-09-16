package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.service.HistoryOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 历史订单控制器
 * 展示已审核投入生产的销售订单（order_xs_list 中 state='1' 的记录），只读。
 */
@RestController
@RequestMapping("/history-orders")
@Tag(name = "历史订单", description = "已审核投入生产的销售订单展示（只读）")
public class HistoryOrderController {

    private final HistoryOrderService historyOrderService;

    public HistoryOrderController(HistoryOrderService historyOrderService) {
        this.historyOrderService = historyOrderService;
    }

    /**
     * 分页查询历史订单（默认只返回已审核订单）
     */
    @GetMapping
    @Operation(summary = "历史订单分页列表", description = "固定只查已审核（state=1）订单，支持关键词/执行状态/是否下计划/日期范围筛选")
    public ApiResponse<Map<String, Object>> listOrders(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "zxtate", required = false) String zxtate,
            @RequestParam(value = "sfplan", required = false) String sfplan,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "sortField", defaultValue = "zhdate") String sortField,
            @RequestParam(value = "sortOrder", defaultValue = "desc") String sortOrder) {
        return ApiResponse.success("获取成功",
                historyOrderService.listOrders(page, size, keyword, zxtate, sfplan, dateFrom, dateTo, sortField, sortOrder));
    }

    /**
     * 历史订单统计（已审核订单维度）
     */
    @GetMapping("/stats")
    @Operation(summary = "历史订单统计", description = "已审核订单总数/数量合计/客户数/已下计划数/本月新增")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success("获取成功", historyOrderService.stats());
    }

    /**
     * 订单详情（按单号）
     */
    @GetMapping("/{dh}")
    @Operation(summary = "订单详情", description = "按单号查询订单全部字段（含品名）")
    public ApiResponse<Map<String, Object>> getOrder(@PathVariable("dh") String dh) {
        return ApiResponse.success("获取成功", historyOrderService.getOrder(dh));
    }
}
