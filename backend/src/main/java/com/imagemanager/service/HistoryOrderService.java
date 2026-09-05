package com.imagemanager.service;

import java.util.Map;

/**
 * 历史订单服务
 * 销售订单（order_xs_list）中已审核投入生产的订单展示（只读）
 */
public interface HistoryOrderService {

    /**
     * 分页查询历史订单（固定只查 state='1' 已审核订单）
     *
     * @param page      页码（从 1 开始）
     * @param size      每页数量
     * @param keyword   关键词（单号/业务单号/客户/生产货号/成品货号/业务员 模糊匹配），可空
     * @param zxtate    执行状态筛选（0→未审核 1→已复审 其他→已经终审），可空
     * @param sfplan    是否下计划筛选（是/否），可空
     * @param dateFrom  下单日期起（yyyy-MM-dd），可空
     * @param dateTo    下单日期止（yyyy-MM-dd），可空
     * @param sortField 排序字段（zhdate/jh_date/sl_sum/dh），默认 zhdate
     * @param sortOrder 排序方向（asc/desc），默认 desc
     * @return list/total/page/size
     */
    Map<String, Object> listOrders(int page, int size, String keyword, String zxtate, String sfplan,
                                   String dateFrom, String dateTo, String sortField, String sortOrder);

    /**
     * 历史订单统计（已审核订单维度）
     *
     * @return totalOrders/totalQuantity/customerCount/plannedCount/monthNewCount
     */
    Map<String, Object> stats();

    /**
     * 订单详情（按单号）
     *
     * @param dh 订单单号
     * @return 订单全部字段（含品名）
     */
    Map<String, Object> getOrder(String dh);
}
