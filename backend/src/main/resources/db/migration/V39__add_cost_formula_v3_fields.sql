-- ============================================================
-- V39: 成本计算公式V3 - 新增用户复杂公式所需字段
-- 新公式:
--   日产量 = 24*3600/下机时间 * 利用率
--   织造成本 = 机台费/日产量
--   染色成本 = 缝拼克重 * 染色单价
--   原料合计 = 原料BOM明细合计金额 (Σ 用量*单价/1000)
--   原料金额 = 原料合计 * (1 + 1 - 原料利用率) = 原料合计 * (2 - 原料利用率)
--   前道合计 = 织造成本 + 前道管理费用 + 染色成本 + 定型 + 其他工价 + 原料金额 + 缝制工价
--   辅料合计 = 辅料BOM明细合计金额
--   辅料金额 = 辅料合计 * (2 - 辅料利用率)
--   后道合计 = 包装 + 后道管理费用 + 辅料金额
--   净成本 = (织造成本+染色成本+定型+其他工价+原料金额+缝制工价+包装)*(2-正品率) + 辅料金额 + 前道管理费用 + 后道管理费用
--   理论税金 = 净成本 * 0.08
--   实际税金 = 默认理论税金（可修改）
--   销售成本 = 净成本 + 运费 + 实际税金
-- ============================================================

-- 利用率（下机时间利用率，百分比，如93表示93%）
ALTER TABLE product_quotation ADD COLUMN IF NOT EXISTS utilization_rate DECIMAL(5,2) DEFAULT 93.00;
COMMENT ON COLUMN product_quotation.utilization_rate IS '下机利用率(%)';

-- 原料利用率（百分比）
ALTER TABLE product_quotation ADD COLUMN IF NOT EXISTS material_utilization_rate DECIMAL(5,2) DEFAULT 95.00;
COMMENT ON COLUMN product_quotation.material_utilization_rate IS '原料利用率(%)';

-- 辅料利用率（百分比）
ALTER TABLE product_quotation ADD COLUMN IF NOT EXISTS accessory_utilization_rate DECIMAL(5,2) DEFAULT 95.00;
COMMENT ON COLUMN product_quotation.accessory_utilization_rate IS '辅料利用率(%)';

-- 正品率（百分比，如93表示93%）
-- yield_rate 已存在，直接复用

-- 前道管理费用
ALTER TABLE product_quotation ADD COLUMN IF NOT EXISTS front_management_cost DECIMAL(12,4) DEFAULT 0;
COMMENT ON COLUMN product_quotation.front_management_cost IS '前道管理费用(元/双)';

-- 后道管理费用
ALTER TABLE product_quotation ADD COLUMN IF NOT EXISTS rear_management_cost DECIMAL(12,4) DEFAULT 0;
COMMENT ON COLUMN product_quotation.rear_management_cost IS '后道管理费用(元/双)';

-- 缝制工价
ALTER TABLE product_quotation ADD COLUMN IF NOT EXISTS sewing_labor_cost DECIMAL(12,4) DEFAULT 0;
COMMENT ON COLUMN product_quotation.sewing_labor_cost IS '缝制工价(元/双)';

-- 其他工价
ALTER TABLE product_quotation ADD COLUMN IF NOT EXISTS other_labor_cost DECIMAL(12,4) DEFAULT 0;
COMMENT ON COLUMN product_quotation.other_labor_cost IS '其他工价(元/双)';

-- 运费
ALTER TABLE product_quotation ADD COLUMN IF NOT EXISTS freight_cost DECIMAL(12,4) DEFAULT 0;
COMMENT ON COLUMN product_quotation.freight_cost IS '运费(元/双)';

-- 实际税金（默认=理论税金，可手动修改）
-- tax_amount 已存在，复用

-- 缝拼克重（用于染色成本计算，克/双）
-- sewing_weight 已存在，复用

-- 下机时间（秒/双）- 用于日产量计算
-- weaving_seconds 已存在，复用

-- 机台费（元/天）- 用于织造成本计算
-- equipment_daily_cost 已存在，复用
