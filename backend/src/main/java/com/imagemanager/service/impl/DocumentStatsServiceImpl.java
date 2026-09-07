package com.imagemanager.service.impl;

import com.imagemanager.service.DocumentStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三单据（报价单/销售单/工艺单）统计实现（JdbcTemplate 只读查询）
 *
 * 数据口径：
 * - 报价金额 = SUM(order_bjd_query.saleprice)；报价笔数 = COUNT
 * - 销售金额 = SUM(order_xs_list.sl_sum × 货号最新报价 saleprice)（已审核订单，按最新报价估算）
 * - 订单数 = COUNT(DISTINCT order_xs_list.dh WHERE state='审核')
 * - 在制工艺数 = COUNT(order_jfk_gongyidan)
 * - 合格率 = AVG(order_bjd_query.zpl)（正品率均值，兼容 0-1 与 0-100 两种存储）
 * - 工艺单关联数据：复用现有系统关联逻辑（jfk.huohao = buj/gxp/gxpr.hhname = raw_material_warehouse.huohao），
 *   按货号聚合原料BOM/机台产能/工序/工价条数
 */
@Slf4j
@Service
public class DocumentStatsServiceImpl implements DocumentStatsService {

    private static final String QUOTATION_TABLE = "order_bjd_query";
    private static final String SALES_ORDER_TABLE = "order_xs_list";
    private static final String JFK_PROCESS_TABLE = "order_jfk_gongyidan";
    private static final String BUJ_COMPONENT_TABLE = "order_buj_component";
    private static final String GONGXU_PROCESS_TABLE = "order_gongxu_process";
    private static final String GONGXU_PRICE_TABLE = "order_gongxu_price";
    private static final String RAW_MATERIAL_TABLE = "raw_material_warehouse";
    /** 销售单已审核状态（实际库存中文"审核"） */
    private static final String STATE_APPROVED = "审核";

    private final JdbcTemplate jdbcTemplate;

    public DocumentStatsServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();

        // 报价金额/笔数
        try {
            Map<String, Object> q = jdbcTemplate.queryForMap(
                    "SELECT COALESCE(SUM(saleprice), 0) AS amount, COUNT(*) AS cnt FROM " + QUOTATION_TABLE);
            out.put("quotationAmount", round2(q.get("amount")));
            out.put("quotationCount", ((Number) q.get("cnt")).longValue());
        } catch (Exception e) {
            log.warn("[三单据统计] 报价统计失败: {}", e.getMessage());
            out.put("quotationAmount", 0);
            out.put("quotationCount", 0);
        }

        // 销售数量（sl_sum=数量合计，销售单无金额字段）/订单数
        try {
            Map<String, Object> s = jdbcTemplate.queryForMap(
                    "SELECT COALESCE(SUM(sl_sum), 0) AS qty, COUNT(DISTINCT dh) AS cnt "
                            + "FROM " + SALES_ORDER_TABLE + " WHERE state = ?", STATE_APPROVED);
            out.put("salesQuantity", ((Number) s.get("qty")).longValue());
            out.put("salesOrderCount", ((Number) s.get("cnt")).longValue());
        } catch (Exception e) {
            log.warn("[三单据统计] 销售统计失败: {}", e.getMessage());
            out.put("salesQuantity", 0);
            out.put("salesOrderCount", 0);
        }

        // 在制工艺数
        try {
            Long cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + JFK_PROCESS_TABLE, Long.class);
            out.put("gongyidanCount", cnt != null ? cnt : 0);
        } catch (Exception e) {
            log.warn("[三单据统计] 工艺单统计失败: {}", e.getMessage());
            out.put("gongyidanCount", 0);
        }

        // 合格率（报价单正品率 zpl 均值，兼容 0-1 / 0-100 存储）
        try {
            Double avg = jdbcTemplate.queryForObject(
                    "SELECT AVG(CASE WHEN zpl > 1 THEN zpl ELSE zpl * 100 END) FROM " + QUOTATION_TABLE
                            + " WHERE zpl IS NOT NULL AND zpl > 0", Double.class);
            out.put("passRate", avg != null ? Math.round(avg * 10.0) / 10.0 : 0);
        } catch (Exception e) {
            log.warn("[三单据统计] 合格率统计失败: {}", e.getMessage());
            out.put("passRate", 0);
        }

        // 环比：本月 vs 上月报价笔数/订单数
        out.put("quotationMonthTrend", monthTrend(QUOTATION_TABLE, null));
        out.put("salesMonthTrend", monthTrend(SALES_ORDER_TABLE, STATE_APPROVED));
        return out;
    }

    /** 本月/上月笔数环比（百分比，无上月数据时返回 null） */
    private Double monthTrend(String table, String approvedState) {
        try {
            String where = approvedState != null ? " AND state = '" + approvedState + "'" : "";
            Map<String, Object> r = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) FILTER (WHERE zhdate >= date_trunc('month', CURRENT_DATE)) AS cur, "
                            + "COUNT(*) FILTER (WHERE zhdate >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month' "
                            + "AND zhdate < date_trunc('month', CURRENT_DATE)) AS prev "
                            + "FROM " + table + " WHERE zhdate IS NOT NULL" + where);
            long cur = ((Number) r.get("cur")).longValue();
            long prev = ((Number) r.get("prev")).longValue();
            if (prev == 0) return cur > 0 ? 100.0 : null;
            return Math.round((cur - prev) * 1000.0 / prev) / 10.0;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Map<String, Object> trend(int days) {
        int range = Math.min(Math.max(days, 7), 90);
        Map<String, Object> out = new LinkedHashMap<>();

        // 报价金额趋势（按 zhdate 日期聚合）
        List<Map<String, Object>> quotation = safeQuery(
                "SELECT to_char(zhdate, 'MM-DD') AS day, COALESCE(SUM(saleprice), 0) AS amount "
                        + "FROM " + QUOTATION_TABLE + " WHERE zhdate >= CURRENT_DATE - INTERVAL '" + range + " days' "
                        + "GROUP BY to_char(zhdate, 'MM-DD'), zhdate::date ORDER BY zhdate::date");

        // 销售数量趋势（sl_sum=数量合计，已审核；销售单无金额字段）
        List<Map<String, Object>> sales = safeQuery(
                "SELECT to_char(zhdate, 'MM-DD') AS day, COALESCE(SUM(sl_sum), 0) AS quantity "
                        + "FROM " + SALES_ORDER_TABLE
                        + " WHERE state = '" + STATE_APPROVED + "' "
                        + "AND zhdate >= CURRENT_DATE - INTERVAL '" + range + " days' "
                        + "GROUP BY to_char(zhdate, 'MM-DD'), zhdate::date ORDER BY zhdate::date");

        out.put("days", range);
        out.put("quotation", quotation);
        out.put("sales", sales);
        return out;
    }

    @Override
    public List<Map<String, Object>> gongyidanStatus() {
        // 工艺单按货号类型 hhtype 分布（空类型归入"未分类"）
        return safeQuery(
                "SELECT COALESCE(NULLIF(TRIM(hhtype), ''), '未分类') AS name, COUNT(*) AS value "
                        + "FROM " + JFK_PROCESS_TABLE + " GROUP BY COALESCE(NULLIF(TRIM(hhtype), ''), '未分类') "
                        + "ORDER BY value DESC");
    }

    @Override
    public List<Map<String, Object>> recentQuotations(int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        List<Map<String, Object>> rows = safeQuery(
                "SELECT q.dh, q.zhdate, q.khname, q.huohao, q.saleprice, q.xscb, q.zpl, j.spname "
                        + "FROM " + QUOTATION_TABLE + " q "
                        + "LEFT JOIN (SELECT DISTINCT ON (huohao) huohao, spname FROM " + JFK_PROCESS_TABLE
                        + " WHERE huohao IS NOT NULL) j ON q.huohao = j.huohao "
                        + "ORDER BY q.zhdate DESC NULLS LAST LIMIT " + size);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dh", row.get("dh"));
            m.put("date", fmtDate(row.get("zhdate")));
            m.put("customer", row.get("khname"));
            m.put("huohao", row.get("huohao"));
            m.put("spname", row.get("spname"));
            m.put("saleprice", round2(row.get("saleprice")));
            m.put("cost", round2(row.get("xscb")));
            m.put("passRate", round2(row.get("zpl")));
            out.add(m);
        }
        return out;
    }

    @Override
    public List<Map<String, Object>> recentGongyidan(int limit) {
        int size = Math.min(Math.max(limit, 1), 50);
        // 最近工艺单 + 现有系统关联逻辑带出（原料BOM/机台产能/工序/工价条数，按货号聚合）
        List<Map<String, Object>> rows = safeQuery(
                "SELECT j.bh, j.hhtype, j.huohao, j.spname, j.designer, j.dw, j.rsjgh, "
                        + "(SELECT COUNT(*) FROM " + RAW_MATERIAL_TABLE + " r WHERE r.huohao = j.huohao) AS bom_count, "
                        + "(SELECT COUNT(*) FROM " + BUJ_COMPONENT_TABLE + " b WHERE b.hhname = j.huohao) AS machine_count, "
                        + "(SELECT COUNT(*) FROM " + GONGXU_PROCESS_TABLE + " p WHERE p.hhname = j.huohao) AS process_count, "
                        + "(SELECT COUNT(*) FROM " + GONGXU_PRICE_TABLE + " pr WHERE pr.hhname = j.huohao) AS price_count "
                        + "FROM " + JFK_PROCESS_TABLE + " j ORDER BY j.bh DESC LIMIT " + size);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bh", row.get("bh"));
            m.put("hhtype", row.get("hhtype"));
            m.put("huohao", row.get("huohao"));
            m.put("spname", row.get("spname"));
            m.put("designer", row.get("designer"));
            m.put("unit", row.get("dw"));
            m.put("rsjgh", row.get("rsjgh"));
            m.put("bomCount", ((Number) row.getOrDefault("bom_count", 0)).intValue());
            m.put("machineCount", ((Number) row.getOrDefault("machine_count", 0)).intValue());
            m.put("processCount", ((Number) row.getOrDefault("process_count", 0)).intValue());
            m.put("priceCount", ((Number) row.getOrDefault("price_count", 0)).intValue());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> safeQuery(String sql) {
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.warn("[三单据统计] 查询失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private Object round2(Object v) {
        if (v == null) return null;
        try {
            double d = Double.parseDouble(String.valueOf(v));
            return Math.round(d * 100.0) / 100.0;
        } catch (NumberFormatException e) {
            return v;
        }
    }

    private String fmtDate(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }
}
