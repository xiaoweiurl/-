package com.imagemanager.service;

import java.util.List;
import java.util.Map;

/**
 * 三单据（报价单/销售单/工艺单）统计服务
 * 数据范围严格限定三类单据，不出现其他业务模块指标。
 */
public interface DocumentStatsService {

    /**
     * 概览 6 卡片：报价金额/报价笔数/销售金额/订单数/在制工艺数/合格率
     */
    Map<String, Object> overview();

    /**
     * 报价&销售金额趋势（按日期聚合）
     *
     * @param days 近 N 天
     */
    Map<String, Object> trend(int days);

    /**
     * 工艺单状态分布（按货号类型 hhtype 分组，环形图）
     */
    List<Map<String, Object>> gongyidanStatus();

    /**
     * 最近报价单列表
     */
    List<Map<String, Object>> recentQuotations(int limit);

    /**
     * 最近工艺单列表（按现有系统关联逻辑带出：原料BOM/机台产能/工序/工价条数）
     */
    List<Map<String, Object>> recentGongyidan(int limit);
}
