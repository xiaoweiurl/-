-- 知识库分类表（独立于记忆库）
CREATE TABLE IF NOT EXISTS knowledge_base_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parent_id UUID,
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识库文档表（独立于 knowledge_documents / memory 系统）
CREATE TABLE IF NOT EXISTS knowledge_base_docs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    category_id UUID REFERENCES knowledge_base_categories(id) ON DELETE SET NULL,
    title VARCHAR(500) NOT NULL,
    file_name VARCHAR(500),
    file_type VARCHAR(20),
    file_size BIGINT,
    file_path VARCHAR(1000),
    content TEXT,
    status VARCHAR(20) DEFAULT 'active',
    tags TEXT[],
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_kb_docs_user ON knowledge_base_docs(user_id);
CREATE INDEX IF NOT EXISTS idx_kb_docs_category ON knowledge_base_docs(category_id);
CREATE INDEX IF NOT EXISTS idx_kb_docs_created ON knowledge_base_docs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_kb_categories_user ON knowledge_base_categories(user_id);
