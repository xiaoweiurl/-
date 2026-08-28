-- =====================================================================
-- V51: 清除原料入库表伪造种子数据 + 标注非报表字段 + 重建原料价格视图
-- 背景：raw_material_warehouse 实为「原料统计报表」（Excel 导入，仅 14 个报表字段），
--       报表不含 单价/原料编码/公司 列；V47 曾补种的演示行含编造单价，必须清除。
--       真实物料标识 = 物料名称 + 规格；价格仅支持页面手工维护。
-- =====================================================================

-- 1. 清除 V47 伪造种子行
--    特征：无货号且无物料名称却有单价 —— 真实 Excel 导入必填货号/物料名称且不写单价，
--    该条件只会命中 V47 种子（9 行），绝不误删用户导入的报表数据
DELETE FROM raw_material_warehouse
WHERE huohao IS NULL AND material_name IS NULL AND unit_price IS NOT NULL;

-- 2. 修正非报表字段注释（Excel 导入不填充，防止再被当作真实数据源）
COMMENT ON COLUMN raw_material_warehouse.product_code IS '原料编码【非报表字段】原料统计报表Excel无此列，导入不填充，遗留字段；真实物料标识=物料名称+规格';
COMMENT ON COLUMN raw_material_warehouse.unit_price IS '单价【非报表字段】原料统计报表Excel无此列，导入不填充；仅页面手工维护，作为智能报价参考价与供应商对比数据源';
COMMENT ON COLUMN raw_material_warehouse.company IS '公司【非报表字段】原料统计报表Excel无此列，导入不填充';

-- 3. 重建原料价格视图：按真实报表字段（物料名称+规格）聚合
--    报表无价格列：min/max/avg_price 仅统计页面手工维护的单价；
--    无维护价格时价格列为 NULL，但 record_count 仍反映报表记录数
DROP VIEW IF EXISTS v_material_price;
CREATE VIEW v_material_price AS
SELECT
    w.material_name                                   AS material_name,
    NULLIF(w.specification, '')                       AS specification,
    COUNT(DISTINCT NULLIF(w.supplier, ''))            AS supplier_count,
    COUNT(*) FILTER (WHERE w.unit_price IS NOT NULL)  AS priced_count,
    MIN(w.unit_price)                                 AS min_price,
    MAX(w.unit_price)                                 AS max_price,
    AVG(w.unit_price)                                 AS avg_price,
    COUNT(*)                                          AS record_count,
    STRING_AGG(DISTINCT NULLIF(w.supplier, ''), '、') AS suppliers
FROM raw_material_warehouse w
WHERE w.material_name IS NOT NULL AND w.material_name <> ''
GROUP BY w.material_name, NULLIF(w.specification, '');

COMMENT ON VIEW v_material_price IS '原料价格视图：按 物料名称+规格（原料统计报表真实标识）聚合；报表本身无价格列，min/max/avg_price 仅统计页面手工维护的单价，无维护价格时价格列为NULL但record_count仍反映报表记录数';
COMMENT ON COLUMN v_material_price.material_name IS '物料名称（报表字段）';
COMMENT ON COLUMN v_material_price.specification IS '规格（报表字段），物料真实标识=物料名称+规格';
COMMENT ON COLUMN v_material_price.supplier_count IS '不同供应商数（报表supplier列去重）';
COMMENT ON COLUMN v_material_price.priced_count IS '已手工维护单价（unit_price非空）的记录数；0表示该物料尚未维护价格';
COMMENT ON COLUMN v_material_price.min_price IS '最低价（手工维护单价的最小值，智能报价参考价）';
COMMENT ON COLUMN v_material_price.max_price IS '最高价（手工维护单价的最大值）';
COMMENT ON COLUMN v_material_price.avg_price IS '均价（手工维护单价的平均值）';
COMMENT ON COLUMN v_material_price.record_count IS '报表记录总数（含未维护价格记录）';
COMMENT ON COLUMN v_material_price.suppliers IS '供应商列表（顿号分隔去重）';
