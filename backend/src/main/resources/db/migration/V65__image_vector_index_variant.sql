-- ============================================================
-- V65: 以图搜图进度按变体分开（full 整图 / crop 主体裁剪）
--
-- Flyway 自动迁移是关闭的。请在数据库里手动执行本文件，可以重复执行。
-- 不修改 images / goods_library，也不删除、不改写 Milvus 里已有的
-- image_vectors_vitl 和 salesperson_* 集合。
--
-- 示例（库名以 application-local.yml 为准，默认 image_management）：
--   psql -h localhost -U postgres -d image_management -f backend/src/main/resources/db/migration/V65__image_vector_index_variant.sql
-- ============================================================

ALTER TABLE image_vector_index
    ADD COLUMN IF NOT EXISTS variant varchar(16) NOT NULL DEFAULT 'full';

-- 旧的 (source, source_id, slot) 唯一索引会挡住同一张图的 crop 行。
DROP INDEX IF EXISTS uk_image_vector_index_item;

UPDATE image_vector_index
   SET variant = 'full'
 WHERE variant IS NULL OR btrim(variant) = '';

ALTER TABLE image_vector_index ALTER COLUMN variant SET DEFAULT 'full';
ALTER TABLE image_vector_index ALTER COLUMN variant SET NOT NULL;

DO $$
DECLARE
    def text;
    pkname text;
BEGIN
    SELECT conname, pg_get_constraintdef(oid) INTO pkname, def
      FROM pg_constraint
     WHERE conrelid = 'image_vector_index'::regclass
       AND contype = 'p';
    IF pkname IS NULL THEN
        ALTER TABLE image_vector_index ADD PRIMARY KEY (vector_id, variant);
    ELSIF def NOT ILIKE '%variant%' THEN
        EXECUTE format('ALTER TABLE image_vector_index DROP CONSTRAINT %I', pkname);
        ALTER TABLE image_vector_index ADD PRIMARY KEY (vector_id, variant);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_image_vector_index_item
    ON image_vector_index (variant, source, source_id, slot);

CREATE INDEX IF NOT EXISTS idx_image_vector_index_status
    ON image_vector_index (status);

COMMENT ON COLUMN image_vector_index.variant IS 'full=整图集合，crop=主体裁剪集合。同一张图可以各有一行';
