-- =====================================================================
-- V49: raw_material_warehouse 原料入库表字段补齐与全列注释
-- 对齐上游 Excel 14 列业务结构：货号/颜色/尺码/部件/供应商/物料名称/
--   规格/物料颜色/批号/捻向/单位/单件用量/损耗(%)/备注
-- 兼容性：
--   1) 已存在的表（schema_business_data 版或 V37 版）只补缺失列，不动存量数据
--   2) 全新环境按完整结构建表（冷启动兜底）
--   3) 重复执行无副作用（全部 IF NOT EXISTS）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. 冷启动兜底建表（表已存在时跳过；结构 = 存量7列 + Excel新增11列）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS raw_material_warehouse (
    id               SERIAL PRIMARY KEY,
    product_code     VARCHAR(128),
    color            VARCHAR(100),
    batch_no         VARCHAR(100),
    unit             VARCHAR(50),
    unit_price       NUMERIC(18, 4) DEFAULT 0,
    company          VARCHAR(50),
    -- 以下为 Excel 对齐新增列
    huohao           VARCHAR(128),
    size             VARCHAR(50),
    component        VARCHAR(100),
    supplier         VARCHAR(200),
    material_name    VARCHAR(200),
    specification    VARCHAR(200),
    material_color   VARCHAR(100),
    twist_direction  VARCHAR(20),
    usage_per_unit   NUMERIC(12, 4),
    loss_rate        NUMERIC(8, 2),
    remark           TEXT
);

-- ---------------------------------------------------------------------
-- 2. 幂等补列（表已存在但缺列时补齐；列已存在时跳过）
--    同时覆盖 V37 变体（无 product_code/color/batch_no/unit_price）与
--    schema_business_data 变体（无 Excel 新列），任何历史结构都能收敛
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
-- 3. 表注释 + 全列注释（Excel 表头含义 + 业务语义 + 关联键标注）
-- ---------------------------------------------------------------------
COMMENT ON TABLE raw_material_warehouse IS '原料入库表——按「成品货号+部件+物料」记录每双袜子的用料BOM与入库明细，对齐上游Excel14列结构（货号/颜色/尺码/部件/供应商/物料名称/规格/物料颜色/批号/捻向/单位/单件用量/损耗%/备注）';

COMMENT ON COLUMN raw_material_warehouse.id IS '自增主键';
COMMENT ON COLUMN raw_material_warehouse.huohao IS '成品货号【关联键】Excel表头"货号"；关联 order_bjd_query.huohao / order_xs_list.detailhuohao / production_plan.product_code，v_product_genealogy 谱系入口';
COMMENT ON COLUMN raw_material_warehouse.color IS '颜色（成品颜色）Excel表头"颜色"；注意与 material_color（物料颜色）区分';
COMMENT ON COLUMN raw_material_warehouse.size IS '尺码 Excel表头"尺码"；如 22-24 / 26-28，对应 order_bjd_query.chima';
COMMENT ON COLUMN raw_material_warehouse.component IS '部件 Excel表头"部件"；如袜口/袜筒/脚尖/橡筋，同一货号不同部件分别用料';
COMMENT ON COLUMN raw_material_warehouse.supplier IS '供应商 Excel表头"供应商"；与 raw_material_purchase.supplier 同名等价，供应商比价维度';
COMMENT ON COLUMN raw_material_warehouse.material_name IS '物料名称 Excel表头"物料名称"；如锦纶丝/氨纶包覆纱/橡筋线';
COMMENT ON COLUMN raw_material_warehouse.specification IS '规格 Excel表头"规格"；如 70D/24F、2070/48F';
COMMENT ON COLUMN raw_material_warehouse.material_color IS '物料颜色 Excel表头"物料颜色"；原料本身的颜色（Z本色/黑/白等）';
COMMENT ON COLUMN raw_material_warehouse.product_code IS '原料编码（XF1202020/XB55/24F 等）；与 raw_material_purchase.material_code 等价，v_material_price 取价关联键';
COMMENT ON COLUMN raw_material_warehouse.batch_no IS '批号 Excel表头"批号"；与 raw_material_purchase.batch_no 对应，同批号采购价可比';
COMMENT ON COLUMN raw_material_warehouse.twist_direction IS '捻向 Excel表头"捻向"；S捻 / Z捻';
COMMENT ON COLUMN raw_material_warehouse.unit IS '单位 Excel表头"单位"；kg / g / 双 等';
COMMENT ON COLUMN raw_material_warehouse.usage_per_unit IS '单件用量 Excel表头"单件用量"；每双（件）成品消耗的物料量，智能报价「用量×采购最低价」的用量来源';
COMMENT ON COLUMN raw_material_warehouse.loss_rate IS '损耗率(%) Excel表头"损耗(%)"；净用量 = 单件用量 × (1 + loss_rate/100)';
COMMENT ON COLUMN raw_material_warehouse.unit_price IS '入库单价（元）Excel无此列，本地成本核算保留；与 raw_material_purchase.unit_price 采购价对比可看价差';
COMMENT ON COLUMN raw_material_warehouse.remark IS '备注 Excel表头"备注"';
COMMENT ON COLUMN raw_material_warehouse.company IS '公司/租户标识（V37统一补充列）';

-- ---------------------------------------------------------------------
-- 4. 关联键与检索索引
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rmw_huohao        ON raw_material_warehouse (huohao);
CREATE INDEX IF NOT EXISTS idx_rmw_supplier      ON raw_material_warehouse (supplier);
CREATE INDEX IF NOT EXISTS idx_rmw_material_name ON raw_material_warehouse (material_name);
