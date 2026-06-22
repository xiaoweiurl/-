-- 市场营销对话历史表（独立于 smart_chat_history，避免类型冲突）
CREATE TABLE IF NOT EXISTS marketing_chat_history (
    id VARCHAR(100) PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    company VARCHAR(50),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sources TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_marketing_chat_user_company ON marketing_chat_history(user_id, company);
CREATE INDEX IF NOT EXISTS idx_marketing_chat_created ON marketing_chat_history(created_at);
