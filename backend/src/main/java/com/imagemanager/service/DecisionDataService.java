package com.imagemanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 决策/企划模式结构化数据确定性检索服务
 *
 * 与报价单确定性查询（QuotationCalcService）同思路：
 * 大模型只负责理解问题与组织语言，产能数字、客户订单统计全部由本服务参数化 SQL 产出，
 * 以 type/summary/data 条目形式注入上下文（与 supplyChainResults 格式兼容）。
 *
 * 覆盖维度：
 * 1. 产能与排产（production_plan 表）：机台数、机型分布、单机日产量、货号产能明细
 * 2. 客户订单维度（order_bjd_query 表）：客户分组统计（单号数/平均售价/平均毛利/首末单日期）
 * 3. 业务员绩效维度：当前库内无业务员字段结构化数据源，注入"数据缺失说明"条目，
 *    由 prompt 规则约束模型如实提示而非编造（禁止行为清单第1条）
 */
@Slf4j
@Service
public class DecisionDataService {

    private static final String QUOTATION_TABLE = "order_bjd_query";
    private static final String PLAN_TABLE = "production_plan";
    private static final Pattern PRODUCT_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9]{3,}");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 按消息意图与子模式决定注入哪些结构化数据。
     *
     * @param message 用户消息
     * @param subMode 业务子模式（planning/decision/null=通用）
     * @return 结构化数据条目列表（type/summary/data），无意图时返回空
     */
    public List<Map<String, Object>> searchStructuredForMessage(String message, String subMode) {
        List<Map<String, Object>> out = new ArrayList<>();
        boolean decision = "decision".equals(subMode);
        boolean planning = "planning".equals(subMode);
        boolean finalDoc = isFinalDocIntent(message);
        boolean capacityIntent = decision || (planning && finalDoc)
                || containsAny(message, "排产", "产能", "机台", "交期", "排期", "空置", "人力缺口", "接单", "投产", "生产安排", "设备");
        boolean customerIntent = decision || (planning && finalDoc)
                || containsAny(message, "客户经营", "客户结构", "客户增减", "份额", "撬单", "保单", "放单", "复购", "流失", "客户订单", "订单匹配", "客户维度");
        boolean perfIntent = containsAny(message, "业务员效能", "业务员绩效", "人效", "业绩", "业务员能力", "客户分配", "业务员负载");

        // 通用工厂模式下无任何相关意图时不注入（避免无关数据引发幻觉）
        if (!decision && !planning && !capacityIntent && !customerIntent && !perfIntent) {
            return out;
        }

        if (capacityIntent) {
            out.addAll(queryCapacityOverview());
            String code = extractProductCode(message);
            if (code != null) {
                out.addAll(queryCapacityByProductCode(code));
            }
        }
        if (customerIntent) {
            out.addAll(queryCustomerOrderStats());
        }
        if (perfIntent) {
            // 业务员绩效无结构化数据源（报价单表无业务员字段），注入缺失说明，触发 prompt 禁止行为规则
            Map<String, Object> miss = new LinkedHashMap<>();
            miss.put("type", "数据缺失说明");
            miss.put("summary", "业务员绩效维度：当前结构化数据库中无业务员字段（报价单表仅含客户维度），无法产出业务员个人绩效指标");
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("缺失项", "业务员绩效结构化数据（报价单表无业务员归属字段）");
            data.put("可代理数据", "客户订单维度统计（按客户分组，可间接反映业务员负责客户的经营情况）");
            data.put("处理要求", "如实告知用户该维度数据缺失，建议补充业务员-客户归属数据后再做效能分析；禁止编造业务员个人业绩数字");
            miss.put("data", data);
            out.add(miss);
        }
        return out;
    }

    /** 终稿/完整报告意图：用户要求整合多轮内容一次性输出 */
    public boolean isFinalDocIntent(String message) {
        return containsAny(message, "终稿", "完整企划", "完整报告", "一次性输出", "汇总输出", "生成报告",
                "生成企划案", "输出企划", "整合", "V1.0", "定稿", "导出", "完整版");
    }

    // ====== 产能与排产（production_plan） ======

    /** 产能总览 + 机型分布 */
    private List<Map<String, Object>> queryCapacityOverview() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Map<String, Object> total = jdbcTemplate.queryForMap(
                    "SELECT COUNT(*) AS cnt, COALESCE(SUM(machine_count),0) AS machines FROM " + PLAN_TABLE);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("在产工艺款数", total.get("cnt"));
            data.put("机台总数", total.get("machines"));

            List<Map<String, Object>> byType = jdbcTemplate.queryForList(
                    "SELECT machine_type, COUNT(*) AS cnt, COALESCE(SUM(machine_count),0) AS machines, "
                            + "AVG(single_machine_output) AS avg_output, AVG(seconds) AS avg_seconds "
                            + "FROM " + PLAN_TABLE + " WHERE machine_type IS NOT NULL AND machine_type <> '' "
                            + "GROUP BY machine_type ORDER BY machines DESC LIMIT 15");
            StringBuilder typeSummary = new StringBuilder();
            for (Map<String, Object> row : byType) {
                String mt = String.valueOf(row.get("machine_type"));
                Object machines = row.get("machines");
                data.put("机型[" + mt + "]机台数", machines);
                data.put("机型[" + mt + "]平均单机日产量", round2(row.get("avg_output")));
                data.put("机型[" + mt + "]平均下机时间(秒)", round2(row.get("avg_seconds")));
                typeSummary.append(mt).append("(").append(machines).append("台) ");
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "产能排产总览");
            entry.put("summary", "排产表产能总览：在产工艺款数 " + total.get("cnt")
                    + "，机台总数 " + total.get("machines") + "，机型分布 " + typeSummary.toString().trim());
            entry.put("data", data);
            out.add(entry);
            log.info("[结构化数据] 产能排产总览: 款数={}, 机台数={}, 机型数={}", total.get("cnt"), total.get("machines"), byType.size());
        } catch (Exception e) {
            log.warn("[结构化数据] 产能排产总览查询失败: {}", e.getMessage());
        }
        return out;
    }

    /** 按货号查询产能明细（排产/下机时间/单机日产量） */
    private List<Map<String, Object>> queryCapacityByProductCode(String code) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT semi_product_code, product_code, machine_type, needle_count, seconds, "
                            + "machine_count, single_machine_output, sewing_weight FROM " + PLAN_TABLE
                            + " WHERE product_code ILIKE ? OR semi_product_code ILIKE ? LIMIT 20",
                    "%" + code + "%", "%" + code + "%");
            for (Map<String, Object> row : rows) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("半成品编码", row.get("semi_product_code"));
                data.put("产品编码", row.get("product_code"));
                data.put("机型", row.get("machine_type"));
                data.put("针数", row.get("needle_count"));
                data.put("下机时间(秒)", row.get("seconds"));
                data.put("投入机台数", row.get("machine_count"));
                data.put("单机日产量", row.get("single_machine_output"));
                data.put("缝拼克重", row.get("sewing_weight"));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "排产产能明细");
                entry.put("summary", "货号 " + row.get("product_code") + " 排产产能数据");
                entry.put("data", data);
                out.add(entry);
            }
            if (!rows.isEmpty()) {
                log.info("[结构化数据] 货号产能明细命中: code={}, 条数={}", code, rows.size());
            }
        } catch (Exception e) {
            log.warn("[结构化数据] 货号产能明细查询失败: {}", e.getMessage());
        }
        return out;
    }

    // ====== 客户订单维度（order_bjd_query 按客户分组统计） ======

    /** 客户订单总览 + 客户分组统计（单号数/平均售价/平均毛利/首末单日期） */
    private List<Map<String, Object>> queryCustomerOrderStats() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Map<String, Object> total = jdbcTemplate.queryForMap(
                    "SELECT COUNT(DISTINCT khname) AS customers, COUNT(DISTINCT dh) AS orders FROM "
                            + QUOTATION_TABLE + " WHERE khname IS NOT NULL AND khname <> ''");

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT khname, COUNT(DISTINCT dh) AS order_cnt, AVG(saleprice) AS avg_price, "
                            + "AVG(xscb) AS avg_cost, AVG(mlr_dp) AS avg_profit, "
                            + "MAX(zhdate) AS last_date, MIN(zhdate) AS first_date "
                            + "FROM " + QUOTATION_TABLE + " WHERE khname IS NOT NULL AND khname <> '' "
                            + "GROUP BY khname ORDER BY order_cnt DESC LIMIT 30");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("客户总数", total.get("customers"));
            data.put("单号总数", total.get("orders"));
            StringBuilder top = new StringBuilder();
            int idx = 0;
            for (Map<String, Object> row : rows) {
                idx++;
                String name = String.valueOf(row.get("khname"));
                data.put("客户" + idx + "[" + name + "]单号数", row.get("order_cnt"));
                data.put("客户" + idx + "[" + name + "]平均售价", round2(row.get("avg_price")));
                data.put("客户" + idx + "[" + name + "]平均销售成本", round2(row.get("avg_cost")));
                data.put("客户" + idx + "[" + name + "]平均单品毛利", round2(row.get("avg_profit")));
                data.put("客户" + idx + "[" + name + "]首单日期", fmtDate(row.get("first_date")));
                data.put("客户" + idx + "[" + name + "]最近单日期", fmtDate(row.get("last_date")));
                if (idx <= 5) {
                    top.append(name).append("(").append(row.get("order_cnt")).append("单) ");
                }
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "客户订单维度统计");
            entry.put("summary", "客户订单总览：客户总数 " + total.get("customers") + "，单号总数 "
                    + total.get("orders") + "，TOP客户 " + top.toString().trim());
            entry.put("data", data);
            out.add(entry);
            log.info("[结构化数据] 客户订单维度统计: 客户数={}, 单号数={}, 分组数={}", total.get("customers"), total.get("orders"), rows.size());
        } catch (Exception e) {
            log.warn("[结构化数据] 客户订单维度统计查询失败: {}", e.getMessage());
        }
        return out;
    }

    // ====== 工具方法 ======

    private boolean containsAny(String message, String... kws) {
        if (message == null) return false;
        for (String kw : kws) {
            if (message.contains(kw)) return true;
        }
        return false;
    }

    /** 从问题中提取可能的货号（字母开头的字母数字组合，长度>=4） */
    private String extractProductCode(String message) {
        if (message == null) return null;
        Matcher m = PRODUCT_CODE.matcher(message);
        while (m.find()) {
            String token = m.group();
            // 排除常见英文词与纯单词
            if (!token.matches("(?i)plan|mode|excel|pdf|sku|v1|no|date|time")) {
                return token;
            }
        }
        return null;
    }

    private Object round2(Object v) {
        if (v instanceof Number) {
            return new java.math.BigDecimal(((Number) v).doubleValue())
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return v;
    }

    private String fmtDate(Object v) {
        if (v instanceof Timestamp) {
            return new SimpleDateFormat("yyyy-MM-dd").format((Timestamp) v);
        }
        return v != null ? String.valueOf(v) : "";
    }
}
