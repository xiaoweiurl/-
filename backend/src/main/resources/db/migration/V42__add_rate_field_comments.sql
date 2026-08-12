-- V42: 比率字段统一存小数（94 → 0.94），并添加单位注释

-- 1. 现有数据从百分数转为小数（仅转换 >1 的值，避免重复转换）
UPDATE order_bjd_query SET lyl = lyl / 100 WHERE lyl > 1;
UPDATE order_bjd_query SET zpl = zpl / 100 WHERE zpl > 1;
UPDATE order_bjd_query SET yllyl = yllyl / 100 WHERE yllyl > 1;
UPDATE order_bjd_query SET fllyl = fllyl / 100 WHERE fllyl > 1;

-- 2. 添加字段注释，明确单位为小数（如 0.94 表示 94%）
COMMENT ON COLUMN order_bjd_query.lyl IS '利用率（小数，如 0.94 表示 94%）';
COMMENT ON COLUMN order_bjd_query.zpl IS '正品率（小数，如 0.94 表示 94%）';
COMMENT ON COLUMN order_bjd_query.yllyl IS '原料利用率（小数，如 0.94 表示 94%）';
COMMENT ON COLUMN order_bjd_query.fllyl IS '辅料利用率（小数，如 0.94 表示 94%）';
