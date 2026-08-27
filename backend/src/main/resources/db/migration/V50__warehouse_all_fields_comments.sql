-- =====================================================================
-- V50: raw_material_warehouse 全字段注释兜底（兼容多版本表结构）
-- 背景：
--   线上存在早期 DDL 版本的表（仓库语义字段：material_id/warehouse_number/
--   warehouse_name/location/stock_quantity/status/user_id/created_at/updated_at），
--   这些字段此前没有任何注释；且 V49 在部分环境执行中断，Excel 新列注释缺失。
-- 本迁移做两件事：
--   1) 结构收敛兜底：幂等补齐 Excel 入库语义 17 列（防 V49 半途失败环境）
--   2) 两套字段全集注释：仓库语义列 + Excel 入库语义列，全部 DO 块保护式
-- 兼容性：
--   - 任何版本的表结构（schema_complete 仓库版 / schema_business_data 入库版 /
--     V49 收敛版）都能完整执行，列不存在时跳过该列注释，绝不报错
--   - 重复执行无副作用
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 结构收敛兜底（幂等）：Excel 入库语义列补齐
--    与 V49 第2节一致；表已是完整结构时全部跳过
-- ---------------------------------------------------------------------
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS product_code    VARCHAR(128);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS color           VARCHAR(100);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS batch_no        VARCHAR(100);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS unit            VARCHAR(50);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS unit_price      NUMERIC(18, 4) DEFAULT 0;
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS company         VARCHAR(50);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS huohao          VARCHAR(128);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS size            VARCHAR(50);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS component       VARCHAR(100);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS supplier        VARCHAR(200);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS material_name   VARCHAR(200);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS specification   VARCHAR(200);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS material_color  VARCHAR(100);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS twist_direction VARCHAR(20);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS usage_per_unit  NUMERIC(12, 4);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS loss_rate       NUMERIC(8, 2);
ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS remark          TEXT;

-- ---------------------------------------------------------------------
-- 2. 表注释（说明两套字段语义共存的历史背景）
-- ---------------------------------------------------------------------
COMMENT ON TABLE raw_material_warehouse IS '原料入库表——按「成品货号+部件+物料」记录每双袜子的用料BOM与入库明细，对齐上游Excel14列结构（货号/颜色/尺码/部件/供应商/物料名称/规格/物料颜色/批号/捻向/单位/单件用量/损耗%/备注）。注意：早期DDL版本的仓库语义字段（material_id/warehouse_number/warehouse_name/location/stock_quantity/status）为历史遗留，当前业务以Excel入库明细字段为准';

-- ---------------------------------------------------------------------
-- 3. 全字段注释（DO 块循环 + 列存在性保护）
--    覆盖两套字段全集：仓库语义列（早期DDL版） + Excel入库语义列（V49版）
--    列不存在时自动跳过，不报错；已存在的注释被覆盖为最新文本（幂等）
-- ---------------------------------------------------------------------
DO $$
DECLARE
    comments text[][] := ARRAY[
        -- ===== 公共列 =====
        ['id',               '自增主键'],
        -- ===== 早期DDL仓库语义列（列存在才生效） =====
        ['material_id',      '物料ID（早期DDL版本外键，指向 materials.id；当前系统未启用该关联，仅历史数据追溯用）'],
        ['warehouse_number', '仓库编号（早期DDL仓库管理语义遗留字段；当前业务以Excel入库明细字段为准）'],
        ['warehouse_name',   '仓库名称（早期DDL仓库管理语义遗留字段；当前业务以Excel入库明细字段为准）'],
        ['location',         '库位（早期DDL仓库管理语义遗留字段；当前业务以Excel入库明细字段为准）'],
        ['stock_quantity',   '库存数量（早期DDL仓库管理语义遗留字段，单位见unit列；当前业务以Excel入库明细字段为准）'],
        ['status',           '状态（早期DDL遗留字段：如启用/停用；当前业务以Excel入库明细字段为准）'],
        ['user_id',          '归属用户ID（早期DDL遗留字段，创建该记录的用户，关联users.id）'],
        ['created_at',       '创建时间'],
        ['updated_at',       '更新时间'],
        -- ===== Excel入库语义列（V49 对齐上游14列表头） =====
        ['huohao',           '成品货号【关联键】Excel表头"货号"；关联 order_bjd_query.huohao / order_xs_list.detailhuohao / production_plan.product_code，v_product_genealogy 谱系入口'],
        ['color',            '颜色（成品颜色）Excel表头"颜色"；注意与 material_color（物料颜色）区分'],
        ['size',             '尺码 Excel表头"尺码"；如 22-24 / 26-28，对应 order_bjd_query.chima'],
        ['component',        '部件 Excel表头"部件"；如袜口/袜筒/脚尖/橡筋，同一货号不同部件分别用料'],
        ['supplier',         '供应商 Excel表头"供应商"；与 raw_material_purchase.supplier 同名等价，供应商比价维度'],
        ['material_name',    '物料名称 Excel表头"物料名称"；如锦纶丝/氨纶包覆纱/橡筋线'],
        ['specification',    '规格 Excel表头"规格"；如 70D/24F、2070/48F'],
        ['material_color',   '物料颜色 Excel表头"物料颜色"；原料本身的颜色（Z本色/黑/白等）'],
        ['product_code',     '原料编码（XF1202020/XB55/24F 等）；与 raw_material_purchase.material_code 等价，v_material_price 取价关联键'],
        ['batch_no',         '批号 Excel表头"批号"；与 raw_material_purchase.batch_no 对应，同批号采购价可比'],
        ['twist_direction',  '捻向 Excel表头"捻向"；S捻 / Z捻'],
        ['unit',             '单位 Excel表头"单位"；kg / g / 双 等'],
        ['usage_per_unit',   '单件用量 Excel表头"单件用量"；每双（件）成品消耗的物料量，智能报价「用量×采购最低价」的用量来源'],
        ['loss_rate',        '损耗率(%) Excel表头"损耗(%)"；净用量 = 单件用量 × (1 + loss_rate/100)'],
        ['unit_price',       '入库单价（元）Excel无此列，本地成本核算保留；与 raw_material_purchase.unit_price 采购价对比可看价差'],
        ['remark',           '备注 Excel表头"备注"'],
        ['company',          '公司/租户标识（V37统一补充列）']
    ];
    i int;
BEGIN
    FOR i IN 1..array_length(comments, 1) LOOP
        IF EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = current_schema()
                     AND table_name  = 'raw_material_warehouse'
                     AND column_name = comments[i][1]) THEN
            EXECUTE format('COMMENT ON COLUMN raw_material_warehouse.%I IS %L',
                           comments[i][1], comments[i][2]);
        END IF;
    END LOOP;
END $$;
