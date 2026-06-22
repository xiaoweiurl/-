-- 智能对话会话管理表
CREATE TABLE IF NOT EXISTS smart_chat_conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    company VARCHAR(50),
    title VARCHAR(200) NOT NULL DEFAULT '新对话',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_conv_user_company ON smart_chat_conversations(user_id, company);
CREATE INDEX IF NOT EXISTS idx_conv_updated ON smart_chat_conversations(updated_at DESC);

-- 为 smart_chat_history 添加 conversation_id 列
ALTER TABLE smart_chat_history ADD COLUMN IF NOT EXISTS conversation_id UUID;

-- 为现有数据设置 conversation_id（基于原有 session_id 关联）
-- 旧数据: session_id 是基于 userId 确定性生成的，把它们的 conversation_id 设为一个默认值
UPDATE smart_chat_history 
SET conversation_id = session_id 
WHERE conversation_id IS NULL;

-- 索引
CREATE INDEX IF NOT EXISTS idx_smart_chat_conversation ON smart_chat_history(conversation_id);
