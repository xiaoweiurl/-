-- ============================================================
-- V64: 以图搜图回填进度
--
-- Flyway 自动迁移是关闭的。请在数据库里手动执行本文件，可以重复执行。
-- 不修改 images / goods_library，也不触碰 Milvus 里已有的 salesperson_* 集合。
--
-- 示例（库名以你的 application-local.yml 为准，默认 image_management）：
--   psql -h localhost -U postgres -d image_management -f backend/src/main/resources/db/migration/V64__image_vector_index.sql
-- 也可以在 IntelliJ IDEA 的 Database 控制台里打开本文件执行。
-- ============================================================

CREATE TABLE IF NOT EXISTS image_vector_index (
    vector_id       varchar(160) PRIMARY KEY,
    source          varchar(16)  NOT NULL,
    source_id       varchar(64)  NOT NULL,
    slot            varchar(16)  NOT NULL DEFAULT '',
    company         varchar(64),
    oss_key         varchar(1000),
    content_sha256  varchar(64),
    status          varchar(16)  NOT NULL,
    error_message   text,
    attempts        integer      NOT NULL DEFAULT 0,
    indexed_at      timestamp,
    updated_at      timestamp    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_image_vector_index_item
    ON image_vector_index (source, source_id, slot);

CREATE INDEX IF NOT EXISTS idx_image_vector_index_status
    ON image_vector_index (status);

COMMENT ON TABLE image_vector_index IS '以图搜图回填进度。DONE 且对象键未变则跳过，FAILED 可 --only-failed 重试';
COMMENT ON COLUMN image_vector_index.source IS 'library=素材库 images，goods=商品库/打样 goods_library';
COMMENT ON COLUMN image_vector_index.status IS 'DONE / FAILED / SKIPPED';
