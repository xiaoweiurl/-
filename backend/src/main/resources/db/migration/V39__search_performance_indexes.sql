-- V39: 知识库搜索性能优化索引
-- 针对 knowledge_embeddings 表数据量增大后的查询优化

-- 1. 启用 pg_trgm 扩展（支持 trigram 索引加速 ILIKE 查询）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. 复合索引：source_type + company（最常用的 WHERE 条件组合）
CREATE INDEX IF NOT EXISTS idx_ke_source_type_company 
    ON knowledge_embeddings(source_type, company);

-- 3. chunk_text 的 trigram GIN 索引（加速 ILIKE '%keyword%' 查询）
--    pg_trgm 的 gin_trgm_ops 可以将 ILIKE 查询从全表扫描变为索引扫描
CREATE INDEX IF NOT EXISTS idx_ke_chunk_text_trgm 
    ON knowledge_embeddings USING gin(chunk_text gin_trgm_ops);

-- 4. source_doc_id 索引（加速 LEFT JOIN）
CREATE INDEX IF NOT EXISTS idx_ke_source_doc_id 
    ON knowledge_embeddings(source_doc_id);

-- 5. created_at 索引（加速 ORDER BY created_at DESC）
CREATE INDEX IF NOT EXISTS idx_ke_created_at 
    ON knowledge_embeddings(created_at DESC);

-- 6. knowledge_base_docs 表的索引优化
CREATE INDEX IF NOT EXISTS idx_kbd_company ON knowledge_base_docs(company);
CREATE INDEX IF NOT EXISTS idx_kbd_category_id ON knowledge_base_docs(category_id);

-- 7. knowledge_base_docs 的 title 和 file_name 的 trigram 索引
CREATE INDEX IF NOT EXISTS idx_kbd_title_trgm 
    ON knowledge_base_docs USING gin(title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_kbd_file_name_trgm 
    ON knowledge_base_docs USING gin(file_name gin_trgm_ops);

-- 8. knowledge_base_categories 的 company 索引
CREATE INDEX IF NOT EXISTS idx_kbc_company ON knowledge_base_categories(company);

-- 9. 为 knowledge_embeddings 添加搜索优化列（预计算的 tsvector）
--    避免每次查询时实时计算 to_tsvector
ALTER TABLE knowledge_embeddings ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- 10. 创建 tsvector 的 GIN 索引
CREATE INDEX IF NOT EXISTS idx_ke_search_vector 
    ON knowledge_embeddings USING gin(search_vector);

-- 11. 触发器：自动维护 search_vector 列
CREATE OR REPLACE FUNCTION update_ke_search_vector() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', COALESCE(NEW.chunk_text, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_ke_search_vector ON knowledge_embeddings;
CREATE TRIGGER trg_ke_search_vector
    BEFORE INSERT OR UPDATE OF chunk_text ON knowledge_embeddings
    FOR EACH ROW EXECUTE FUNCTION update_ke_search_vector();

-- 12. 回填现有数据的 search_vector
UPDATE knowledge_embeddings 
SET search_vector = to_tsvector('simple', COALESCE(chunk_text, ''))
WHERE search_vector IS NULL;
