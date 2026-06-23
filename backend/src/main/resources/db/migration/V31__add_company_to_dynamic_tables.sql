-- V31: 给所有动态用户图片表添加 company 字段，支持按公司隔离

-- 获取所有 images_ 开头的表，逐个添加 company 字段
DO $$
DECLARE
    tbl RECORD;
BEGIN
    FOR tbl IN 
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name LIKE 'images_%' 
        AND table_type = 'BASE TABLE'
    LOOP
        -- 检查 company 字段是否已存在
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns 
            WHERE table_name = tbl.table_name 
            AND column_name = 'company'
        ) THEN
            EXECUTE format('ALTER TABLE %I ADD COLUMN company VARCHAR(50)', tbl.table_name);
            RAISE NOTICE 'Added company column to table %', tbl.table_name;
        ELSE
            RAISE NOTICE 'Company column already exists in table %', tbl.table_name;
        END IF;
    END LOOP;
END $$;

-- 从主表 images 同步 company 值到动态表
-- 动态表和主表通过 id 字段关联
DO $$
DECLARE
    tbl RECORD;
BEGIN
    FOR tbl IN 
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name LIKE 'images_%' 
        AND table_type = 'BASE TABLE'
    LOOP
        -- 更新动态表中的 company 值（从主表同步）
        EXECUTE format('
            UPDATE %I dt 
            SET company = i.company 
            FROM images i 
            WHERE dt.id = i.id AND (dt.company IS NULL OR dt.company = '''') AND i.company IS NOT NULL
        ', tbl.table_name);
        RAISE NOTICE 'Synced company values for table %', tbl.table_name;
    END LOOP;
END $$;
