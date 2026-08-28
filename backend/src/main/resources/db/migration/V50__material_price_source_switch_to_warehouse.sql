-- V50: ERP 无采购数据，原料价格基准源从 raw_material_purchase 切换到 raw_material_warehouse
-- 背景：
--   raw_material_purchase 无上游数据（ERP 不写采购数据，表已空，V47 曾补种的模拟种子一并清除）；
--   raw_material_warehouse 有真实入库明细（product_code/unit_price/supplier/batch_no），
--   入库记录本质是实际采购入库的价格快照，作为智能报价"用量×最低价"的取价基准。
-- 兼容性：v_material_price 保留旧输出列名（material_code/supplier_count/min_price/max_price/avg_price），
--   下游（智能报价取价、AI 供应商对比、数据字典）无需改列名；warehouse_price 列删除（价格已同源，无参考意义），
--   新增 record_count/suppliers 两列增强可观测性。

-- 1. 采购表停用标注（结构保留，兼容历史代码与未来 ERP 恢复写入）
COMMENT ON TABLE raw_material_purchase IS '原料采购表【已停用】ERP 无采购数据，价格基准源已切换 raw_material_warehouse；本表仅保留结构，不参与报价计算';

-- 2. 清除 V47 补种的模拟种子数据（生产环境执行本迁移时同样生效，保证表为空）
DELETE FROM raw_material_purchase;

-- 3. 重写 v_material_price：价格源 = 原料入库表（列集变化，必须 DROP 后重建）
DROP VIEW IF EXISTS v_material_price;
CREATE VIEW v_material_price AS
SELECT
  w.product_code                                        AS material_code,
  COUNT(DISTINCT NULLIF(w.supplier, ''))                AS supplier_count,
  MIN(w.unit_price)                                     AS min_price,
  MAX(w.unit_price)                                     AS max_price,
  AVG(w.unit_price)                                     AS avg_price,
  COUNT(*)                                              AS record_count,
  STRING_AGG(DISTINCT NULLIF(w.supplier, ''), '、')      AS suppliers
FROM raw_material_warehouse w
WHERE w.product_code IS NOT NULL AND w.product_code <> ''
  AND w.unit_price IS NOT NULL
GROUP BY w.product_code;

COMMENT ON VIEW v_material_price IS '原料价格视图：价格源=原料入库表 raw_material_warehouse（按 product_code 聚合），智能报价取 min_price；raw_material_purchase 已停用（ERP 无采购数据）';
COMMENT ON COLUMN v_material_price.material_code  IS '原料编码【统一关联键】← raw_material_warehouse.product_code（= product_quotation.raw_material_name1~6 = order_bjd_query.huohao 体系）';
COMMENT ON COLUMN v_material_price.supplier_count IS '供应商数 ← raw_material_warehouse COUNT(DISTINCT supplier)（入库供应商）';
COMMENT ON COLUMN v_material_price.min_price      IS '最低入库价 ← raw_material_warehouse MIN(unit_price)【智能报价取价字段】';
COMMENT ON COLUMN v_material_price.max_price      IS '最高入库价 ← raw_material_warehouse MAX(unit_price)（与最低价算节省比例）';
COMMENT ON COLUMN v_material_price.avg_price      IS '入库均价 ← raw_material_warehouse AVG(unit_price)';
COMMENT ON COLUMN v_material_price.record_count   IS '入库记录数 ← raw_material_warehouse COUNT(*)（价格样本量，越多越可信）';
COMMENT ON COLUMN v_material_price.suppliers      IS '供应商列表 ← STRING_AGG(DISTINCT supplier)，顿号分隔（无供应商数据时为 NULL）';
