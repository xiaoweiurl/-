-- V38__fix_embedding_column_type.sql
-- 修复 knowledge_embeddings.embedding 列类型：FLOAT8[] → vector(1024)
-- FLOAT8[] 类型不支持 pgvector 的 <=> 余弦距离操作符，导致所有向量搜索SQL失败

-- Step 1: 先检查当前列类型，只有 FLOAT8[] 时才转换
DO $$
DECLARE
    col_type VARCHAR;
BEGIN
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_name = 'knowledge_embeddings' AND column_name = 'embedding';
    
    RAISE NOTICE '当前 embedding 列类型: %', col_type;
    
    -- 如果是 ARRAY 类型（FLOAT8[]），需要转换为 vector
    IF col_type = 'ARRAY' THEN
        -- 将 FLOAT8[] 的文本表示 {1.0,2.0,...} 转为 pgvector 格式 [1.0,2.0,...]
        -- pgvector 要求方括号格式，不是圆括号
        ALTER TABLE knowledge_embeddings ALTER COLUMN embedding TYPE vector(1024)
        USING replace(replace(embedding::text, '{', '['), '}', ']')::vector;
        RAISE NOTICE 'embedding 列已从 FLOAT8[] 转换为 vector(1024)';
    ELSE
        RAISE NOTICE 'embedding 列已经是 vector 类型，跳过转换';
    END IF;
END $$;

-- Step 2: 重建 HNSW 索引（向量搜索性能关键）
DROP INDEX IF EXISTS idx_knowledge_embeddings_hnsw;
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_hnsw
ON knowledge_embeddings USING hnsw (embedding vector_cosine_ops);

-- Step 3: 重建 source_type 和 source_doc_id 索引
CREATE INDEX IF NOT EXISTS idx_embeddings_source_type ON knowledge_embeddings(source_type);
CREATE INDEX IF NOT EXISTS idx_embeddings_source_doc_id ON knowledge_embeddings(source_doc_id);
