-- 为知识库文档添加向量化状态字段
ALTER TABLE knowledge_base_docs
    ADD COLUMN IF NOT EXISTS chunk_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS file_content TEXT;

-- 为 knowledge_embeddings 表添加来源标识，支持知识库和记忆库共用
ALTER TABLE knowledge_embeddings
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) NOT NULL DEFAULT 'MEMORY',
    ADD COLUMN IF NOT EXISTS source_doc_id VARCHAR(100);

-- 修改 card_id 允许 NULL，以便知识库向量记录不需要关联 knowledge_cards
ALTER TABLE knowledge_embeddings ALTER COLUMN card_id DROP NOT NULL;

-- 删除外键约束（知识库向量不需要关联 knowledge_cards）
-- PostgreSQL 自动生成的约束名通常为 knowledge_embeddings_card_id_fkey
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'knowledge_embeddings_card_id_fkey'
        AND table_name = 'knowledge_embeddings'
    ) THEN
        ALTER TABLE knowledge_embeddings DROP CONSTRAINT knowledge_embeddings_card_id_fkey;
    END IF;
END $$;

-- 创建索引加速知识库文档的向量检索
CREATE INDEX IF NOT EXISTS idx_embeddings_source_type ON knowledge_embeddings(source_type);
CREATE INDEX IF NOT EXISTS idx_embeddings_source_doc_id ON knowledge_embeddings(source_doc_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_docs_status ON knowledge_base_docs(embedding_status);

-- 删除之前错误的函数（如果已创建）
DROP FUNCTION IF EXISTS search_knowledge_embeddings_unified;
