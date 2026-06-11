-- ============================================================
-- 盈云企业智能中台 - 记忆库数据库迁移脚本
-- V16__create_memory_knowledge_base.sql
-- 
-- 四层架构: 数据库层 → 记忆库 → 大模型 → 用户层
-- 八大知识域: 产品库(中枢) + 研发/客户情报/竞争情报/供应链/品控认证/财务成本/治理决策
-- ============================================================

-- 1. 知识域表
CREATE TABLE IF NOT EXISTS knowledge_domains (
    id SERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    icon VARCHAR(50),
    color VARCHAR(20),
    sort_order INTEGER DEFAULT 0,
    card_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 初始化八大知识域
INSERT INTO knowledge_domains (code, name, description, icon, color, sort_order) VALUES
('product', '产品库(中枢)', '品号/品类/纱线/克重/工艺/成本/认证/迭代/场景适配。所有域围绕产品库，产品库是结构化数据库的核心表', 'Package', 'violet', 1),
('rd', '研发知识', '工艺参数、面料关联、试错记录。产品库=研发输出物', 'FlaskConical', 'blue', 2),
('customer', '客户情报', '品牌画像、成交偏好、潜在机会。客户买的是产品', 'Users', 'green', 3),
('competitive', '竞争情报', '新品拆解、技术路线、定价。对标对象是产品', 'Swords', 'red', 4),
('supply_chain', '供应链知识', '真实交期、质量波动、隐性成本。为产品供应服务', 'Truck', 'orange', 5),
('quality', '品控认证', '缺陷库、客户特殊标准、认证经验。检测对象是产品', 'ShieldCheck', 'emerald', 6),
('finance', '财务成本', '成本结构、报价模型、项目ROI。核算单元是产品', 'Calculator', 'yellow', 7),
('governance', '治理决策', '投资判断、定价逻辑、合作约束。立项对象是产品线', 'Gavel', 'slate', 8)
ON CONFLICT (code) DO NOTHING;

-- 2. 知识卡片表 - 每张卡片一件事、有判断、可追溯
CREATE TABLE IF NOT EXISTS knowledge_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    domain_code VARCHAR(50) NOT NULL REFERENCES knowledge_domains(code),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    tags TEXT[] DEFAULT '{}',
    product_code VARCHAR(50),                -- 关联产品编码(中枢关联)
    source VARCHAR(100),                     -- 来源：谁写的/从哪来的
    confidence VARCHAR(20) DEFAULT 'medium', -- 置信度：high/medium/low
    status VARCHAR(20) DEFAULT 'published',  -- 状态：draft/published/archived
    review_status VARCHAR(20) DEFAULT 'pending', -- 审核状态：pending/approved/rejected
    reviewer VARCHAR(100),                   -- 审核人
    reviewed_at TIMESTAMP,
    created_by VARCHAR(100) NOT NULL,        -- 创建人
    view_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 向量嵌入表 - 知识卡片的向量化表示(1024维)
CREATE TABLE IF NOT EXISTS knowledge_embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    card_id UUID NOT NULL REFERENCES knowledge_cards(id) ON DELETE CASCADE,
    embedding vector(1024) NOT NULL,
    embedding_model VARCHAR(100),            -- 使用的嵌入模型
    chunk_text TEXT,                         -- 切片原文
    chunk_index INTEGER DEFAULT 0,           -- 切片序号
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. AI对话历史表 - 记录问答过程，支持上下文续问
CREATE TABLE IF NOT EXISTS knowledge_chat_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,               -- user/assistant/system
    content TEXT NOT NULL,
    sources JSONB,                           -- 引用的知识卡片来源
    model VARCHAR(50),                       -- 使用的模型
    tokens_used INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ========== 索引 ==========

-- 向量索引(HNSW，适合大规模语义检索)
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_hnsw
ON knowledge_embeddings USING hnsw (embedding vector_cosine_ops);

-- 卡片查询索引
CREATE INDEX IF NOT EXISTS idx_knowledge_cards_domain ON knowledge_cards(domain_code);
CREATE INDEX IF NOT EXISTS idx_knowledge_cards_product ON knowledge_cards(product_code);
CREATE INDEX IF NOT EXISTS idx_knowledge_cards_status ON knowledge_cards(status);
CREATE INDEX IF NOT EXISTS idx_knowledge_cards_tags ON knowledge_cards USING gin(tags);
CREATE INDEX IF NOT EXISTS idx_knowledge_cards_created ON knowledge_cards(created_at DESC);

-- 对话历史索引
CREATE INDEX IF NOT EXISTS idx_chat_history_session ON knowledge_chat_history(session_id, created_at);
