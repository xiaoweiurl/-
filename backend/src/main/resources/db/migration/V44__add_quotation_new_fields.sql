-- =====================================================
-- order_bjd_query 报价单表 - 新增上游实体字段
-- 上游 C# 实体新增字段同步（报价核算公式已同步更新）：
--   染色成本 = 表内 rsprice 存储值优先，否则 fpkz × rsdj
--   前道合计 += 腰口工价 ykgj
--   后道合计 += 全检工价 qjprice
--   净成本   = (织造+染色+定型+其他工价+原料+缝制+腰口工价+全检工价+包装)×(1-正品率+1)+辅料+前道管理费+后道管理费
-- 幂等：ADD COLUMN IF NOT EXISTS，列已存在时跳过
-- =====================================================

-- 染色单价（元/克或元/公斤，与上游一致）
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS rsdj NUMERIC(18,4);
-- 缝拼克重（克）
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS fpkz NUMERIC(18,4);
-- 染色成本（上游系统已计算的存储值，核算时优先使用）
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS rsprice NUMERIC(18,4);
-- 全检工价（质检工价，元/件）
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS qjprice NUMERIC(18,4);
-- 腰口工价（缝制类工价，元/件）
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS ykgj NUMERIC(18,4);
-- 前道规格（文本参数，如针数/纱线规格）
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS qdzs TEXT;
-- 后道规格（文本参数，如针数/纱线规格）
ALTER TABLE order_bjd_query ADD COLUMN IF NOT EXISTS hdzs TEXT;

COMMENT ON COLUMN order_bjd_query.rsdj    IS '染色单价';
COMMENT ON COLUMN order_bjd_query.fpkz    IS '缝拼克重（克）';
COMMENT ON COLUMN order_bjd_query.rsprice IS '染色成本（上游存储值，核算优先使用；缺省时按 fpkz×rsdj 计算）';
COMMENT ON COLUMN order_bjd_query.qjprice IS '全检工价（元/件，计入后道合计与净成本）';
COMMENT ON COLUMN order_bjd_query.ykgj    IS '腰口工价（元/件，缝制类工价，计入前道合计与净成本）';
COMMENT ON COLUMN order_bjd_query.qdzs    IS '前道规格（文本参数，不参与成本计算）';
COMMENT ON COLUMN order_bjd_query.hdzs    IS '后道规格（文本参数，不参与成本计算）';
