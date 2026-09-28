-- 对话回答的引用原文，以及答错后进入的待补充知识。
-- Flyway 自动迁移已关闭，上线前需要在目标库手动执行本脚本。

ALTER TABLE smart_chat_history ADD COLUMN IF NOT EXISTS sources_json TEXT;

CREATE TABLE IF NOT EXISTS knowledge_supplement (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    company VARCHAR(50),
    conversation_id VARCHAR(64),
    question TEXT NOT NULL,
    answer TEXT,
    sources_json TEXT,
    comment TEXT,
    verdict VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'recorded',
    created_at TIMESTAMP DEFAULT NOW()
);

ALTER TABLE knowledge_supplement ADD COLUMN IF NOT EXISTS sources_json TEXT;
ALTER TABLE knowledge_supplement ADD COLUMN IF NOT EXISTS comment TEXT;

CREATE INDEX IF NOT EXISTS idx_knowledge_supplement_status
    ON knowledge_supplement(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_knowledge_supplement_company
    ON knowledge_supplement(company, status, created_at DESC);
