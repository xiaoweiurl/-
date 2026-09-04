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
 * 3. 业务员绩效维度（order_xs_list 表 ywyname 字段，V45 起）：业务员分组统计
 *    （单量/订单数量合计/客户覆盖/首末单日期），V45 前无数据源时注入"数据缺失说明"兜底
 * 4. 销售订单需求与交期（order_xs_list 表）：真实订单需求(sl_sum)、交期(jh_date)、
 *    审核/计划状态，与产能供给侧构成"产能-订单匹配"闭环
 * 5. 工艺单参数（order_sw_gongyidan 表）：按货号查下机克重/下机秒数/制成率/机型/针数/
 *    理论产量/缝拼克重(pfkz)，工艺类问题的确定性依据
 * 6. 货号全链路关联（V49+）：以生产货号为统一关联键，拉通丝袜工艺单(order_sw_gongyidan)
 *    + 内衣工艺单(order_jfk_gongyidan) + 销售订单(order_xs_list: 业务员ywyname/客户khname)
 *    + 产品报价信息(order_bjd_query) + 采购原料BOM(raw_material_warehouse: 物料/供应商/用量/损耗)
 *    + 机台产能(order_buj_component: 机型/理论产量) + 工序工价(order_gongxu_process+order_gongxu_price)，
 *    品名/客户名等字段仅当数据非空时带出
 * 7. 商品库文件夹关联（goods_library）：按货号匹配品名+货号命名的商品文件夹，
 *    带出文件夹信息（发起人/打样员/客户/订单号/备注）与主图/侧面图/细节图/产品图签名URL
 */
@Slf4j
@Service
public class DecisionDataService {

    private static final String QUOTATION_TABLE = "order_bjd_query";
    private static final String PLAN_TABLE = "production_plan";
    private static final String SALES_ORDER_TABLE = "order_xs_list";
    private static final String PROCESS_TABLE = "order_sw_gongyidan";
    private static final String JFK_PROCESS_TABLE = "order_jfk_gongyidan";
    /** 内衣货号工艺部件表（机台机型/理论产量），关联键 hhname=货号 */
    private static final String BUJ_COMPONENT_TABLE = "order_buj_component";
    /** 内衣货号工艺工序表（工序名称/机种/针数/用时），关联键 hhname=货号 */
    private static final String GONGXU_PROCESS_TABLE = "order_gongxu_process";
    /** 内衣货号工序工价表（技术工价/工价/临时工价），关联键 hhname+wtname=货号+工序 */
    private static final String GONGXU_PRICE_TABLE = "order_gongxu_price";
    /** 原料入库表（采购原料品种/供应商/单件用量/损耗率），关联键 huohao=成品货号 */
    private static final String RAW_MATERIAL_TABLE = "raw_material_warehouse";
    private static final Pattern PRODUCT_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9]{3,}");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 对象存储服务（生成商品库图片签名 URL），本地存储实现不可用时降级为仅标注已上传 */
    @Autowired(required = false)
    private FileStorageService fileStorageService;

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
        boolean processIntent = containsAny(message, "工艺", "克重", "针数", "机型", "制成率", "下机秒数", "理论产量", "打样", "工艺单");
        boolean deliveryIntent = containsAny(message, "交期", "交货", "延期", "逾期", "未下计划", "终审", "交付风险");

        // 通用工厂模式下无任何相关意图时不注入（避免无关数据引发幻觉）
        if (!decision && !planning && !capacityIntent && !customerIntent && !perfIntent && !processIntent && !deliveryIntent) {
            return out;
        }

        if (capacityIntent) {
            out.addAll(queryCapacityOverview());
            String code = extractProductCode(message);
            if (code != null) {
                out.addAll(queryCapacityByProductCode(code));
            }
            // 需求侧：销售订单需求与交期（产能-订单匹配闭环）
            out.addAll(queryOrderDemand());
        }
        if (customerIntent) {
            out.addAll(queryCustomerOrderStats());
            // 真实销售订单维度（报价≠成交）
            out.addAll(querySalesOrderStats());
        }
        if (deliveryIntent) {
            out.addAll(queryOrderDemand());
        }
        if (perfIntent) {
            // V45 起：order_xs_list.ywyname 提供业务员维度真数据；空结果时回退缺失说明
            List<Map<String, Object>> perf = querySalespersonPerformance();
            if (!perf.isEmpty()) {
                out.addAll(perf);
            } else {
                Map<String, Object> miss = new LinkedHashMap<>();
                miss.put("type", "数据缺失说明");
                miss.put("summary", "业务员绩效维度：销售订单表(order_xs_list)暂无业务员数据，无法产出业务员个人绩效指标");
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("缺失项", "业务员绩效结构化数据（order_xs_list.ywyname 为空或表未同步）");
                data.put("可代理数据", "客户订单维度统计（按客户分组，可间接反映业务员负责客户的经营情况）");
                data.put("处理要求", "如实告知用户该维度数据缺失，建议同步销售订单数据后再做效能分析；禁止编造业务员个人业绩数字");
                miss.put("data", data);
                out.add(miss);
            }
        }
        // 货号全链路关联：有货号且（工艺/原料/工序工价/机台产能等 ERP 业务维度意图 / 企划 / 决策模式）时注入
        // （丝袜工艺单 + 内衣工艺单 + 销售订单[业务员/客户名/品名] + 产品报价信息 + 商品库文件夹
        //   + 采购原料BOM + 机台产能[部件工艺] + 工序工价）
        boolean erpBizIntent = processIntent || containsAny(message,
                "原料", "用料", "物料", "BOM", "供应商", "工序", "工价", "机台", "产能", "理论产量", "损耗");
        if (erpBizIntent || decision || (planning && finalDoc)) {
            String code = extractProductCode(message);
            if (code != null) {
                out.addAll(queryHuohaoFullChain(code));
            }
        } else {
            // 商品库/图片意图独立触发：仅查商品库文件夹（品名+货号命名，含图片签名URL），不拉全链路
            boolean goodsLibraryIntent = containsAny(message,
                    "商品库", "文件夹", "主图", "侧面图", "细节图", "产品图", "商品图片", "商品图");
            if (goodsLibraryIntent) {
                String code = extractProductCode(message);
                if (code != null) {
                    out.addAll(queryGoodsLibraryByHuohao(code));
                }
            }
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

            // 均值仅统计可信区间(0,10000)，防止上游脏数据（订单总额/时间戳混入 saleprice）毒化 LLM 回答
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT khname, COUNT(DISTINCT dh) AS order_cnt, "
                            + "AVG(saleprice) FILTER (WHERE saleprice > 0 AND saleprice < 10000) AS avg_price, "
                            + "AVG(xscb) FILTER (WHERE xscb > 0 AND xscb < 10000) AS avg_cost, "
                            + "AVG(mlr_dp) FILTER (WHERE mlr_dp > -10000 AND mlr_dp < 10000) AS avg_profit, "
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

    // ====== 业务员绩效维度（order_xs_list 按业务员分组统计，V45） ======

    /** 业务员绩效：单量/订单数量合计/客户覆盖/首末单日期（ywyname 分组） */
    private List<Map<String, Object>> querySalespersonPerformance() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT ywyname, COUNT(DISTINCT dh) AS order_cnt, COALESCE(SUM(sl_sum),0) AS qty, "
                            + "COUNT(DISTINCT khname) AS customers, "
                            + "MAX(zhdate) AS last_date, MIN(zhdate) AS first_date "
                            + "FROM " + SALES_ORDER_TABLE + " WHERE ywyname IS NOT NULL AND ywyname <> '' "
                            + "GROUP BY ywyname ORDER BY order_cnt DESC LIMIT 20");
            if (rows.isEmpty()) return out;

            Map<String, Object> data = new LinkedHashMap<>();
            StringBuilder top = new StringBuilder();
            int idx = 0;
            for (Map<String, Object> row : rows) {
                idx++;
                String name = String.valueOf(row.get("ywyname"));
                data.put("业务员" + idx + "[" + name + "]单号数", row.get("order_cnt"));
                data.put("业务员" + idx + "[" + name + "]订单数量合计", row.get("qty"));
                data.put("业务员" + idx + "[" + name + "]覆盖客户数", row.get("customers"));
                data.put("业务员" + idx + "[" + name + "]首单日期", fmtDate(row.get("first_date")));
                data.put("业务员" + idx + "[" + name + "]最近单日期", fmtDate(row.get("last_date")));
                if (idx <= 5) {
                    top.append(name).append("(").append(row.get("order_cnt")).append("单/").append(row.get("qty")).append("件) ");
                }
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "业务员绩效统计");
            entry.put("summary", "业务员绩效总览（销售订单表真实数据）：业务员 " + rows.size()
                    + " 人，TOP " + top.toString().trim());
            entry.put("data", data);
            out.add(entry);
            log.info("[结构化数据] 业务员绩效统计: 业务员数={}", rows.size());
        } catch (Exception e) {
            log.warn("[结构化数据] 业务员绩效统计查询失败: {}", e.getMessage());
        }
        return out;
    }

    // ====== 销售订单需求与交期（order_xs_list，V45） ======

    /** 销售订单维度客户统计（真实成交，区别于报价维度） */
    private List<Map<String, Object>> querySalesOrderStats() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            Map<String, Object> total = jdbcTemplate.queryForMap(
                    "SELECT COUNT(DISTINCT khname) AS customers, COUNT(DISTINCT dh) AS orders, "
                            + "COALESCE(SUM(sl_sum),0) AS qty FROM " + SALES_ORDER_TABLE
                            + " WHERE khname IS NOT NULL AND khname <> ''");

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT khname, COUNT(DISTINCT dh) AS order_cnt, COALESCE(SUM(sl_sum),0) AS qty, "
                            + "MAX(zhdate) AS last_date, MAX(jh_date) AS latest_delivery "
                            + "FROM " + SALES_ORDER_TABLE + " WHERE khname IS NOT NULL AND khname <> '' "
                            + "GROUP BY khname ORDER BY qty DESC LIMIT 30");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("成交客户总数", total.get("customers"));
            data.put("销售订单总数", total.get("orders"));
            data.put("订单数量总计", total.get("qty"));
            StringBuilder top = new StringBuilder();
            int idx = 0;
            for (Map<String, Object> row : rows) {
                idx++;
                String name = String.valueOf(row.get("khname"));
                data.put("客户" + idx + "[" + name + "]订单数", row.get("order_cnt"));
                data.put("客户" + idx + "[" + name + "]数量合计", row.get("qty"));
                data.put("客户" + idx + "[" + name + "]最近下单", fmtDate(row.get("last_date")));
                data.put("客户" + idx + "[" + name + "]最晚交期", fmtDate(row.get("latest_delivery")));
                if (idx <= 5) {
                    top.append(name).append("(").append(row.get("qty")).append("件) ");
                }
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "销售订单维度统计");
            entry.put("summary", "销售订单总览（真实成交）：客户 " + total.get("customers") + "，订单 "
                    + total.get("orders") + " 单，数量 " + total.get("qty") + "，TOP客户 " + top.toString().trim());
            entry.put("data", data);
            out.add(entry);
            log.info("[结构化数据] 销售订单维度统计: 客户数={}, 订单数={}", total.get("customers"), total.get("orders"));
        } catch (Exception e) {
            log.warn("[结构化数据] 销售订单维度统计查询失败: {}", e.getMessage());
        }
        return out;
    }

    /** 订单需求与交期：未来待交付订单（产能-订单匹配的需求侧 + 交期风险） */
    private List<Map<String, Object>> queryOrderDemand() {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT dh, khname, detailhuohao, sl_sum, jh_date, sfplan, zxtate, ddtype "
                            + "FROM " + SALES_ORDER_TABLE
                            + " WHERE jh_date IS NOT NULL AND jh_date >= CURRENT_DATE "
                            + "ORDER BY jh_date ASC LIMIT 20");
            if (rows.isEmpty()) return out;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("待交付订单数(交期在未来)", rows.size());
            int idx = 0;
            java.math.BigDecimal totalQty = java.math.BigDecimal.ZERO;
            for (Map<String, Object> row : rows) {
                idx++;
                if (row.get("sl_sum") instanceof Number) {
                    totalQty = totalQty.add(new java.math.BigDecimal(row.get("sl_sum").toString()));
                }
                if (idx <= 10) {
                    String key = "订单" + idx + "[" + row.get("dh") + "/" + row.get("khname") + "]";
                    data.put(key + "货号", row.get("detailhuohao"));
                    data.put(key + "数量", row.get("sl_sum"));
                    data.put(key + "交期", fmtDate(row.get("jh_date")));
                    data.put(key + "是否下计划", row.get("sfplan"));
                    data.put(key + "执行状态", row.get("zxtate"));
                }
            }
            data.put("待交付数量合计", totalQty);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", "订单需求与交期");
            entry.put("summary", "待交付订单（需求侧）：" + rows.size() + " 单，数量合计 " + totalQty
                    + "，最近交期 " + fmtDate(rows.get(0).get("jh_date")));
            entry.put("data", data);
            out.add(entry);
            log.info("[结构化数据] 订单需求与交期: 待交付单数={}", rows.size());
        } catch (Exception e) {
            log.warn("[结构化数据] 订单需求与交期查询失败: {}", e.getMessage());
        }
        return out;
    }

    // ====== 工艺单参数（order_sw_gongyidan，V45） ======

    /** 按货号查询工艺单参数（克重/秒数/制成率/机型/针数/理论产量/缝拼克重） */
    private List<Map<String, Object>> queryProcessParams(String code) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT bh, huohao, spname, xjkz, xjsl, pfkz, cpkz, zcl, jix, zs, djcl, "
                            + "hhywy, qd_dys, hd_dys FROM " + PROCESS_TABLE
                            + " WHERE huohao ILIKE ? LIMIT 10",
                    "%" + code + "%");
            for (Map<String, Object> row : rows) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("编号", row.get("bh"));
                data.put("生产货号", row.get("huohao"));
                data.put("品名", row.get("spname"));
                data.put("下机克重", row.get("xjkz"));
                data.put("下机秒数", row.get("xjsl"));
                data.put("缝拼克重(pfkz)", row.get("pfkz"));
                data.put("成品克重", row.get("cpkz"));
                data.put("制成率", row.get("zcl"));
                data.put("机型", row.get("jix"));
                data.put("针数", row.get("zs"));
                data.put("理论产量", row.get("djcl"));
                data.put("业务员", row.get("hhywy"));
                data.put("前道打样师", row.get("qd_dys"));
                data.put("后道打样师", row.get("hd_dys"));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "工艺单参数");
                entry.put("summary", "货号 " + row.get("huohao") + " 工艺单参数（下机克重 " + row.get("xjkz")
                        + "，下机秒数 " + row.get("xjsl") + "，理论产量 " + row.get("djcl") + "）");
                entry.put("data", data);
                out.add(entry);
            }
            if (!rows.isEmpty()) {
                log.info("[结构化数据] 工艺单参数命中: code={}, 条数={}", code, rows.size());
            }
        } catch (Exception e) {
            log.warn("[结构化数据] 工艺单参数查询失败: {}", e.getMessage());
        }
        return out;
    }

    // ====== 货号全链路关联（丝袜工艺单 + 内衣工艺单 + 销售订单 + 产品报价信息，V49） ======

    /**
     * 货号全链路关联查询：以生产货号为统一关联键，拉通
     * ①丝袜工艺单(order_sw_gongyidan) ②内衣工艺单(order_jfk_gongyidan)
     * ③销售订单(order_xs_list: 业务员ywyname/客户khname/品名/数量/交期)
     * ④产品报价信息(order_bjd_query 最近记录: 客户/售价/销售成本/尺码)
     * 品名/客户名等字段仅当数据非空时带出。
     */
    private List<Map<String, Object>> queryHuohaoFullChain(String code) {
        List<Map<String, Object>> out = new ArrayList<>();
        out.addAll(queryProcessParams(code));       // ①丝袜工艺单
        out.addAll(queryJfkProcessParams(code));    // ②内衣工艺单
        out.addAll(querySalesOrdersByHuohao(code)); // ③销售订单（业务员/客户/品名）
        out.addAll(queryProductQuoteInfo(code));    // ④产品报价信息
        out.addAll(queryGoodsLibraryByHuohao(code));// ⑤商品库文件夹（品名+货号命名，含图片签名URL）
        out.addAll(queryRawMaterialBom(code));      // ⑥采购原料BOM（原料品种/供应商/单件用量/损耗率）
        out.addAll(queryBujMachineCapacity(code));  // ⑦机台产能（机型/针数/克重/理论产量，按部件）
        out.addAll(queryGongxuProcessPrice(code));  // ⑧工序工价（工序参数+技术工价/工价/临时工价）
        return out;
    }

    /**
     * 按货号关联商品库文件夹（goods_library）。
     * 文件夹命名规则 = 货号+品名，故用货号同时匹配 goods_no 与 folder_name；
     * 带出文件夹信息（发起人/打样员/品名/客户/订单号/备注）与四类图片
     * （主图/侧面图/细节图/产品图）的 24h 签名 URL，供 LLM 综合回答与 markdown 展示。
     */
    private List<Map<String, Object>> queryGoodsLibraryByHuohao(String code) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, folder_name, initiator, sampler, product_name, goods_no, customer, order_no, "
                            + "main_image_key, side_image_key, detail_image_key, product_image_key, remark "
                            + "FROM goods_library "
                            + "WHERE goods_no ILIKE ? OR folder_name ILIKE ? ORDER BY created_at DESC LIMIT 5",
                    "%" + code + "%", "%" + code + "%");
            String[][] slots = {
                    {"main_image_key", "主图"}, {"side_image_key", "侧面图"},
                    {"detail_image_key", "细节图"}, {"product_image_key", "产品图"}
            };
            for (Map<String, Object> row : rows) {
                Map<String, Object> data = new LinkedHashMap<>();
                putIfNonBlank(data, "文件夹名称", row.get("folder_name"));
                putIfNonBlank(data, "品名", row.get("product_name"));
                putIfNonBlank(data, "货号", row.get("goods_no"));
                putIfNonBlank(data, "发起人", row.get("initiator"));
                putIfNonBlank(data, "打样员", row.get("sampler"));
                putIfNonBlank(data, "客户", row.get("customer"));
                putIfNonBlank(data, "订单号", row.get("order_no"));
                putIfNonBlank(data, "备注", row.get("remark"));
                int imageCount = 0;
                List<String> uploaded = new ArrayList<>();
                for (String[] slot : slots) {
                    Object keyObj = row.get(slot[0]);
                    if (keyObj == null || String.valueOf(keyObj).trim().isEmpty()) continue;
                    imageCount++;
                    uploaded.add(slot[1]);
                    String url = signImageUrl(String.valueOf(keyObj));
                    if (url != null) {
                        data.put(slot[1] + "图片URL", url);
                    } else {
                        data.put(slot[1], "已上传（签名URL生成失败）");
                    }
                }
                putIfNonBlank(data, "已上传图片", uploaded.isEmpty() ? null : String.join("、", uploaded));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "商品库文件夹");
                entry.put("summary", "商品库文件夹「" + row.get("folder_name") + "」（品名 " + row.get("product_name")
                        + "，货号 " + row.get("goods_no") + "，已传图片 " + imageCount + "/4 张）");
                entry.put("data", data);
                out.add(entry);
            }
            if (!rows.isEmpty()) {
                log.info("[结构化数据] 商品库文件夹命中: code={}, 条数={}", code, rows.size());
            }
        } catch (Exception e) {
            log.warn("[结构化数据] 商品库文件夹查询失败: {}", e.getMessage());
        }
        return out;
    }

    /** 生成商品库图片 24h 签名 URL，存储服务不可用或生成失败时返回 null（降级） */
    private String signImageUrl(String key) {
        if (fileStorageService == null) return null;
        try {
            return fileStorageService.generatePresignedUrl(key, 86400);
        } catch (Exception e) {
            log.warn("[结构化数据] 商品库图片签名URL生成失败: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /** 按货号查询内衣工艺单（order_jfk_gongyidan：品名/设计师/单位/染色厂/打样师/打样版号），非空字段才带出 */
    private List<Map<String, Object>> queryJfkProcessParams(String code) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT bh, hhtype, huohao, spname, designer, dw, rsjgh, qd_dys, hd_dys, dybanhao, remark FROM "
                            + JFK_PROCESS_TABLE + " WHERE huohao ILIKE ? LIMIT 10",
                    "%" + code + "%");
            for (Map<String, Object> row : rows) {
                Map<String, Object> data = new LinkedHashMap<>();
                putIfNonBlank(data, "编号", row.get("bh"));
                putIfNonBlank(data, "生产货号", row.get("huohao"));
                putIfNonBlank(data, "货号类别", row.get("hhtype"));
                putIfNonBlank(data, "品名", row.get("spname"));
                putIfNonBlank(data, "设计师", row.get("designer"));
                putIfNonBlank(data, "单位", row.get("dw"));
                putIfNonBlank(data, "染色厂", row.get("rsjgh"));
                putIfNonBlank(data, "前道打样师", row.get("qd_dys"));
                putIfNonBlank(data, "后道打样师", row.get("hd_dys"));
                putIfNonBlank(data, "打样版号", row.get("dybanhao"));
                putIfNonBlank(data, "备注", row.get("remark"));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "内衣工艺单");
                entry.put("summary", "货号 " + row.get("huohao") + " 内衣工艺单（品名 " + row.get("spname")
                        + "，设计师 " + row.get("designer") + "）");
                entry.put("data", data);
                out.add(entry);
            }
            if (!rows.isEmpty()) {
                log.info("[结构化数据] 内衣工艺单命中: code={}, 条数={}", code, rows.size());
            }
        } catch (Exception e) {
            log.warn("[结构化数据] 内衣工艺单查询失败: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 按货号查询采购原料BOM（raw_material_warehouse：部件/物料名称/规格/供应商/单件用量/损耗率），
     * 关联键 huohao=成品货号；一条 BOM 记录一个条目，非空字段才带出
     */
    private List<Map<String, Object>> queryRawMaterialBom(String code) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT huohao, color, size, component, supplier, material_name, specification,"
                            + " material_color, batch_no, twist_direction, unit, usage_per_unit, loss_rate, remark FROM "
                            + RAW_MATERIAL_TABLE + " WHERE huohao ILIKE ? ORDER BY component, material_name LIMIT 20",
                    "%" + code + "%");
            for (Map<String, Object> row : rows) {
                Map<String, Object> data = new LinkedHashMap<>();
                putIfNonBlank(data, "成品货号", row.get("huohao"));
                putIfNonBlank(data, "颜色", row.get("color"));
                putIfNonBlank(data, "尺码", row.get("size"));
                putIfNonBlank(data, "部件", row.get("component"));
                putIfNonBlank(data, "物料名称", row.get("material_name"));
                putIfNonBlank(data, "规格", row.get("specification"));
                putIfNonBlank(data, "供应商", row.get("supplier"));
                putIfNonBlank(data, "物料颜色", row.get("material_color"));
                putIfNonBlank(data, "批号", row.get("batch_no"));
                putIfNonBlank(data, "捻向", row.get("twist_direction"));
                putIfNonBlank(data, "单位", row.get("unit"));
                putIfNonBlank(data, "单件用量", row.get("usage_per_unit"));
                putIfNonBlank(data, "损耗率%", row.get("loss_rate"));
                putIfNonBlank(data, "备注", row.get("remark"));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "采购原料BOM");
                entry.put("summary", "货号 " + row.get("huohao") + " 采购原料（部件 " + row.get("component")
                        + "，物料 " + row.get("material_name") + " " + row.get("specification")
                        + "，供应商 " + row.get("supplier") + "）");
                entry.put("data", data);
                out.add(entry);
            }
            if (!rows.isEmpty()) {
                log.info("[结构化数据] 采购原料BOM命中: code={}, 条数={}", code, rows.size());
            }
        } catch (Exception e) {
            log.warn("[结构化数据] 采购原料BOM查询失败: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 按货号查询机台产能（order_buj_component：部件/机型/针数/克重/理论产量/织造难度），
     * 关联键 hhname=货号；一个部件工艺记录一个条目，非空字段才带出
     */
    private List<Map<String, Object>> queryBujMachineCapacity(String code) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT hhname, color, chima, buj, zbj, jix, zs, cxm, tongjing, kez, xjtime, llcl, zznd, remark FROM "
                            + BUJ_COMPONENT_TABLE + " WHERE hhname ILIKE ? ORDER BY zbj DESC NULLS LAST, buj LIMIT 20",
                    "%" + code + "%");
            for (Map<String, Object> row : rows) {
                Map<String, Object> data = new LinkedHashMap<>();
                putIfNonBlank(data, "货号", row.get("hhname"));
                putIfNonBlank(data, "颜色", row.get("color"));
                putIfNonBlank(data, "尺码", row.get("chima"));
                putIfNonBlank(data, "部件", row.get("buj"));
                Object zbj = row.get("zbj");
                if (zbj != null) {
                    data.put("主部件", "1".equals(String.valueOf(zbj).trim()) ? "是" : "否");
                }
                putIfNonBlank(data, "机型", row.get("jix"));
                putIfNonBlank(data, "针数", row.get("zs"));
                putIfNonBlank(data, "程序名", row.get("cxm"));
                putIfNonBlank(data, "口径", row.get("tongjing"));
                putIfNonBlank(data, "克重", row.get("kez"));
                putIfNonBlank(data, "下机时间", row.get("xjtime"));
                putIfNonBlank(data, "理论产量", row.get("llcl"));
                putIfNonBlank(data, "织造难度", row.get("zznd"));
                putIfNonBlank(data, "备注", row.get("remark"));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "机台产能（部件工艺）");
                entry.put("summary", "货号 " + row.get("hhname") + " 部件「" + row.get("buj")
                        + "」机台产能（机型 " + row.get("jix") + "，理论产量 " + row.get("llcl") + "）");
                entry.put("data", data);
                out.add(entry);
            }
            if (!rows.isEmpty()) {
                log.info("[结构化数据] 机台产能命中: code={}, 条数={}", code, rows.size());
            }
        } catch (Exception e) {
            log.warn("[结构化数据] 机台产能查询失败: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 按货号查询工序工价（order_gongxu_process FULL OUTER JOIN order_gongxu_price ON 货号+工序）：
     * 工序参数（机种/针目/针号/针数/用时）与工价（技术工价/工价/临时工价）合并为一个条目；
     * 仅有工序参数或仅有工价的工序也会带出，非空字段才展示
     */
    private List<Map<String, Object>> queryGongxuProcessPrice(String code) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            String like = "%" + code + "%";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT COALESCE(p.hhname, pr.hhname) AS hhname, COALESCE(p.wtname, pr.wtname) AS wtname,"
                            + " p.jizhong, p.zhenju, p.zhenhao, p.zhenmu, p.zhens, p.zline, p.sline, p.yongl, p.yongl2,"
                            + " p.sort, p.using_state, p.zhgx, p.tims,"
                            + " pr.jsprice, pr.price, pr.tempworker_price, pr.remarkgz, pr.state AS price_state FROM "
                            + GONGXU_PROCESS_TABLE + " p FULL OUTER JOIN " + GONGXU_PRICE_TABLE + " pr"
                            + " ON p.hhname = pr.hhname AND p.wtname = pr.wtname"
                            + " WHERE p.hhname ILIKE ? OR pr.hhname ILIKE ?"
                            + " ORDER BY p.sort NULLS LAST, COALESCE(p.wtname, pr.wtname) LIMIT 30",
                    like, like);
            for (Map<String, Object> row : rows) {
                Map<String, Object> data = new LinkedHashMap<>();
                putIfNonBlank(data, "货号", row.get("hhname"));
                putIfNonBlank(data, "工序", row.get("wtname"));
                putIfNonBlank(data, "机种", row.get("jizhong"));
                putIfNonBlank(data, "针目", row.get("zhenju"));
                putIfNonBlank(data, "针号", row.get("zhenhao"));
                putIfNonBlank(data, "针距", row.get("zhenmu"));
                putIfNonBlank(data, "针数", row.get("zhens"));
                putIfNonBlank(data, "缝线上", row.get("zline"));
                putIfNonBlank(data, "缝线下", row.get("sline"));
                putIfNonBlank(data, "用量/CM上", row.get("yongl"));
                putIfNonBlank(data, "用量/CM下", row.get("yongl2"));
                putIfNonBlank(data, "用时(秒)", row.get("tims"));
                putIfNonBlank(data, "使用中", row.get("using_state"));
                putIfNonBlank(data, "最后工序", row.get("zhgx"));
                putIfNonBlank(data, "技术工价", row.get("jsprice"));
                putIfNonBlank(data, "工价", row.get("price"));
                putIfNonBlank(data, "临时工价", row.get("tempworker_price"));
                putIfNonBlank(data, "工价备注", row.get("remarkgz"));
                putIfNonBlank(data, "工价审核状态", row.get("price_state"));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "工序工价");
                entry.put("summary", "货号 " + row.get("hhname") + " 工序「" + row.get("wtname")
                        + "」（机种 " + row.get("jizhong") + "，工价 " + row.get("price") + "）");
                entry.put("data", data);
                out.add(entry);
            }
            if (!rows.isEmpty()) {
                log.info("[结构化数据] 工序工价命中: code={}, 条数={}", code, rows.size());
            }
        } catch (Exception e) {
            log.warn("[结构化数据] 工序工价查询失败: {}", e.getMessage());
        }
        return out;
    }

    /** 按生产货号查询销售订单（order_xs_list：单号/客户名/业务员/成品货号/数量/交期），非空字段才带出 */
    private List<Map<String, Object>> querySalesOrdersByHuohao(String code) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT dh, zhdate, jh_date, khname, detailhuohao, detailhuohaocp, sl_sum, ywyname, ddtype "
                            + "FROM " + SALES_ORDER_TABLE
                            + " WHERE detailhuohao ILIKE ? ORDER BY zhdate DESC NULLS LAST LIMIT 20",
                    "%" + code + "%");
            for (Map<String, Object> row : rows) {
                Map<String, Object> data = new LinkedHashMap<>();
                putIfNonBlank(data, "订单号", row.get("dh"));
                putIfNonBlank(data, "下单日期", row.get("zhdate"));
                putIfNonBlank(data, "交货日期", row.get("jh_date"));
                putIfNonBlank(data, "客户名", row.get("khname"));
                putIfNonBlank(data, "生产货号", row.get("detailhuohao"));
                putIfNonBlank(data, "成品货号", row.get("detailhuohaocp"));
                putIfNonBlank(data, "订单数量", row.get("sl_sum"));
                putIfNonBlank(data, "业务员", row.get("ywyname"));
                putIfNonBlank(data, "销售类型", row.get("ddtype"));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "销售订单");
                entry.put("summary", "货号 " + row.get("detailhuohao") + " 销售订单（业务员 " + row.get("ywyname")
                        + "，客户 " + row.get("khname") + "，数量 " + row.get("sl_sum") + "）");
                entry.put("data", data);
                out.add(entry);
            }
            if (!rows.isEmpty()) {
                log.info("[结构化数据] 销售订单命中: code={}, 条数={}", code, rows.size());
            }
        } catch (Exception e) {
            log.warn("[结构化数据] 销售订单查询失败: {}", e.getMessage());
        }
        return out;
    }

    /** 按生产货号查询产品报价信息（order_bjd_query 最近记录：客户/售价/销售成本/尺码），非空字段才带出 */
    private List<Map<String, Object>> queryProductQuoteInfo(String code) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT dh, zhdate, khname, huohao, houhaocp, chima, saleprice, xscb, jcb, zpl "
                            + "FROM " + QUOTATION_TABLE
                            + " WHERE huohao ILIKE ? ORDER BY zhdate DESC NULLS LAST LIMIT 5",
                    "%" + code + "%");
            for (Map<String, Object> row : rows) {
                Map<String, Object> data = new LinkedHashMap<>();
                putIfNonBlank(data, "报价单号", row.get("dh"));
                putIfNonBlank(data, "报价日期", row.get("zhdate"));
                putIfNonBlank(data, "客户名", row.get("khname"));
                putIfNonBlank(data, "生产货号", row.get("huohao"));
                putIfNonBlank(data, "后道产品", row.get("houhaocp"));
                putIfNonBlank(data, "尺码", row.get("chima"));
                putIfNonBlank(data, "售价", round2(row.get("saleprice")));
                putIfNonBlank(data, "销售成本", round2(row.get("xscb")));
                putIfNonBlank(data, "基础成本", round2(row.get("jcb")));
                putIfNonBlank(data, "正品率", round2(row.get("zpl")));
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", "产品报价信息");
                entry.put("summary", "货号 " + row.get("huohao") + " 产品报价（客户 " + row.get("khname")
                        + "，售价 " + row.get("saleprice") + "）");
                entry.put("data", data);
                out.add(entry);
            }
            if (!rows.isEmpty()) {
                log.info("[结构化数据] 产品报价信息命中: code={}, 条数={}", code, rows.size());
            }
        } catch (Exception e) {
            log.warn("[结构化数据] 产品报价信息查询失败: {}", e.getMessage());
        }
        return out;
    }

    /** 仅当值非空（非null、非空白、非字符串"null"）时放入，满足品名/客户名等字段不为空才带出的要求 */
    private void putIfNonBlank(Map<String, Object> data, String key, Object value) {
        if (value == null) return;
        String s = String.valueOf(value).trim();
        if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
            data.put(key, value);
        }
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
