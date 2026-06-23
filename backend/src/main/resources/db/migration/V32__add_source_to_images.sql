-- 给主表 images 添加 source 字段，区分知识图片和二创AI图片
ALTER TABLE images ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'knowledge';

-- 给所有动态用户表 images_xxx 添加 source 字段
DO $$
DECLARE
    tbl RECORD;
BEGIN
    FOR tbl IN 
        SELECT tablename FROM pg_tables 
        WHERE tablename LIKE 'images_%' AND tablename NOT LIKE 'images\_%' ESCAPE '\'
    LOOP
        EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT %L', tbl.tablename, 'creative');
    END LOOP;
END$$;
