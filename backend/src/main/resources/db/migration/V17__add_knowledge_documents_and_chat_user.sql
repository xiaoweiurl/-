-- ============================================================
-- V17: 添加文档管理表 + 对话历史用户隔离
-- ============================================================

-- 1. 文档管理表 - 记录用户上传的文档及其处理状态
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,            -- 所属用户(数据隔离)
    file_name VARCHAR(500) NOT NULL,          -- 原始文件名
    file_type VARCHAR(20) NOT NULL,           -- 文件类型(pdf/doc/docx/xls/xlsx/txt)
    file_size BIGINT,                         -- 文件大小(字节)
    domain_code VARCHAR(50),                  -- 所属知识域
    status VARCHAR(20) DEFAULT 'processing',  -- 处理状态: processing/completed/failed/empty
    chunk_count INTEGER DEFAULT 0,            -- 成功切片数量
    error_message TEXT,                       -- 错误信息
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_documents_user ON knowledge_documents(user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_status ON knowledge_documents(status);

-- 2. 对话历史添加user_id字段(用户隔离)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'knowledge_chat_history' AND column_name = 'user_id'
    ) THEN
        ALTER TABLE knowledge_chat_history ADD COLUMN user_id VARCHAR(100);
        CREATE INDEX IF NOT EXISTS idx_chat_history_user ON knowledge_chat_history(user_id);
    END IF;
END $$;
