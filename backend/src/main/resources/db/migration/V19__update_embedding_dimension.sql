-- ============================================================
-- V19__update_embedding_dimension.sql
-- 将 embedding 维度从 1024 改为 1536，适配 MiniMax embo-01 模型
-- 当前无数据，可直接修改列类型
-- ============================================================

-- 修改向量嵌入表维度
ALTER TABLE knowledge_embeddings ALTER COLUMN embedding TYPE vector(1536);

-- 修改向量检索函数中的维度限制
CREATE OR REPLACE FUNCTION search_knowledge_similar(
    query_embedding vector(1536),
    domain_filter VARCHAR DEFAULT NULL,
    similarity_threshold FLOAT DEFAULT 0.3,
    max_results INT DEFAULT 10,
    user_filter VARCHAR DEFAULT NULL
) RETURNS TABLE(
    card_id UUID,
    similarity FLOAT,
    domain_code VARCHAR,
    title VARCHAR,
    content TEXT,
    tags TEXT[],
    product_code VARCHAR,
    source VARCHAR,
    confidence VARCHAR,
    created_by VARCHAR,
    created_at TIMESTAMP
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        c.id AS card_id,
        (1 - (e.embedding <=> query_embedding))::FLOAT AS similarity,
        c.domain_code,
        c.title,
        c.content,
        c.tags,
        c.product_code,
        c.source,
        c.confidence,
        c.created_by,
        c.created_at
    FROM knowledge_embeddings e
    JOIN knowledge_cards c ON e.card_id = c.id
    WHERE
        (1 - (e.embedding <=> query_embedding)) > similarity_threshold
        AND c.status = 'published'
        AND (domain_filter IS NULL OR c.domain_code = domain_filter)
        AND (user_filter IS NULL OR c.user_id = user_filter)
    ORDER BY e.embedding <=> query_embedding
    LIMIT max_results;
END;
$$ LANGUAGE plpgsql;
