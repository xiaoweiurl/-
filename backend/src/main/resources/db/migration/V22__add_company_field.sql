-- 添加 company 字段到 users 表，用于区分宝娜斯和盈云用户
ALTER TABLE users ADD COLUMN IF NOT EXISTS company VARCHAR(20);

-- 给已有用户设置默认公司
UPDATE users SET company = '盈云' WHERE company IS NULL;

-- 添加索引
CREATE INDEX IF NOT EXISTS idx_user_company ON users(company);

-- 知识库文档添加 company 字段
ALTER TABLE knowledge_base_docs ADD COLUMN IF NOT EXISTS company VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_knowledge_base_docs_company ON knowledge_base_docs(company);

-- 记忆库知识卡片添加 company 字段
ALTER TABLE knowledge_cards ADD COLUMN IF NOT EXISTS company VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_knowledge_cards_company ON knowledge_cards(company);

-- 记忆库知识域添加 company 字段
ALTER TABLE knowledge_domains ADD COLUMN IF NOT EXISTS company VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_knowledge_domains_company ON knowledge_domains(company);

-- 会话表添加 company 字段
ALTER TABLE user_sessions ADD COLUMN IF NOT EXISTS company VARCHAR(20);
