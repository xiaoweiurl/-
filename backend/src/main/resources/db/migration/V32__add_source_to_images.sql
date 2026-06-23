-- 给主表 images 添加 source 字段，区分知识图片和二创AI图片
ALTER TABLE images ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'knowledge';

-- 给所有动态用户表 images_xxx 添加 source 字段
-- 动态表名格式: images_<用户名>，用户名可以是字母、数字、中文等
DO $$
DECLARE
    tbl RECORD;
BEGIN
    FOR tbl IN 
        SELECT tablename FROM pg_tables 
        WHERE schemaname = 'public'
        AND tablename LIKE 'images_%'
        AND tablename != 'images'
    LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT %L', tbl.tablename, 'knowledge');
    END LOOP;
END$$;
