-- V54: 系统统一宝娜斯，数据隔离从 company 改为 user_id
-- 1. knowledge_embeddings 增加 user_id 列（向量检索按用户隔离，NULL 兼容旧数据）
ALTER TABLE knowledge_embeddings ADD COLUMN IF NOT EXISTS user_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_user_id ON knowledge_embeddings(user_id);

-- 2. knowledge_base_docs 补齐 user_id 列（部分环境表结构为旧版）
ALTER TABLE knowledge_base_docs ADD COLUMN IF NOT EXISTS user_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_docs_user_id ON knowledge_base_docs(user_id);

-- 3. knowledge_embeddings 补齐 V20 的 source_type / source_doc_id（部分环境缺失）
ALTER TABLE knowledge_embeddings ADD COLUMN IF NOT EXISTS source_type VARCHAR(32) DEFAULT 'MEMORY';
ALTER TABLE knowledge_embeddings ADD COLUMN IF NOT EXISTS source_doc_id VARCHAR(64);
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_source ON knowledge_embeddings(source_type, source_doc_id);

-- 4. knowledge_base_categories 补齐 user_id 列
ALTER TABLE knowledge_base_categories ADD COLUMN IF NOT EXISTS user_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_categories_user_id ON knowledge_base_categories(user_id);

-- 5. 历史数据 company 统一为宝娜斯（不再区分公司）
UPDATE users SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE images SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE albums SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE knowledge_base_docs SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE knowledge_base_categories SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE knowledge_embeddings SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE knowledge_cards SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE knowledge_domains SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE knowledge_documents SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE position_knowledge_cards SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE smart_chat_history SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE smart_chat_conversations SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE marketing_chat_history SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
UPDATE ai_call_log SET company = '宝娜斯' WHERE company IS DISTINCT FROM '宝娜斯';
