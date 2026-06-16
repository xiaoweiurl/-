-- 为知识库文档添加向量化状态字段
ALTER TABLE knowledge_base_docs
    ADD COLUMN IF NOT EXISTS chunk_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS file_content TEXT;

-- 为 knowledge_embeddings 表添加来源标识，支持知识库和记忆库共用
ALTER TABLE knowledge_embeddings
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) NOT NULL DEFAULT 'MEMORY',
    ADD COLUMN IF NOT EXISTS source_doc_id VARCHAR(100);

-- 创建索引加速知识库文档的向量检索
CREATE INDEX IF NOT EXISTS idx_embeddings_source_type ON knowledge_embeddings(source_type);
CREATE INDEX IF NOT EXISTS idx_embeddings_source_doc_id ON knowledge_embeddings(source_doc_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_docs_status ON knowledge_base_docs(embedding_status);

-- 创建统一向量检索函数（同时支持记忆库和知识库）
CREATE OR REPLACE FUNCTION search_knowledge_embeddings_unified(
    query_embedding vector(1024),
    p_source_type VARCHAR(20) DEFAULT NULL,
    p_domain_code VARCHAR(100) DEFAULT NULL,
    p_min_score DOUBLE PRECISION DEFAULT 0.3,
    p_limit INT DEFAULT 10
)
RETURNS TABLE(
    id UUID,
    card_id UUID,
    domain_code VARCHAR(100),
    product_code VARCHAR(100),
    title VARCHAR(500),
    content TEXT,
    source VARCHAR(500),
    confidence VARCHAR(50),
    created_by VARCHAR(36),
    created_at TIMESTAMP,
    chunk_text TEXT,
    score DOUBLE PRECISION
) AS $$
BEGIN
    IF p_source_type IS NOT NULL THEN
        -- 指定来源类型
        RETURN QUERY
        SELECT
            e.id,
            e.card_id,
            e.domain_code,
            NULL::VARCHAR(100) AS product_code,
            e.title,
            e.content,
            e.source,
            'high'::VARCHAR(50) AS confidence,
            NULL::VARCHAR(36) AS created_by,
            e.created_at,
            e.chunk_text,
            (1 - (e.embedding <=> query_embedding))::DOUBLE PRECISION AS score
        FROM knowledge_embeddings e
        WHERE e.source_type = p_source_type
          AND (1 - (e.embedding <=> query_embedding)) >= p_min_score
        ORDER BY e.embedding <=> query_embedding
        LIMIT p_limit;
    ELSE
        -- 同时搜索记忆库和知识库
        RETURN QUERY
        SELECT
            e.id,
            e.card_id,
            e.domain_code,
            NULL::VARCHAR(100) AS product_code,
            e.title,
            e.content,
            e.source,
            'high'::VARCHAR(50) AS confidence,
            NULL::VARCHAR(36) AS created_by,
            e.created_at,
            e.chunk_text,
            (1 - (e.embedding <=> query_embedding))::DOUBLE PRECISION AS score
        FROM knowledge_embeddings e
        WHERE (1 - (e.embedding <=> query_embedding)) >= p_min_score
        ORDER BY e.embedding <=> query_embedding
        LIMIT p_limit;
    END IF;
END;
$$ LANGUAGE plpgsql;
