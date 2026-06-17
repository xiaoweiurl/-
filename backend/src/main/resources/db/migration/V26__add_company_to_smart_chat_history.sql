-- 添加 company 字段到 smart_chat_history 表
ALTER TABLE smart_chat_history ADD COLUMN IF NOT EXISTS company VARCHAR(50) DEFAULT '盈云';

-- 回填历史数据的 company 字段
UPDATE smart_chat_history SET company = u.company
FROM users u
WHERE smart_chat_history.user_id::text = u.id::text
  AND smart_chat_history.company IS NULL;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_smart_chat_company ON smart_chat_history(company);
