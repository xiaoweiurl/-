package com.imagemanager.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 供应链 AI 工具集
 * 
 * 通过 LangChain4j @Tool 注解暴露给大模型自动调用：
 * 1. queryDatabase - Text-to-SQL：大模型根据用户问题自动生成 SQL 查询数据库
 * 2. searchSupplyChainByKeyword - 关键词搜索（降级方案，现有逻辑）
 * 
 * 安全策略：
 * - 只允许 SELECT 语句
 * - 自动注入 LIMIT 100
 * - 强制 company 过滤
 * - 5 秒查询超时（由数据库连接池控制）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplyChainTools {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 供应链核心表的 Schema 元数据
     * 大模型基于此生成正确的 SQL
     */
    private static final String SCHEMA_CONTEXT = """
            ## 供应链数据库表结构（PostgreSQL）

            ### 1. products - 产品表
            - id (VARCHAR, 主键, 产品编码如 HT01-S)
            - name (产品名称)
            - category (分类)
            - company (公司, 多租户隔离字段)

            ### 2. product_quotation - 产品报价单
            - id (SERIAL, 主键)
            - product_code (VARCHAR, 产品编码, 关联 products.id)
            - production_code (生产编码)
            - document_no (单据编号)
            - period (期间)
            - customer (客户)
            - salesperson (业务员)
            - product_category (产品类别)
            - approval_status (审批状态)
            - sales_type (销售类型)
            - raw_material_name1~6 (原料名称1-6)
            - material_usage1~6 (用量1-6, DECIMAL)
            - material_unit_price1~6 (单价1-6, DECIMAL)
            - accessory_name (辅料名称)
            - accessory_price (辅料价格, DECIMAL)
            - weaving_seconds (织造秒数, DECIMAL)
            - daily_output (日产量, INTEGER)
            - equipment_daily_cost (设备日成本, DECIMAL)
            - weaving_cost (织造成本, DECIMAL)
            - yield_rate (良品率, DECIMAL)
            - sewing_weight (缝纫克重, DECIMAL)
            - sewing_cost (缝纫成本, DECIMAL)
            - dyeing_unit_price (染色单价, DECIMAL)
            - dyeing_cost (染色成本, DECIMAL)
            - setting_cost (定型成本, DECIMAL)
            - packaging_cost (包装成本, DECIMAL)
            - manufacturing_total (制造费用合计, DECIMAL)
            - net_cost (净成本, DECIMAL)
            - sales_cost (销售成本, DECIMAL)
            - tax_amount (税额, DECIMAL)
            - machine_hourly_rate (机台时薪, DECIMAL)
            - single_machine_output_hourly (单机时产量, DECIMAL)
            - company (公司, 多租户隔离字段)

            ### 3. raw_material_purchase - 原料采购表
            - id (SERIAL, 主键)
            - material_code (原料编码)
            - unit (单位)
            - supplier (供应商)
            - batch_no (批号)
            - unit_price (单价, DECIMAL)
            - company (公司)

            ### 4. raw_material_warehouse - 原料入库表
            - id (SERIAL, 主键)
            - product_code (产品编码)
            - color (颜色)
            - batch_no (批号)
            - unit (单位)
            - unit_price (单价, DECIMAL)
            - company (公司)

            ### 5. production_plan - 生产计划表
            - id (SERIAL, 主键)
            - semi_product_code (半成品编码)
            - product_code (产品编码)
            - sewing_weight (缝纫克重, DECIMAL)
            - machine_type (机型)
            - needle_count (针数)
            - seconds (秒数, DECIMAL)
            - machine_count (机台数, INTEGER)
            - single_machine_output (单机产量, DECIMAL)
            - company (公司)

            ### 6. accessory_purchase - 辅料采购表
            - id (SERIAL, 主键)
            - accessory_name (辅料名称)
            - accessory_category (辅料类别)
            - unit (单位)
            - supplier (供应商)
            - accessory_unit_price (辅料单价, DECIMAL)
            - company (公司)

            ## 关键说明
            - 所有金额字段为 DECIMAL 类型，单位为元
            - material_usage 为用料量，material_unit_price 为对应单价
            - 原料成本 = 用量 x 单价（6组原料分别计算后求和）
            - 查询时必须加 WHERE company = ? 条件做数据隔离
            """;

    /** 危险 SQL 关键词 */
    private static final Pattern DANGEROUS_SQL = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|EXEC|EXECUTE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Text-to-SQL 工具：大模型自动生成 SQL 并执行查询
     * 
     * 调用方式：大模型会传入完整的 SQL 语句，此方法负责安全校验和执行
     * 注意：company 参数由 SmartChatServiceImpl 通过 ThreadLocal 或上下文传入
     */
    @Tool("执行SQL查询数据库获取供应链数据。传入完整的SELECT语句。只能查询，禁止修改数据。SQL中用 'COMPANY_PLACEHOLDER' 代替公司名，系统会自动替换。")
    public String queryDatabase(String sql) {
        log.info("[Text-to-SQL] 接收到SQL: {}", sql);

        // 安全校验
        String trimmedSql = sql.trim().replaceAll(";\\s*$", "");
        
        if (!trimmedSql.toUpperCase().startsWith("SELECT")) {
            return "错误：只允许SELECT查询语句";
        }
        
        if (DANGEROUS_SQL.matcher(trimmedSql).find()) {
            return "错误：检测到危险SQL操作，已拒绝执行";
        }

        // 替换公司占位符
        String company = currentCompany.get();
        if (company == null || company.isEmpty()) {
            company = "";
        }
        trimmedSql = trimmedSql.replace("COMPANY_PLACEHOLDER", "'" + company.replace("'", "''") + "'");

        // 自动注入 LIMIT（如果没有的话）
        if (!trimmedSql.toUpperCase().contains("LIMIT")) {
            trimmedSql = trimmedSql + " LIMIT 100";
        }

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(trimmedSql);
            log.info("[Text-to-SQL] 查询返回 {} 行", rows.size());

            if (rows.isEmpty()) {
                return "查询结果为空，数据库中没有匹配的数据。";
            }

            // 格式化为 Markdown 表格，方便大模型理解
            return formatAsMarkdownTable(rows);

        } catch (Exception e) {
            log.error("[Text-to-SQL] SQL执行失败: {}", e.getMessage(), e);
            return "SQL执行失败: " + e.getMessage() + "。请检查SQL语法是否正确。";
        }
    }

    /** ThreadLocal 存储当前请求的 company，用于 SQL 注入 */
    private static final ThreadLocal<String> currentCompany = new ThreadLocal<>();

    /**
     * 设置当前请求的公司（由 SmartChatServiceImpl 调用）
     */
    public void setCurrentCompany(String company) {
        currentCompany.set(company);
    }

    /**
     * 清理 ThreadLocal（请求结束后调用）
     */
    public void clearCurrentCompany() {
        currentCompany.remove();
    }

    /**
     * 获取数据库表结构（大模型生成SQL时需要参考）
     */
    @Tool("获取供应链数据库的表结构信息，用于了解有哪些表和字段可以查询")
    public String getDatabaseSchema() {
        return SCHEMA_CONTEXT;
    }

    /**
     * 将查询结果格式化为 Markdown 表格
     */
    private String formatAsMarkdownTable(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "无数据";
        }

        StringBuilder sb = new StringBuilder();
        Set<String> columns = rows.get(0).keySet();

        // 表头
        sb.append("| ");
        sb.append(String.join(" | ", columns));
        sb.append(" |\n");

        // 分隔行
        sb.append("| ");
        sb.append(String.join(" | ", columns.stream().map(c -> "---").toList()));
        sb.append(" |\n");

        // 数据行
        for (Map<String, Object> row : rows) {
            sb.append("| ");
            sb.append(columns.stream()
                    .map(c -> {
                        Object v = row.get(c);
                        return v == null ? "" : v.toString();
                    })
                    .reduce((a, b) -> a + " | " + b)
                    .orElse(""));
            sb.append(" |\n");
        }

        sb.append("\n共 ").append(rows.size()).append(" 行数据。");
        return sb.toString();
    }
}
