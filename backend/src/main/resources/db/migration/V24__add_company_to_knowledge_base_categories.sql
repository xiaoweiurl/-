-- 知识库分类表添加 company 字段，实现公司级数据隔离
ALTER TABLE knowledge_base_categories ADD COLUMN IF NOT EXISTS company VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_kb_categories_company ON knowledge_base_categories(company);
UPDATE knowledge_base_categories SET company = '盈云' WHERE company IS NULL;
