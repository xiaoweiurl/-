-- ============================================================
-- V54: 品牌统一 - 全库 company 字段统一为「宝娜斯集团」
-- 说明：动态遍历 public schema 下所有含 company 字段的表，
--       将 company 值（含空值/NULL）统一更新为「宝娜斯集团」。
--       该脚本可重复执行（幂等）。
-- ============================================================

DO $$
DECLARE
  t RECORD;
  affected INTEGER;
  total INTEGER := 0;
BEGIN
  FOR t IN
    SELECT DISTINCT c.table_name
    FROM information_schema.columns c
    WHERE c.table_schema = 'public'
      AND c.column_name = 'company'
    ORDER BY c.table_name
  LOOP
    EXECUTE format(
      'UPDATE public.%I SET company = %L WHERE company IS DISTINCT FROM %L',
      t.table_name, '宝娜斯集团', '宝娜斯集团'
    );
    GET DIAGNOSTICS affected = ROW_COUNT;
    IF affected > 0 THEN
      RAISE NOTICE '表 % : 更新 % 行', t.table_name, affected;
      total := total + affected;
    END IF;
  END LOOP;
  RAISE NOTICE 'company 字段统一完成，共更新 % 行', total;
END $$;
