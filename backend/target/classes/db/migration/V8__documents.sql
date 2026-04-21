-- ============================================
-- 文档管理表
-- ============================================

-- 创建文档表
CREATE TABLE IF NOT EXISTS documents (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(500) NOT NULL,
    original_name VARCHAR(500),
    stored_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    url VARCHAR(1000),
    size BIGINT DEFAULT 0,
    content_type VARCHAR(100),
    extension VARCHAR(20),
    category VARCHAR(20) NOT NULL DEFAULT 'other',
    user_id VARCHAR(36) NOT NULL,
    deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 添加外键约束
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_documents_user'
        AND table_name = 'documents'
    ) THEN
        ALTER TABLE documents
        ADD CONSTRAINT fk_documents_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_documents_category ON documents(category);
CREATE INDEX IF NOT EXISTS idx_documents_user_id ON documents(user_id);
CREATE INDEX IF NOT EXISTS idx_documents_deleted ON documents(deleted);
CREATE INDEX IF NOT EXISTS idx_documents_created_at ON documents(created_at DESC);

-- 完成
SELECT '文档表创建完成！' AS status;
