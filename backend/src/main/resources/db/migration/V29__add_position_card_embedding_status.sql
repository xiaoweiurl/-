-- V29__add_position_card_embedding_status.sql
-- 岗位知识卡片添加向量化状态字段

ALTER TABLE position_knowledge_cards ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(20) DEFAULT 'PENDING';
COMMENT ON COLUMN position_knowledge_cards.embedding_status IS '向量化状态: PENDING/PROCESSING/COMPLETED/FAILED';

-- 将已有向量记录的卡片标记为 COMPLETED
UPDATE position_knowledge_cards SET embedding_status = 'COMPLETED'
WHERE id IN (
    SELECT DISTINCT source_doc_id FROM knowledge_embeddings
    WHERE source_type = 'POSITION_CARD' AND source_doc_id IS NOT NULL
);
