-- ============================================================================
-- V47: 业务基线数据种子 + 客户360视图防脏数据加固
--
-- 背景：
-- 1. schema_business_data.sql 中含五张本地管辖表的基线数据（原料入库/原料采购/
--    生产计划/辅料采购/产品报价），但该文件从未作为迁移被执行——表由 V37 创建后
--    一直为空，导致智能报价（用量×采购最低价）、供应商对比等功能无数据可用。
--    本迁移仅在【空表】时写入基线数据，绝不覆盖任何已有数据
--    （含用户经供应链页面/Excel 导入的真实数据）。
-- 2. v_customer_360 的平均售价被上游脏数据毒化（如订单总额/时间戳等非单价值
--    混入 saleprice 列，个别客户 AVG 高达上亿）。视图聚合本身无重复计数，
--    根因是底层数据异常值。本迁移用 FILTER 限定可信区间计算均值，并新增
--    min/max/异常行数三列让脏数据【可见】而非【毒化】平均值。
--
-- 幂等性：全部语句可重复执行。
-- ============================================================================

-- ============================================================
-- 一、基线数据种子（仅空表时写入）
-- ============================================================
DO $$
BEGIN
    ------------------------------------------------------------------
    -- 1. 原料入库（raw_material_warehouse）9 条
    ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM raw_material_warehouse) THEN
        INSERT INTO raw_material_warehouse (product_code, color, batch_no, unit, unit_price) VALUES
            ('XF1202020', '', 'G01201', '千克', 102.0),
            ('XF4070', 'Z', 'G4706-1', '千克', 36.5),
            ('XF2030', 'S', 'G2341', '千克', 33.0),
            ('XF2030', 'Z', 'G2341', '千克', 33.0),
            ('XB40/2', '', '', '千克', 28.0),
            ('XB55/24F', 'S', 'JS5524W110', '千克', 19.6),
            ('XB55/24F', 'Z', 'JS5524W110', '千克', 19.6),
            ('XF2070/48F', 'Z', 'HDC27049T', '千克', 29.0),
            ('XF2070/48F', 'S', 'HDC27049T', '千克', 29.0);
    END IF;

    ------------------------------------------------------------------
    -- 2. 原料采购（raw_material_purchase）6 条
    --    注意：unit_price 单位随 unit 列变化（条/千克），供应商对比
    --    取最低价时按同单位分组比较
    ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM raw_material_purchase) THEN
        INSERT INTO raw_material_purchase (material_code, unit, supplier, batch_no, unit_price) VALUES
            ('2070/48F', '条', '精美纺织', 'DC27078', 0.029),
            ('2070/48F', '条', '精美纺织', 'DC27049W', 0.029),
            ('50D/24F', '条', '义乌华鼎锦纶有限公司', '-', 0.0196),
            ('HD-XB30/12F', '千克', '月源化纤', 317.0, 0.0222),
            ('XD40/2F', '千克', '无锡都灵化纤有限公司', 94.64, 0.032),
            ('XF1202020', '千克', '雅安百丝得包纱有限公司', 'G01201', 0.102);
    END IF;

    ------------------------------------------------------------------
    -- 3. 生产计划（production_plan）4 条
    ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM production_plan) THEN
        INSERT INTO production_plan (semi_product_code, product_code, sewing_weight, machine_type, needle_count, seconds, machine_count, single_machine_output) VALUES
            ('HT01S', 'HT01-S', 16.5, '医疗机', '352N', 188, 1, 360),
            ('HT01M', 'HT01-M', 17.8, '医疗机', '352N', 198, 1, 350),
            ('HT01L', 'HT01-L', 19, '医疗机', '352N', 208, 1, 340),
            ('HT01XL', 'HT01-XL', 20, '医疗机', '352N', 210, 1, 330);
    END IF;

    ------------------------------------------------------------------
    -- 4. 辅料采购（accessory_purchase）8 条
    ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM accessory_purchase) THEN
        INSERT INTO accessory_purchase (accessory_name, accessory_category, unit, supplier, accessory_unit_price) VALUES
            ('磨砂袋14.5*16.5', '包装袋', '个', '星际包装', 0.115),
            ('10.4*14.4CM', '纸板', '个', '义乌市春云包装厂', 0.035),
            ('10.4*14.4CM', '纸板', '个', '金华市凡贺包装有限公司', 0.035),
            ('金色尺码贴S', '其他', '个', '陈庆武', 0.015),
            ('金色尺码贴M', '其他', '个', '陈庆武', 0.015),
            ('金色尺码贴L', '其他', '个', '陈庆武', 0.015),
            ('金色尺码贴XL', '其他', '个', '陈庆武', 0.015),
            ('XX44*34*32', '纸箱', '个', '义乌市春云包装厂', 4.8);
    END IF;

    ------------------------------------------------------------------
    -- 5. 产品报价（product_quotation）4 条
    ------------------------------------------------------------------
    IF NOT EXISTS (SELECT 1 FROM product_quotation) THEN
        INSERT INTO product_quotation (product_code, production_code, document_no, period, customer, salesperson, product_category, front_quotation_no, approval_status, sales_type, raw_material_name1, material_usage1, material_unit_price1, raw_material_name2, material_usage2, material_unit_price2, raw_material_name3, material_usage3, material_unit_price3, raw_material_name4, material_usage4, material_unit_price4, raw_material_name5, material_usage5, material_unit_price5, raw_material_name6, material_usage6, material_unit_price6, accessory_name, accessory_price, weaving_seconds, daily_output, equipment_daily_cost, weaving_cost, yield_rate, sewing_weight, sewing_cost, dyeing_unit_price, dyeing_cost, setting_cost, packaging_cost, manufacturing_total, net_cost, sales_cost, tax_amount, machine_hourly_rate, single_machine_output_hourly) VALUES
            ('HT01-S', 'HTO1S', 'HDBJ0126040040', '2026/3/1-2026/4/25', '莎维亚/AC3059', '赵瑞', '丝袜', 'CPBJ0126030087', '已终审', '内销', '203030', 0.2, 0.245, '2070/48F', 2.0, 0.029, '50D/24F', 2.4, 0.0196, 'HD-XB30/12F', 0.4, 0.0222, 'XD40/2F', 4.6, 0.032, 'XF1202020', 7.8, 0.102, '6011牛皮纸盒', 0.181, 188, 460, 450, 0.0500, 93, 16.5, 0, 7.5000, 0.1238, 0, 0, 0.1738, 1.4400, 1.4400, 0, 50.0000, 1000.0000),
            ('HT01-M', 'KTO1M', 'HDBJ0126040041', '2026/3/1-2026/4/25', '莎维亚/AC3059', '赵瑞锋', '丝袜', 'CPBJ0126030089', '已终审', '内销', '2070/48F', 2.0, 0.029, '50D/24F', 2.4, 0.0196, 'HD-XB30/12F', 0.4, 0.0222, 'XD40/2F', 5.0, 0.032, 'XF1202020', 8.2, 0.102, NULL, NULL, NULL, '6011牛皮纸盒', 0.181, 198, 436, 450, 0.0500, 93, 17.8, 0, 7.5000, 0.1335, 0, 0, 0.1835, 1.5130, 1.5130, 0, 50.0000, 1000.0000),
            ('HT01-L', 'HTO1L', 'HDBJ0126040042', '2026/3/1-2026/4/25', '莎维亚/AC3059', '赵瑞锋', '丝袜', 'CPBJ0126030090', '已终审', '内销', '2070/48F', 2.0, 0.029, '50D/24F', 2.4, 0.0196, 'HD-KB30/12F', 0.4, 0.0222, 'XD40/2F', 5.6, 0.032, 'XF1202020', 8.8, 0.102, NULL, NULL, NULL, '6010条码不干胶', 0.181, 208, 415, 450, 0.0500, 93, 19, 0, 7.5000, 0.1425, 0, 0, 0.1925, 1.5872, 1.5872, 0, 50.0000, 1000.0000),
            ('HT01-XL', 'XT01XL', 'HDBJ0126040043', '2026/3/1-2026/4/25', '莎维亚/AC3059', '赵瑞', '丝袜', 'CPBJ0126030091', '已终审', '内销', '2070/48F', 2.0, 0.029, '50D/24F', 2.4, 0.0196, 'HD-XB30/12F', 0.4, 0.0222, 'XD40/2F', 6.0, 0.032, 'XF1202020', 9.6, 0.102, NULL, NULL, NULL, '6011牛皮纸盒', 0.181, 210, 411, 450, 0.0500, 93, 20, 0, 7.5000, 0.1500, 0, 0, 0.2000, 1.6409, 1.6409, 0, 50.0000, 1000.0000);
    END IF;
END $$;

-- ============================================================
-- 二、客户360视图加固：可信区间均值 + 异常值可见化
--
-- 可信单价区间说明（丝袜行业产品售价为元/双量级）：
--   saleprice ∈ (0, 10000)      售价/成本可信（万双计价上限也不超万元/双）
--   mlr_dp   ∈ (-10000, 10000)  单品毛利可信（允许亏损单为负值）
-- 超出区间的行计入 saleprice_anomaly_cnt，不参与均值——脏数据（订单总额、
-- 时间戳、单位错误等）不再毒化平均值，且行数暴露便于上游排查。
-- ============================================================
CREATE OR REPLACE VIEW v_customer_360 AS
SELECT
    COALESCE(q.khname, o.khname)                     AS khname,
    q.quotation_cnt                                   AS quotation_cnt,
    q.avg_saleprice                                   AS avg_saleprice,
    q.avg_sales_cost                                  AS avg_sales_cost,
    q.avg_unit_profit                                 AS avg_unit_profit,
    q.last_quotation_date                             AS last_quote_date,
    o.sales_order_cnt                                 AS sales_order_cnt,
    o.sales_total_qty                                 AS sales_total_qty,
    o.last_order_date                                 AS last_order_date,
    o.latest_delivery                                 AS latest_delivery,
    o.salespersons                                    AS salespersons,
    q.min_saleprice                                   AS min_saleprice,
    q.max_saleprice                                   AS max_saleprice,
    q.saleprice_anomaly_cnt                           AS saleprice_anomaly_cnt
FROM (
    SELECT
        khname,
        COUNT(DISTINCT dh)                                                            AS quotation_cnt,
        AVG(saleprice) FILTER (WHERE saleprice > 0 AND saleprice < 10000)             AS avg_saleprice,
        AVG(xscb)      FILTER (WHERE xscb > 0 AND xscb < 10000)                       AS avg_sales_cost,
        AVG(mlr_dp)    FILTER (WHERE mlr_dp > -10000 AND mlr_dp < 10000)              AS avg_unit_profit,
        MAX(zhdate)                                                                   AS last_quotation_date,
        MIN(saleprice)                                                                AS min_saleprice,
        MAX(saleprice)                                                                AS max_saleprice,
        COUNT(*) FILTER (WHERE saleprice IS NOT NULL AND (saleprice <= 0 OR saleprice >= 10000)) AS saleprice_anomaly_cnt
    FROM order_bjd_query
    WHERE khname IS NOT NULL AND khname <> ''
    GROUP BY khname
) q
FULL OUTER JOIN (
    SELECT
        khname,
        COUNT(DISTINCT dh)                    AS sales_order_cnt,
        SUM(sl_sum)                           AS sales_total_qty,
        MAX(zhdate)                           AS last_order_date,
        MAX(jh_date)                          AS latest_delivery,
        STRING_AGG(DISTINCT ywyname, '、')    AS salespersons
    FROM order_xs_list
    WHERE khname IS NOT NULL AND khname <> ''
    GROUP BY khname
) o ON o.khname = q.khname;

-- 视图列注释（覆盖 V46 原注释，反映加固后语义）
COMMENT ON VIEW v_customer_360 IS '客户360视图：报价侧(order_bjd_query)与成交侧(order_xs_list)按客户名预聚合后FULL OUTER JOIN。均值仅统计可信区间(0,10000)，异常行数单列暴露';
COMMENT ON COLUMN v_customer_360.khname IS '客户名称（两侧COALESCE兜底，任一侧有记录即出现）';
COMMENT ON COLUMN v_customer_360.quotation_cnt IS '报价单号数（COUNT DISTINCT dh，order_bjd_query）';
COMMENT ON COLUMN v_customer_360.avg_saleprice IS '平均售价（元/双，仅统计 0<saleprice<10000 的可信行；全部异常时为NULL，勿当0处理）';
COMMENT ON COLUMN v_customer_360.avg_sales_cost IS '平均销售成本（元/双，仅统计 0<xscb<10000 的可信行）';
COMMENT ON COLUMN v_customer_360.avg_unit_profit IS '平均单品毛利（元，仅统计 |mlr_dp|<10000 的可信行，允许负值=亏损单）';
COMMENT ON COLUMN v_customer_360.last_quote_date IS '最近报价日期（order_bjd_query.zhdate最大值）';
COMMENT ON COLUMN v_customer_360.sales_order_cnt IS '销售订单数（COUNT DISTINCT dh，order_xs_list）';
COMMENT ON COLUMN v_customer_360.sales_total_qty IS '成交数量合计（SUM(sl_sum)，交期风险与产能需求测算输入）';
COMMENT ON COLUMN v_customer_360.last_order_date IS '最近下单日期（order_xs_list.zhdate最大值）';
COMMENT ON COLUMN v_customer_360.latest_delivery IS '最晚交期（order_xs_list.jh_date最大值，交期风险识别输入）';
COMMENT ON COLUMN v_customer_360.salespersons IS '合作业务员（STRING_AGG去重，顿号分隔）';
COMMENT ON COLUMN v_customer_360.min_saleprice IS '报价原始最小值（含脏数据，用于对比avg判断异常）';
COMMENT ON COLUMN v_customer_360.max_saleprice IS '报价原始最大值（含脏数据，上亿通常源于此列对应的异常行）';
COMMENT ON COLUMN v_customer_360.saleprice_anomaly_cnt IS '售价异常行数（saleprice<=0 或 >=10000 的行数，>0说明上游数据需清洗）';

-- ============================================================
-- 三、脏数据排查辅助视图：直接列出毒化均值的异常明细行
-- ============================================================
CREATE OR REPLACE VIEW v_quotation_price_anomaly AS
SELECT
    dh            AS quotation_no,
    khname        AS customer,
    huohao        AS product_code,
    chima         AS size,
    saleprice,
    xscb          AS sales_cost,
    mlr_dp        AS unit_profit,
    zhdate        AS quote_date
FROM order_bjd_query
WHERE khname IS NOT NULL AND khname <> ''
  AND (saleprice <= 0 OR saleprice >= 10000
       OR xscb < 0 OR xscb >= 10000
       OR mlr_dp <= -10000 OR mlr_dp >= 10000);

COMMENT ON VIEW v_quotation_price_anomaly IS '报价单异常值明细：售价/成本/毛利超出可信区间的原始行，用于定位毒化客户360均值的脏数据（排查order_bjd_query上游同步质量）';
COMMENT ON COLUMN v_quotation_price_anomaly.quotation_no IS '报价单号（order_bjd_query.dh）';
COMMENT ON COLUMN v_quotation_price_anomaly.customer IS '客户名称';
COMMENT ON COLUMN v_quotation_price_anomaly.product_code IS '生产货号';
COMMENT ON COLUMN v_quotation_price_anomaly.size IS '尺码';
COMMENT ON COLUMN v_quotation_price_anomaly.saleprice IS '异常售价原始值（预期元/双量级，出现亿级多为订单总额/时间戳混入）';
COMMENT ON COLUMN v_quotation_price_anomaly.sales_cost IS '销售成本原始值';
COMMENT ON COLUMN v_quotation_price_anomaly.unit_profit IS '单品毛利原始值';
COMMENT ON COLUMN v_quotation_price_anomaly.quote_date IS '报价日期';
