-- 智能对话历史表 (双库检索专用)
CREATE TABLE IF NOT EXISTS smart_chat_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('user', 'assistant', 'system')),
    content TEXT NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_smart_chat_session ON smart_chat_history(session_id);
CREATE INDEX IF NOT EXISTS idx_smart_chat_user ON smart_chat_history(user_id);
CREATE INDEX IF NOT EXISTS idx_smart_chat_session_user ON smart_chat_history(session_id, user_id);
