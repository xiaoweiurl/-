-- V42: 为报价表比率字段添加单位注释（百分比，如 94 表示 94%）
-- 不改动现有数据，代码层通过 pct() 归一化处理

COMMENT ON COLUMN order_bjd_query.lyl IS '利用率（百分比，如 94 表示 94%）';
COMMENT ON COLUMN order_bjd_query.zpl IS '正品率（百分比，如 94 表示 94%）';
COMMENT ON COLUMN order_bjd_query.yllyl IS '原料利用率（百分比，如 94 表示 94%）';
COMMENT ON COLUMN order_bjd_query.fllyl IS '辅料利用率（百分比，如 94 表示 94%）';
