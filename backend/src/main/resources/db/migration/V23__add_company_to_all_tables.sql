-- 补充所有数据表的 company 字段，实现公司级数据隔离

-- 图片表添加 company
ALTER TABLE images ADD COLUMN IF NOT EXISTS company VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_images_company ON images(company);
UPDATE images SET company = '盈云' WHERE company IS NULL;

-- 文档表添加 company
ALTER TABLE documents ADD COLUMN IF NOT EXISTS company VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_documents_company ON documents(company);
UPDATE documents SET company = '盈云' WHERE company IS NULL;

-- 记忆库文档表添加 company
ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS company VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_company ON knowledge_documents(company);
UPDATE knowledge_documents SET company = '盈云' WHERE company IS NULL;

-- 向量嵌入表添加 company
ALTER TABLE knowledge_embeddings ADD COLUMN IF NOT EXISTS company VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_company ON knowledge_embeddings(company);
UPDATE knowledge_embeddings SET company = '盈云' WHERE company IS NULL;

-- 给已有知识库文档和卡片设置默认公司
UPDATE knowledge_base_docs SET company = '盈云' WHERE company IS NULL;
UPDATE knowledge_cards SET company = '盈云' WHERE company IS NULL;
UPDATE knowledge_domains SET company = '盈云' WHERE company IS NULL;
