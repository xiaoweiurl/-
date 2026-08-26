-- ============================================================================
-- V48: 客户明细视图（不聚合，逐单平铺）
--
-- 背景：
--   v_customer_360 定位是"一客户一行"的汇总画像（排序/对比场景需要聚合），
--   但核对与溯源需要逐单明细。本迁移补充两个明细视图：
--   - v_quotation_detail  报价单明细（order_bjd_query 全业务字段逐行列出）
--   - v_sales_order_detail 销售订单明细（order_xs_list 全业务字段逐行列出）
--   明细视图【不做任何 AVG/ SUM 聚合】，脏数据一眼可见，可直接 WHERE 过滤。
--
-- 幂等性：CREATE OR REPLACE VIEW，可重复执行。
-- ============================================================================

-- ============================================================
-- 1. 报价单明细视图（一单一行，含 V44 新增成本构成字段）
-- ============================================================
CREATE OR REPLACE VIEW v_quotation_detail AS
SELECT
    q.dh            AS quotation_no,        -- 报价单号（唯一标识）
    q.zhdate        AS quotation_date,      -- 报价日期
    q.khname        AS customer_name,       -- 客户名称【关联键】→ order_xs_list.khname
    q.huohao        AS product_code,        -- 生产货号【关联键】→ order_sw_gongyidan.huohao / order_xs_list.detailhuohao / production_plan.product_code
    q.houhaocp      AS product_code_cp,     -- 成品货号（客户货号）
    q.chima         AS size,                -- 尺码
    q.remark        AS remark,              -- 备注
    -- ---- 价格与利润 ----
    q.saleprice     AS sale_price,          -- 销售单价（元/双），核对脏数据看这一列原始值
    q.xscb          AS sales_cost,          -- 销售成本
    q.mlr           AS total_profit,        -- 总毛利
    q.mlr_dp        AS profit_per_dozen,    -- 单打毛利
    q.bzlr          AS standard_profit,     -- 标准利润
    q.jsprice       AS settlement_price,    -- 结算价
    q.myprice       AS usd_price,           -- 美元价（外销）
    q.khfl          AS customer_discount,   -- 客户返率
    -- ---- 成本构成：前道 ----
    q.zpl           AS defect_rate_pct,     -- 正品率反值（%），净成本乘数 (1-zpl/100)
    q.jcb           AS basic_cost,          -- 基础成本
    q.zhis          AS machine_seconds,     -- 织造秒数【语义关联】→ order_sw_gongyidan.xjsl / production_plan.seconds
    q.sbdj          AS machine_fee_per_day, -- 机台日费用
    q.zzsb          AS machine_type,        -- 织造设备【语义关联】→ order_sw_gongyidan.jix / production_plan.machine_type
    q.zzcb          AS weaving_cost,        -- 织造成本 = 机台日费用/日产量
    q.qdglf         AS frontend_mgmt_fee,   -- 前道管理费
    q.dxprice       AS setting_cost,        -- 定型费
    q.otherprice    AS other_labor_cost,    -- 其他工价
    q.yllyl         AS raw_material_usage,  -- 原料用量
    q.sumprice      AS raw_material_amount, -- 原料金额合计（预存值，明细见 BOM 视图）
    -- ---- 成本构成：染色（V44 新增） ----
    q.fpkz          AS sewing_weight,       -- 缝拼克重【语义关联】→ order_sw_gongyidan.pfkz / production_plan.sewing_weight
    q.rsdj          AS dye_unit_price,      -- 染色单价（元/kg）
    q.rsprice       AS dye_cost,            -- 染色成本（=fpkz×rsdj，表内有值则优先）
    q.ykgj          AS waist_labor_cost,    -- 腰口工价
    -- ---- 成本构成：后道 ----
    q.qjprice       AS inspection_cost,     -- 全检工价
    q.bzprice       AS packaging_cost,      -- 包装费
    q.hdglf         AS backend_mgmt_fee,    -- 后道管理费
    -- ---- 辅料 ----
    q.fllyl         AS accessory_usage,     -- 辅料用量率
    q.flsum         AS accessory_amount,    -- 辅料金额合计
    -- ---- 汇总口径（与 calculate() 公式对应） ----
    q.countprice    AS frontend_total,      -- 前道合计 = 织造+前道管理+染色+定型+其他工价+原料+缝制+腰口
    q.fpprice       AS waste_loss_amount,   -- 布费损耗金额
    q.hdprice       AS backend_total,       -- 后道合计 = 包装+后道管理费+辅料+全检工价
    q.lyl           AS loss_rate_pct,       -- 损耗率（%）
    q.rcl           AS daily_output,        -- 日产量
    -- ---- 税费运费 ----
    q.yunfei        AS freight,             -- 运费
    q.shuijin       AS tax_amount,          -- 税金
    q.shuijin_sg    AS tax_amount_actual,   -- 实际税金
    -- ---- 前后道针数（V44 新增） ----
    q.qdzs          AS frontend_needles,    -- 前道针数
    q.hdzs          AS backend_needles      -- 后道针数
FROM order_bjd_query q
ORDER BY q.khname NULLS LAST, q.zhdate DESC NULLS LAST;

-- 明细视图列注释
COMMENT ON VIEW  v_quotation_detail IS '报价单明细（一单一行，不聚合）：核对单据/溯源用；360视图的avg被脏数据毒化时，用本视图查原始行';
COMMENT ON COLUMN v_quotation_detail.quotation_no        IS '报价单号，唯一标识【关联键】→ order_xs_list.business_dh';
COMMENT ON COLUMN v_quotation_detail.quotation_date      IS '报价日期';
COMMENT ON COLUMN v_quotation_detail.customer_name      IS '客户名称【关联键】→ order_xs_list.khname';
COMMENT ON COLUMN v_quotation_detail.product_code       IS '生产货号【关联键】→ order_sw_gongyidan.huohao / order_xs_list.detailhuohao / production_plan.product_code';
COMMENT ON COLUMN v_quotation_detail.product_code_cp    IS '成品货号（客户侧货号）';
COMMENT ON COLUMN v_quotation_detail.size               IS '尺码';
COMMENT ON COLUMN v_quotation_detail.remark             IS '备注';
COMMENT ON COLUMN v_quotation_detail.sale_price         IS '销售单价（元/双）——脏数据核对入口：>10000或<0为异常行';
COMMENT ON COLUMN v_quotation_detail.sales_cost         IS '销售成本';
COMMENT ON COLUMN v_quotation_detail.total_profit       IS '总毛利';
COMMENT ON COLUMN v_quotation_detail.profit_per_dozen   IS '单打毛利';
COMMENT ON COLUMN v_quotation_detail.standard_profit    IS '标准利润';
COMMENT ON COLUMN v_quotation_detail.settlement_price   IS '结算价';
COMMENT ON COLUMN v_quotation_detail.usd_price          IS '美元价（外销）';
COMMENT ON COLUMN v_quotation_detail.customer_discount  IS '客户返率';
COMMENT ON COLUMN v_quotation_detail.defect_rate_pct    IS '正品率反值（%），净成本乘数(1-zpl/100)';
COMMENT ON COLUMN v_quotation_detail.basic_cost         IS '基础成本';
COMMENT ON COLUMN v_quotation_detail.machine_seconds    IS '织造秒数【语义关联】→ order_sw_gongyidan.xjsl / production_plan.seconds';
COMMENT ON COLUMN v_quotation_detail.machine_fee_per_day IS '机台日费用';
COMMENT ON COLUMN v_quotation_detail.machine_type       IS '织造设备【语义关联】→ order_sw_gongyidan.jix / production_plan.machine_type';
COMMENT ON COLUMN v_quotation_detail.weaving_cost       IS '织造成本=机台日费用/日产量';
COMMENT ON COLUMN v_quotation_detail.frontend_mgmt_fee  IS '前道管理费';
COMMENT ON COLUMN v_quotation_detail.setting_cost       IS '定型费';
COMMENT ON COLUMN v_quotation_detail.other_labor_cost   IS '其他工价';
COMMENT ON COLUMN v_quotation_detail.raw_material_usage IS '原料用量';
COMMENT ON COLUMN v_quotation_detail.raw_material_amount IS '原料金额合计（预存值）';
COMMENT ON COLUMN v_quotation_detail.sewing_weight      IS '缝拼克重【语义关联】→ order_sw_gongyidan.pfkz / production_plan.sewing_weight';
COMMENT ON COLUMN v_quotation_detail.dye_unit_price     IS '染色单价（元/kg）';
COMMENT ON COLUMN v_quotation_detail.dye_cost           IS '染色成本（=缝拼克重×染色单价，表内有值优先）';
COMMENT ON COLUMN v_quotation_detail.waist_labor_cost   IS '腰口工价';
COMMENT ON COLUMN v_quotation_detail.inspection_cost    IS '全检工价';
COMMENT ON COLUMN v_quotation_detail.packaging_cost     IS '包装费';
COMMENT ON COLUMN v_quotation_detail.backend_mgmt_fee   IS '后道管理费';
COMMENT ON COLUMN v_quotation_detail.accessory_usage    IS '辅料用量率';
COMMENT ON COLUMN v_quotation_detail.accessory_amount   IS '辅料金额合计';
COMMENT ON COLUMN v_quotation_detail.frontend_total     IS '前道合计=织造+前道管理+染色+定型+其他工价+原料+缝制+腰口';
COMMENT ON COLUMN v_quotation_detail.waste_loss_amount  IS '布费损耗金额';
COMMENT ON COLUMN v_quotation_detail.backend_total      IS '后道合计=包装+后道管理费+辅料+全检工价';
COMMENT ON COLUMN v_quotation_detail.loss_rate_pct      IS '损耗率（%）';
COMMENT ON COLUMN v_quotation_detail.daily_output       IS '日产量';
COMMENT ON COLUMN v_quotation_detail.freight            IS '运费';
COMMENT ON COLUMN v_quotation_detail.tax_amount         IS '税金';
COMMENT ON COLUMN v_quotation_detail.tax_amount_actual  IS '实际税金';
COMMENT ON COLUMN v_quotation_detail.frontend_needles   IS '前道针数';
COMMENT ON COLUMN v_quotation_detail.backend_needles    IS '后道针数';

-- ============================================================
-- 2. 销售订单明细视图（一单一行）
-- ============================================================
CREATE OR REPLACE VIEW v_sales_order_detail AS
SELECT
    o.dh              AS order_no,          -- 订单号（唯一标识）
    o.zhdate          AS order_date,        -- 订单日期
    o.khname          AS customer_name,     -- 客户名称【关联键】→ order_bjd_query.khname
    o.detailhuohao    AS product_code,      -- 生产货号【关联键，与报价表不同名】→ order_bjd_query.huohao
    o.detailhuohaocp  AS product_code_cp,   -- 成品货号（客户货号）
    o.sl_sum          AS quantity,          -- 订单数量合计
    o.jh_date         AS delivery_date,     -- 交货日期（交期风险识别输入）
    o.business_dh     AS business_doc_no,   -- 业务单号【关联键】→ order_bjd_query.dh（报价关联候选）
    o.ddtype          AS order_type,        -- 订单类型
    o.ywyname         AS salesperson,       -- 业务员【语义关联】→ order_sw_gongyidan.hhywy / product_quotation.salesperson
    o.state           AS state_code,        -- 订单状态码
    o.zxtate          AS audit_state,       -- 终审状态
    o.sfplan          AS planned_flag,      -- 是否已下排产计划
    o.printnum        AS print_count,       -- 打印次数
    o.zhuser          AS create_user,       -- 制单人
    o.checkuser       AS checker,           -- 审核人
    o.ckeckdate       AS check_date,        -- 审核日期
    o.remark          AS remark             -- 备注
FROM order_xs_list o
ORDER BY o.khname NULLS LAST, o.zhdate DESC NULLS LAST;

COMMENT ON VIEW  v_sales_order_detail IS '销售订单明细（一单一行，不聚合）：核对单据/溯源用';
COMMENT ON COLUMN v_sales_order_detail.order_no         IS '订单号，唯一标识';
COMMENT ON COLUMN v_sales_order_detail.order_date       IS '订单日期';
COMMENT ON COLUMN v_sales_order_detail.customer_name    IS '客户名称【关联键】→ order_bjd_query.khname';
COMMENT ON COLUMN v_sales_order_detail.product_code     IS '生产货号【关联键，与报价表字段不同名】→ order_bjd_query.huohao';
COMMENT ON COLUMN v_sales_order_detail.product_code_cp  IS '成品货号（客户侧货号）';
COMMENT ON COLUMN v_sales_order_detail.quantity         IS '订单数量合计';
COMMENT ON COLUMN v_sales_order_detail.delivery_date    IS '交货日期（交期风险识别输入）';
COMMENT ON COLUMN v_sales_order_detail.business_doc_no  IS '业务单号【关联键】→ order_bjd_query.dh（报价关联候选）';
COMMENT ON COLUMN v_sales_order_detail.order_type       IS '订单类型';
COMMENT ON COLUMN v_sales_order_detail.salesperson      IS '业务员【语义关联】→ order_sw_gongyidan.hhywy / product_quotation.salesperson';
COMMENT ON COLUMN v_sales_order_detail.state_code       IS '订单状态码';
COMMENT ON COLUMN v_sales_order_detail.audit_state      IS '终审状态';
COMMENT ON COLUMN v_sales_order_detail.planned_flag     IS '是否已下排产计划';
COMMENT ON COLUMN v_sales_order_detail.print_count      IS '打印次数';
COMMENT ON COLUMN v_sales_order_detail.create_user      IS '制单人';
COMMENT ON COLUMN v_sales_order_detail.checker          IS '审核人';
COMMENT ON COLUMN v_sales_order_detail.check_date       IS '审核日期';
COMMENT ON COLUMN v_sales_order_detail.remark           IS '备注';
