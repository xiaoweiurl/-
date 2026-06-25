-- V34: 为智能对话表添加mode字段，区分设计师/工厂模式

-- 对话表添加mode字段
ALTER TABLE smart_chat_conversations ADD COLUMN IF NOT EXISTS mode VARCHAR(20) DEFAULT 'designer';

-- 对话历史表添加mode字段
ALTER TABLE smart_chat_history ADD COLUMN IF NOT EXISTS mode VARCHAR(20) DEFAULT 'designer';

-- 为mode字段创建索引，加速按模式筛选
CREATE INDEX IF NOT EXISTS idx_conversations_mode ON smart_chat_conversations(mode);
CREATE INDEX IF NOT EXISTS idx_chat_history_mode ON smart_chat_history(mode);
