-- =====================================================================
-- V46: 业务数据关联关系统一整改
-- 背景：全系统数据关联审计发现的 6 类问题统一修复
--   P1 字段不统一：货号四表四名（huohao/detailhuohao/product_code）→ 统一视图拉通，不改上游表
--   P3 数据孤岛：quotations 表无代码读写 → COMMENT 标注废弃候选
--   P4 双轨表冲突：英文遗留表三套 DDL 并存 → 索引/注释收敛，声明权威结构
--   P6 无跨表 JOIN：全部单表查询，客户360/货号谱系只能内存拼 → 物化为数据库视图
--   P8 order_bjd_query 无本地 DDL：全新环境 V42 UPDATE 会失败 → 幂等兜底建表
-- 原则：不改上游同步表（order_*）任何存量列；全部幂等（IF NOT EXISTS / CREATE OR REPLACE）；
--       不删除任何数据；视图只做读取收敛，写入路径不变。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. order_bjd_query 兜底建表（全新环境容错；已有表时本段无操作）
--    权威结构 = 上游 C# 实体 + V44 新增列；上游表已存在时 CREATE 跳过，
--    后续 ALTER 保证列齐全。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_bjd_query (
  dh         varchar(64),
  zhdate     timestamp,
  khname     varchar(255),
  huohao     varchar(128),
  houhaocp   varchar(128),
  remark     text,
  chima      varchar(64),
  zpl        numeric(18,4),
  jcb        numeric(18,4),
  yunfei     numeric(18,4),
  shuijin    numeric(18,4),
  shuijin_sg numeric(18,4),
  xscb       numeric(18,4),
  khfl       numeric(18,4),
  saleprice  numeric(18,4),
  mlr        numeric(18,4),
  mlr_dp     numeric(18,4),
  bzlr       numeric(18,4),
  jsprice    numeric(18,4),
  myprice    numeric(18,4),
  countprice numeric(18,4),
  zhis       numeric(18,4),
  lyl        numeric(18,4),
  rcl        numeric(18,4),
  zzsb       varchar(128),
  sbdj       numeric(18,4),
  zzcb       numeric(18,4),
  qdglf      numeric(18,4),
  dxprice    numeric(18,4),
  otherprice numeric(18,4),
  yllyl      numeric(18,4),
  sumprice   numeric(18,4),
  fpprice    numeric(18,4),
  hdprice    numeric(18,4),
  bzprice    numeric(18,4),
  hdglf      numeric(18,4),
  fllyl      numeric(18,4),
  flsum      numeric(18,4)
);

-- V44 列兜底（上游表先于 V44 存在时也能补齐；已存在则跳过）
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS rsdj    NUMERIC(18,4);
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS fpkz    NUMERIC(18,4);
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS rsprice NUMERIC(18,4);
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS qjprice NUMERIC(18,4);
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS ykgj    NUMERIC(18,4);
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS qdzs    TEXT;
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS hdzs    TEXT;

COMMENT ON TABLE order_bjd_query IS '报价单表（上游同步）：报价成本核算主表。关联键 huohao→工艺单/排产，khname→销售订单';

-- ---------------------------------------------------------------------
-- 2. 关联键索引补齐（IF NOT EXISTS 幂等）
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_order_bjd_query_dh      ON order_bjd_query (dh);
CREATE INDEX IF NOT EXISTS idx_order_bjd_query_huohao  ON order_bjd_query (huohao);
CREATE INDEX IF NOT EXISTS idx_order_bjd_query_khname  ON order_bjd_query (khname);
-- 工艺单按货号取缝拼克重的高频路径（huohao 等值 + pfkz 过滤）
CREATE INDEX IF NOT EXISTS idx_gongyidan_huohao_pfkz   ON order_sw_gongyidan (huohao, pfkz);

-- ---------------------------------------------------------------------
-- 3. 关联关系 COMMENT 标注（数据库层自文档化，模型/DBA 可直接读取）
--    同义字段映射：
--      货号   = order_bjd_query.huohao = order_xs_list.detailhuohao
--             = order_sw_gongyidan.huohao = production_plan.product_code
--             = product_quotation.product_code
--      客户   = order_bjd_query.khname = order_xs_list.khname = product_quotation.customer
--      业务员 = product_quotation.salesperson = order_xs_list.ywyname = order_sw_gongyidan.hhywy
--      机型   = order_bjd_query.zzsb = order_sw_gongyidan.jix = production_plan.machine_type
--      下机时间 = order_bjd_query.zhis = order_sw_gongyidan.xjsl = production_plan.seconds
--      缝拼克重 = order_bjd_query.fpkz = order_sw_gongyidan.pfkz = production_plan.sewing_weight
--      原料编码 = product_quotation.raw_material_name1~6 = raw_material_purchase.material_code
--               = raw_material_warehouse.product_code
-- ---------------------------------------------------------------------
COMMENT ON COLUMN order_bjd_query.huohao            IS '生产货号【关联键】→ order_sw_gongyidan.huohao / order_xs_list.detailhuohao / production_plan.product_code';
COMMENT ON COLUMN order_bjd_query.khname            IS '客户名称【关联键】→ order_xs_list.khname / product_quotation.customer';
COMMENT ON COLUMN order_bjd_query.zzsb              IS '织造设备【关联键】→ order_sw_gongyidan.jix / production_plan.machine_type';
COMMENT ON COLUMN order_bjd_query.zhis              IS '下机时间(秒)【同义】order_sw_gongyidan.xjsl / production_plan.seconds';
COMMENT ON COLUMN order_xs_list.detailhuohao        IS '生产货号【关联键】→ order_bjd_query.huohao / order_sw_gongyidan.huohao';
COMMENT ON COLUMN order_xs_list.business_dh         IS '业务单号【关联键候选】语义对应上游报价/业务单据号，可追溯报价单 dh';
COMMENT ON COLUMN order_sw_gongyidan.huohao         IS '生产货号【关联键】→ order_bjd_query.huohao / order_xs_list.detailhuohao / production_plan.product_code';

-- 英文遗留表（V37 建表）注释：DO 块保护，表不存在的环境跳过不报错
DO $$
BEGIN
  IF to_regclass('public.production_plan') IS NOT NULL THEN
    COMMENT ON COLUMN production_plan.product_code  IS '产品编码=生产货号【关联键】→ order_bjd_query.huohao / order_sw_gongyidan.huohao';
    COMMENT ON COLUMN production_plan.machine_type  IS '机型【同义】order_sw_gongyidan.jix / order_bjd_query.zzsb';
    COMMENT ON COLUMN production_plan.sewing_weight IS '缝拼克重【同义】order_sw_gongyidan.pfkz / order_bjd_query.fpkz';
  END IF;
  IF to_regclass('public.product_quotation') IS NOT NULL THEN
    COMMENT ON COLUMN product_quotation.product_code IS '产品编码=生产货号【关联键】→ production_plan.product_code；上游权威表为 order_bjd_query.huohao';
    COMMENT ON COLUMN product_quotation.salesperson  IS '业务员【同义】order_xs_list.ywyname / order_sw_gongyidan.hhywy';
    COMMENT ON COLUMN product_quotation.customer     IS '客户【同义】order_bjd_query.khname / order_xs_list.khname';
  END IF;
  IF to_regclass('public.raw_material_purchase') IS NOT NULL THEN
    COMMENT ON COLUMN raw_material_purchase.material_code IS '原料编码【关联键】→ raw_material_warehouse.product_code / product_quotation.raw_material_name1~6';
  END IF;
  IF to_regclass('public.raw_material_warehouse') IS NOT NULL THEN
    COMMENT ON COLUMN raw_material_warehouse.product_code IS '原料编码【关联键】→ raw_material_purchase.material_code';
  END IF;
END $$;

-- ---------------------------------------------------------------------
-- 4. 数据孤岛标注：quotations（V41）无任何代码读写，业务与 order_bjd_query 重叠
--    保留表结构不删数据，标注废弃候选；若后续启用需先接入 QuotationCalcService
-- ---------------------------------------------------------------------
DO $$
BEGIN
  IF to_regclass('public.quotations') IS NOT NULL THEN
    COMMENT ON TABLE quotations IS '【废弃候选】V41 创建的报价落地表，当前无任何代码读写；线上权威报价表为 order_bjd_query（上游同步）。请勿新增依赖，保留仅为历史兼容';
  END IF;
  -- 双轨近似表标注：schema_complete.sql 的 production_plans(复数) 与代码使用的 production_plan(单数) 并存
  IF to_regclass('public.production_plans') IS NOT NULL THEN
    COMMENT ON TABLE production_plans IS '【废弃候选】与代码使用的 production_plan(单数) 并存但无代码读写，请勿新增依赖';
  END IF;
  IF to_regclass('public.raw_material_inbound') IS NOT NULL THEN
    COMMENT ON TABLE raw_material_inbound IS '【废弃候选】无代码读写；原料入库权威表为 raw_material_warehouse';
  END IF;
END $$;

-- ---------------------------------------------------------------------
-- 5. 统一关联视图（读取收敛：模型/代码查视图即可跨表，绕开 2 表 JOIN 限制）
-- ---------------------------------------------------------------------

-- 5.1 货号谱系视图：一个货号的报价 + 工艺参数 + 真实订单需求 + 排产产能
CREATE OR REPLACE VIEW v_product_genealogy AS
SELECT
  q.huohao                                   AS huohao,            -- 生产货号（统一关联键）
  q.khname                                   AS khname,            -- 最近报价客户
  q.chima                                    AS chima,             -- 尺码
  q.dh                                       AS last_quotation_dh, -- 最近报价单号
  q.saleprice                                AS last_saleprice,    -- 最近售价
  q.xscb                                     AS last_sales_cost,   -- 最近销售成本
  g.pfkz                                     AS process_sewing_weight, -- 工艺单缝拼克重(权威)
  g.xjsl                                     AS process_seconds,   -- 工艺单下机秒数(权威)
  g.djcl                                     AS process_theory_output, -- 工艺单理论产量
  g.jix                                      AS process_machine_type,  -- 工艺单机型
  g.zs                                       AS process_needles,   -- 工艺单针数
  o.order_cnt                                AS sales_order_cnt,   -- 真实订单数
  o.total_qty                                AS sales_total_qty,   -- 真实订单数量合计
  o.latest_delivery                          AS latest_delivery,   -- 最晚交期
  p.machine_type                             AS plan_machine_type, -- 排产机型
  p.machine_count                            AS plan_machine_count,-- 投入机台数
  p.single_machine_output                    AS plan_output        -- 单机日产量
FROM (SELECT DISTINCT ON (huohao) * FROM order_bjd_query
      WHERE huohao IS NOT NULL AND huohao <> ''
      ORDER BY huohao, zhdate DESC NULLS LAST) q
LEFT JOIN (SELECT DISTINCT ON (huohao) huohao, pfkz, xjsl, djcl, jix, zs
           FROM order_sw_gongyidan
           WHERE huohao IS NOT NULL AND huohao <> ''
           ORDER BY huohao, bh DESC) g ON g.huohao = q.huohao
LEFT JOIN (SELECT detailhuohao, COUNT(DISTINCT dh) AS order_cnt,
                  COALESCE(SUM(sl_sum), 0) AS total_qty, MAX(jh_date) AS latest_delivery
           FROM order_xs_list
           WHERE detailhuohao IS NOT NULL AND detailhuohao <> ''
           GROUP BY detailhuohao) o ON o.detailhuohao = q.huohao
LEFT JOIN (SELECT DISTINCT ON (product_code) product_code, machine_type, machine_count, single_machine_output
           FROM production_plan
           WHERE product_code IS NOT NULL AND product_code <> ''
           ORDER BY product_code, id DESC) p ON p.product_code = q.huohao;

COMMENT ON VIEW v_product_genealogy IS '货号谱系视图：报价(order_bjd_query) ⨝ 工艺单(order_sw_gongyidan) ⨝ 订单需求(order_xs_list) ⨝ 排产(production_plan)，统一关联键 huohao';
COMMENT ON COLUMN v_product_genealogy.huohao                IS '生产货号【统一关联键】← order_bjd_query.huohao（= order_xs_list.detailhuohao = production_plan.product_code）';
COMMENT ON COLUMN v_product_genealogy.khname                IS '最近报价客户 ← order_bjd_query.khname（按 zhdate 最新一条报价取）';
COMMENT ON COLUMN v_product_genealogy.chima                 IS '尺码 ← order_bjd_query.chima（最近一条报价）';
COMMENT ON COLUMN v_product_genealogy.last_quotation_dh     IS '最近报价单号 ← order_bjd_query.dh（按 zhdate 最新）';
COMMENT ON COLUMN v_product_genealogy.last_saleprice        IS '最近产品售价 ← order_bjd_query.saleprice（最近一条报价）';
COMMENT ON COLUMN v_product_genealogy.last_sales_cost       IS '最近销售成本 ← order_bjd_query.xscb（最近一条报价）';
COMMENT ON COLUMN v_product_genealogy.process_sewing_weight IS '工艺单缝拼克重(权威) ← order_sw_gongyidan.pfkz（按 bh 最新版本；报价 fpkz 兜底源）';
COMMENT ON COLUMN v_product_genealogy.process_seconds       IS '工艺单下机秒数(权威) ← order_sw_gongyidan.xjsl（产能计算标准输入）';
COMMENT ON COLUMN v_product_genealogy.process_theory_output IS '工艺单理论产量 ← order_sw_gongyidan.djcl（产能基准，可对比排产实际算利用率偏差）';
COMMENT ON COLUMN v_product_genealogy.process_machine_type  IS '工艺单机型 ← order_sw_gongyidan.jix';
COMMENT ON COLUMN v_product_genealogy.process_needles       IS '工艺单针数 ← order_sw_gongyidan.zs';
COMMENT ON COLUMN v_product_genealogy.sales_order_cnt       IS '真实订单数 ← order_xs_list COUNT(DISTINCT dh)，按 detailhuohao 聚合';
COMMENT ON COLUMN v_product_genealogy.sales_total_qty       IS '真实订单数量合计 ← order_xs_list SUM(sl_sum)';
COMMENT ON COLUMN v_product_genealogy.latest_delivery       IS '最晚交期 ← order_xs_list MAX(jh_date)';
COMMENT ON COLUMN v_product_genealogy.plan_machine_type     IS '排产机型 ← production_plan.machine_type（按 id 最新一条）';
COMMENT ON COLUMN v_product_genealogy.plan_machine_count    IS '投入机台数 ← production_plan.machine_count';
COMMENT ON COLUMN v_product_genealogy.plan_output           IS '单机日产量 ← production_plan.single_machine_output';

-- 5.2 客户360视图：报价维度 + 成交维度一次查全
CREATE OR REPLACE VIEW v_customer_360 AS
SELECT
  COALESCE(q.khname, o.khname)               AS khname,          -- 客户名称（统一关联键）
  q.quotation_order_cnt                      AS quotation_cnt,   -- 报价单号数
  q.avg_saleprice                            AS avg_saleprice,   -- 平均售价
  q.avg_sales_cost                           AS avg_sales_cost,  -- 平均销售成本
  q.avg_profit                               AS avg_unit_profit, -- 平均单品毛利
  q.last_quotation_date                      AS last_quote_date, -- 最近报价日期
  o.sales_order_cnt                          AS sales_order_cnt, -- 成交订单数
  o.sales_total_qty                          AS sales_total_qty, -- 成交数量合计
  o.last_order_date                          AS last_order_date, -- 最近下单日期
  o.latest_delivery                          AS latest_delivery, -- 最晚交期
  o.salespersons                             AS salespersons     -- 跟进业务员列表
FROM (SELECT khname, COUNT(DISTINCT dh) AS quotation_order_cnt,
             AVG(saleprice) AS avg_saleprice, AVG(xscb) AS avg_sales_cost,
             AVG(mlr_dp) AS avg_profit, MAX(zhdate) AS last_quotation_date
      FROM order_bjd_query
      WHERE khname IS NOT NULL AND khname <> ''
      GROUP BY khname) q
FULL OUTER JOIN (SELECT khname, COUNT(DISTINCT dh) AS sales_order_cnt,
                        COALESCE(SUM(sl_sum), 0) AS sales_total_qty,
                        MAX(zhdate) AS last_order_date, MAX(jh_date) AS latest_delivery,
                        STRING_AGG(DISTINCT ywyname, '、') AS salespersons
                 FROM order_xs_list
                 WHERE khname IS NOT NULL AND khname <> ''
                 GROUP BY khname) o ON o.khname = q.khname;

COMMENT ON VIEW v_customer_360 IS '客户360视图：报价统计(order_bjd_query) ⨝ 成交统计(order_xs_list)，统一关联键 khname';
COMMENT ON COLUMN v_customer_360.khname           IS '客户名称【统一关联键】← COALESCE(order_bjd_query.khname, order_xs_list.khname)，仅报价/仅成交客户也会保留';
COMMENT ON COLUMN v_customer_360.quotation_cnt    IS '报价单号数 ← order_bjd_query COUNT(DISTINCT dh) 按 khname 聚合';
COMMENT ON COLUMN v_customer_360.avg_saleprice    IS '平均售价 ← order_bjd_query AVG(saleprice)';
COMMENT ON COLUMN v_customer_360.avg_sales_cost   IS '平均销售成本 ← order_bjd_query AVG(xscb)';
COMMENT ON COLUMN v_customer_360.avg_unit_profit  IS '平均单品毛利 ← order_bjd_query AVG(mlr_dp)';
COMMENT ON COLUMN v_customer_360.last_quote_date  IS '最近报价日期 ← order_bjd_query MAX(zhdate)';
COMMENT ON COLUMN v_customer_360.sales_order_cnt  IS '成交订单数 ← order_xs_list COUNT(DISTINCT dh) 按 khname 聚合';
COMMENT ON COLUMN v_customer_360.sales_total_qty  IS '成交数量合计 ← order_xs_list SUM(sl_sum)';
COMMENT ON COLUMN v_customer_360.last_order_date  IS '最近下单日期 ← order_xs_list MAX(zhdate)';
COMMENT ON COLUMN v_customer_360.latest_delivery  IS '最晚交期 ← order_xs_list MAX(jh_date)（交期风险识别输入）';
COMMENT ON COLUMN v_customer_360.salespersons     IS '跟进业务员列表 ← order_xs_list STRING_AGG(DISTINCT ywyname)，顿号分隔';

-- 5.3 原料价格视图：采购最低价/最高价/供应商数 + 入库参考价
CREATE OR REPLACE VIEW v_material_price AS
SELECT
  p.material_code                            AS material_code,   -- 原料编码（统一关联键）
  COUNT(DISTINCT p.supplier)                 AS supplier_count,  -- 供应商数
  MIN(p.unit_price)                          AS min_price,       -- 采购最低价
  MAX(p.unit_price)                          AS max_price,       -- 采购最高价
  AVG(p.unit_price)                          AS avg_price,       -- 采购均价
  w.warehouse_price                          AS warehouse_price  -- 入库参考价
FROM raw_material_purchase p
LEFT JOIN (SELECT product_code, AVG(unit_price) AS warehouse_price
           FROM raw_material_warehouse
           WHERE product_code IS NOT NULL AND product_code <> ''
           GROUP BY product_code) w ON w.product_code = p.material_code
WHERE p.material_code IS NOT NULL AND p.material_code <> ''
GROUP BY p.material_code, w.warehouse_price;

COMMENT ON VIEW v_material_price IS '原料价格视图：采购(raw_material_purchase.material_code) ⨝ 入库(raw_material_warehouse.product_code)，智能报价取 min_price';
COMMENT ON COLUMN v_material_price.material_code   IS '原料编码【统一关联键】← raw_material_purchase.material_code（= raw_material_warehouse.product_code = product_quotation.raw_material_name1~6）';
COMMENT ON COLUMN v_material_price.supplier_count  IS '供应商数 ← raw_material_purchase COUNT(DISTINCT supplier)';
COMMENT ON COLUMN v_material_price.min_price       IS '采购最低价 ← raw_material_purchase MIN(unit_price)【智能报价取价字段】';
COMMENT ON COLUMN v_material_price.max_price       IS '采购最高价 ← raw_material_purchase MAX(unit_price)（与最低价算节省比例）';
COMMENT ON COLUMN v_material_price.avg_price       IS '采购均价 ← raw_material_purchase AVG(unit_price)';
COMMENT ON COLUMN v_material_price.warehouse_price IS '入库参考价 ← raw_material_warehouse AVG(unit_price) 按 product_code 聚合';
