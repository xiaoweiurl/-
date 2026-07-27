-- ============================================================
-- 盈云产品智能中台 - PostgreSQL 完整建表脚本
-- 包含所有表结构、索引、约束
-- 所有 id 类型与现有数据库保持一致
-- ============================================================

-- 启用扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 1. 用户与权限相关表
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255),
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500),
    role VARCHAR(20) NOT NULL DEFAULT 'user',
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    last_login_at TIMESTAMP,
    login_count INTEGER DEFAULT 0,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户会话表（与 AuthServiceImpl 代码一致，时间字段用 BIGINT 毫秒时间戳）
CREATE TABLE IF NOT EXISTS user_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    avatar_url VARCHAR(500),
    role VARCHAR(20),
    membership VARCHAR(20),
    company VARCHAR(50),
    remember_me BOOLEAN DEFAULT false,
    created_at BIGINT NOT NULL,
    last_access_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL
);

-- 登录尝试记录表
CREATE TABLE IF NOT EXISTS login_attempts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(36),
    username VARCHAR(100) NOT NULL,
    ip_address VARCHAR(50) NOT NULL,
    user_agent VARCHAR(500),
    success BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason VARCHAR(200),
    attempt_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户设置表
CREATE TABLE IF NOT EXISTS user_settings (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    user_id VARCHAR(36) UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    theme VARCHAR(20) DEFAULT 'auto',
    language VARCHAR(10) DEFAULT 'zh-CN',
    notification_enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 用户索引
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_company ON users(company);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_user_sessions_expires_at ON user_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_company ON user_sessions(company);
CREATE INDEX IF NOT EXISTS idx_login_attempts_username ON login_attempts(username);
CREATE INDEX IF NOT EXISTS idx_login_attempts_ip ON login_attempts(ip_address);

-- ============================================================
-- 2. 相册/分类与图片相关表
-- ============================================================

-- 相册（分类）表
CREATE TABLE IF NOT EXISTS albums (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    cover_image_id VARCHAR(36),
    user_id VARCHAR(36) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'user',
    matching_mode VARCHAR(20) DEFAULT 'include',
    color VARCHAR(20),
    sort_order INTEGER DEFAULT 0,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 图片表
CREATE TABLE IF NOT EXISTS images (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    name VARCHAR(500) NOT NULL,
    original_name VARCHAR(500),
    file_path VARCHAR(1000) NOT NULL,
    file_key VARCHAR(500),
    url VARCHAR(1000),
    thumbnail_url VARCHAR(1000),
    file_size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    format VARCHAR(50),
    width INTEGER,
    height INTEGER,
    taken_at TIMESTAMP,
    uploader_id VARCHAR(36),
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    source VARCHAR(20) DEFAULT 'upload',
    deleted BOOLEAN DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 相册图片关联表
CREATE TABLE IF NOT EXISTS album_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    album_id VARCHAR(36) NOT NULL REFERENCES albums(id) ON DELETE CASCADE,
    image_id VARCHAR(36) NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(album_id, image_id)
);

-- 标签表
CREATE TABLE IF NOT EXISTS tags (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    color VARCHAR(20),
    category VARCHAR(50),
    count INTEGER DEFAULT 0,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(name, user_id)
);

-- 图片标签关联表
CREATE TABLE IF NOT EXISTS image_tags (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    image_id VARCHAR(36) NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(image_id, tag_id)
);

-- 收藏表
CREATE TABLE IF NOT EXISTS favorites (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    image_id VARCHAR(36) NOT NULL REFERENCES images(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, image_id)
);

-- 相册图片索引
CREATE INDEX IF NOT EXISTS idx_albums_user_id ON albums(user_id);
CREATE INDEX IF NOT EXISTS idx_albums_company ON albums(company);
CREATE INDEX IF NOT EXISTS idx_images_user_id ON images(user_id);
CREATE INDEX IF NOT EXISTS idx_images_company ON images(company);
CREATE INDEX IF NOT EXISTS idx_images_deleted ON images(deleted);
CREATE INDEX IF NOT EXISTS idx_images_source ON images(source);
CREATE INDEX IF NOT EXISTS idx_images_created_at ON images(created_at);
CREATE INDEX IF NOT EXISTS idx_images_file_key ON images(file_key);
CREATE INDEX IF NOT EXISTS idx_album_images_album_id ON album_images(album_id);
CREATE INDEX IF NOT EXISTS idx_album_images_image_id ON album_images(image_id);
CREATE INDEX IF NOT EXISTS idx_tags_user_id ON tags(user_id);
CREATE INDEX IF NOT EXISTS idx_image_tags_image_id ON image_tags(image_id);
CREATE INDEX IF NOT EXISTS idx_image_tags_tag_id ON image_tags(tag_id);
CREATE INDEX IF NOT EXISTS idx_favorites_user_id ON favorites(user_id);
CREATE INDEX IF NOT EXISTS idx_favorites_image_id ON favorites(image_id);

-- ============================================================
-- 3. 供应链/工厂管理相关表
-- ============================================================

-- 产品表
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100),
    category VARCHAR(50),
    description TEXT,
    base_price DECIMAL(10,2),
    cost_price DECIMAL(10,2),
    image_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'active',
    is_default BOOLEAN DEFAULT FALSE,
    matching_mode VARCHAR(20) DEFAULT 'include',
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 产品图片表
CREATE TABLE IF NOT EXISTS product_images (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id VARCHAR(36) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(1000) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    is_main BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 原辅料表
CREATE TABLE IF NOT EXISTS materials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100),
    type VARCHAR(20) NOT NULL,
    category VARCHAR(50),
    unit VARCHAR(20),
    spec VARCHAR(200),
    composition VARCHAR(200),
    manufacturer VARCHAR(200),
    supplier VARCHAR(200),
    purchase_price DECIMAL(10,2),
    stock_quantity DECIMAL(10,2) DEFAULT 0,
    description TEXT,
    image_url VARCHAR(500),
    status VARCHAR(20) DEFAULT 'active',
    is_default BOOLEAN DEFAULT FALSE,
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 产品物料明细表
CREATE TABLE IF NOT EXISTS product_materials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id VARCHAR(36) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    material_id UUID NOT NULL REFERENCES materials(id),
    quantity DECIMAL(10,3) NOT NULL,
    unit VARCHAR(20),
    loss_rate DECIMAL(5,2) DEFAULT 0,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(product_id, material_id)
);

-- 生产计划表
CREATE TABLE IF NOT EXISTS production_plans (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    product_id VARCHAR(36) NOT NULL REFERENCES products(id),
    plan_number VARCHAR(100),
    plan_date DATE NOT NULL,
    planned_quantity INTEGER NOT NULL,
    actual_quantity INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'pending',
    priority VARCHAR(20) DEFAULT 'normal',
    notes TEXT,
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 原材料入库记录表
CREATE TABLE IF NOT EXISTS raw_material_inbound (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    material_id UUID NOT NULL REFERENCES materials(id),
    inbound_number VARCHAR(100),
    inbound_date DATE NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit VARCHAR(20),
    purchase_price DECIMAL(10,2) NOT NULL,
    supplier VARCHAR(200),
    status VARCHAR(20) DEFAULT 'pending',
    notes TEXT,
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 原材料采购表
CREATE TABLE IF NOT EXISTS raw_material_purchase (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    material_id UUID REFERENCES materials(id),
    purchase_number VARCHAR(100),
    purchase_date DATE NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    supplier VARCHAR(200),
    status VARCHAR(20) DEFAULT 'pending',
    notes TEXT,
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 原材料仓库表
CREATE TABLE IF NOT EXISTS raw_material_warehouse (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    material_id UUID REFERENCES materials(id),
    warehouse_number VARCHAR(100),
    warehouse_name VARCHAR(200) NOT NULL,
    location VARCHAR(200),
    stock_quantity DECIMAL(10,2) DEFAULT 0,
    unit VARCHAR(20),
    status VARCHAR(20) DEFAULT 'active',
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 产品报价表
CREATE TABLE IF NOT EXISTS product_quotation (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    product_id VARCHAR(36) REFERENCES products(id),
    quotation_number VARCHAR(100),
    quotation_date DATE NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    cost_price DECIMAL(10,2),
    profit_margin DECIMAL(5,2),
    status VARCHAR(20) DEFAULT 'pending',
    customer VARCHAR(200),
    notes TEXT,
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 辅料采购表
CREATE TABLE IF NOT EXISTS accessory_purchase (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    material_id UUID REFERENCES materials(id),
    purchase_number VARCHAR(100),
    purchase_date DATE NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    total_price DECIMAL(10,2) NOT NULL,
    supplier VARCHAR(200),
    status VARCHAR(20) DEFAULT 'pending',
    notes TEXT,
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 加工成本表
CREATE TABLE IF NOT EXISTS manufacturing_costs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id VARCHAR(36) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    cost_type VARCHAR(50) NOT NULL,
    process_name VARCHAR(200) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    unit VARCHAR(20),
    sort_order INTEGER DEFAULT 0,
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 其他费用表
CREATE TABLE IF NOT EXISTS other_costs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id VARCHAR(36) NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    cost_name VARCHAR(200) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    description TEXT,
    sort_order INTEGER DEFAULT 0,
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 供应链索引
CREATE INDEX IF NOT EXISTS idx_products_user_id ON products(user_id);
CREATE INDEX IF NOT EXISTS idx_products_company ON products(company);
CREATE INDEX IF NOT EXISTS idx_products_code ON products(code);
CREATE INDEX IF NOT EXISTS idx_product_images_product_id ON product_images(product_id);
CREATE INDEX IF NOT EXISTS idx_materials_user_id ON materials(user_id);
CREATE INDEX IF NOT EXISTS idx_materials_company ON materials(company);
CREATE INDEX IF NOT EXISTS idx_materials_code ON materials(code);
CREATE INDEX IF NOT EXISTS idx_materials_type ON materials(type);
CREATE INDEX IF NOT EXISTS idx_product_materials_product_id ON product_materials(product_id);
CREATE INDEX IF NOT EXISTS idx_product_materials_material_id ON product_materials(material_id);
CREATE INDEX IF NOT EXISTS idx_production_plans_product_id ON production_plans(product_id);
CREATE INDEX IF NOT EXISTS idx_production_plans_company ON production_plans(company);
CREATE INDEX IF NOT EXISTS idx_raw_material_inbound_material_id ON raw_material_inbound(material_id);
CREATE INDEX IF NOT EXISTS idx_raw_material_inbound_company ON raw_material_inbound(company);
CREATE INDEX IF NOT EXISTS idx_manufacturing_costs_product_id ON manufacturing_costs(product_id);
CREATE INDEX IF NOT EXISTS idx_manufacturing_costs_company ON manufacturing_costs(company);
CREATE INDEX IF NOT EXISTS idx_other_costs_product_id ON other_costs(product_id);

-- ============================================================
-- 4. 知识库相关表
-- ============================================================

-- 知识库分类表
CREATE TABLE IF NOT EXISTS knowledge_base_categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parent_id UUID,
    sort_order INTEGER DEFAULT 0,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识库文档表
CREATE TABLE IF NOT EXISTS knowledge_base_docs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(500) NOT NULL,
    content TEXT,
    file_name VARCHAR(500),
    file_url VARCHAR(1000),
    file_size BIGINT,
    file_type VARCHAR(50),
    category_id UUID REFERENCES knowledge_base_categories(id),
    chunk_count INTEGER DEFAULT 0,
    embedding_status VARCHAR(20) DEFAULT 'PENDING',
    file_content TEXT,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识库索引
CREATE INDEX IF NOT EXISTS idx_kb_categories_company ON knowledge_base_categories(company);
CREATE INDEX IF NOT EXISTS idx_kb_categories_parent_id ON knowledge_base_categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_kb_docs_company ON knowledge_base_docs(company);
CREATE INDEX IF NOT EXISTS idx_kb_docs_category_id ON knowledge_base_docs(category_id);
CREATE INDEX IF NOT EXISTS idx_kb_docs_embedding_status ON knowledge_base_docs(embedding_status);
CREATE INDEX IF NOT EXISTS idx_kb_docs_created_at ON knowledge_base_docs(created_at);

-- ============================================================
-- 5. 向量嵌入相关表（使用 pgvector）
-- ============================================================

-- 知识嵌入表（向量存储）
CREATE TABLE IF NOT EXISTS knowledge_embeddings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    card_id UUID,
    source_type VARCHAR(20) NOT NULL,
    source_doc_id VARCHAR(100),
    chunk_index INTEGER,
    chunk_text TEXT,
    embedding vector(1024) NOT NULL,
    embedding_model VARCHAR(100),
    company VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 向量索引
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_source_type ON knowledge_embeddings(source_type);
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_source_doc_id ON knowledge_embeddings(source_doc_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_company ON knowledge_embeddings(company);

-- 注意: 如果安装了 pgvector，可以使用以下索引加速向量搜索
-- 向量索引（HNSW，余弦相似度）
CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_vector ON knowledge_embeddings
USING hnsw (embedding vector_cosine_ops);

-- ============================================================
-- 6. AI 对话相关表
-- ============================================================

-- AI 对话历史表
CREATE TABLE IF NOT EXISTS smart_chat_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL,
    session_id VARCHAR(100),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    conversation_id UUID,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- AI 对话会话表
CREATE TABLE IF NOT EXISTS smart_chat_conversations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(500),
    model VARCHAR(50) DEFAULT 'default',
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- AI 对话索引
CREATE INDEX IF NOT EXISTS idx_smart_chat_history_user_id ON smart_chat_history(user_id);
CREATE INDEX IF NOT EXISTS idx_smart_chat_history_session_id ON smart_chat_history(session_id);
CREATE INDEX IF NOT EXISTS idx_smart_chat_history_conversation_id ON smart_chat_history(conversation_id);
CREATE INDEX IF NOT EXISTS idx_smart_chat_conversations_user_id ON smart_chat_conversations(user_id);

-- ============================================================
-- 7. 文档管理相关表
-- ============================================================

-- 文档表
CREATE TABLE IF NOT EXISTS documents (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    name VARCHAR(500) NOT NULL,
    original_name VARCHAR(500),
    stored_name VARCHAR(500),
    file_path VARCHAR(1000) NOT NULL,
    url VARCHAR(1000),
    size BIGINT,
    content_type VARCHAR(100),
    extension VARCHAR(20),
    category VARCHAR(20),
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档索引
CREATE INDEX IF NOT EXISTS idx_documents_category ON documents(category);
CREATE INDEX IF NOT EXISTS idx_documents_user_id ON documents(user_id);
CREATE INDEX IF NOT EXISTS idx_documents_company ON documents(company);
CREATE INDEX IF NOT EXISTS idx_documents_deleted ON documents(deleted);

-- ============================================================
-- 8. 动态数据模型相关表
-- ============================================================

-- 数据模型表
CREATE TABLE IF NOT EXISTS data_models (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    table_name VARCHAR(100) NOT NULL,
    icon VARCHAR(50),
    color VARCHAR(20),
    sort_order INTEGER DEFAULT 0,
    is_system BOOLEAN DEFAULT FALSE,
    company VARCHAR(50),
    created_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 数据模型字段表
CREATE TABLE IF NOT EXISTS data_model_fields (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    model_id UUID NOT NULL REFERENCES data_models(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL,
    label VARCHAR(200),
    field_type VARCHAR(50) NOT NULL DEFAULT 'text',
    default_value TEXT,
    placeholder VARCHAR(200),
    required BOOLEAN DEFAULT FALSE,
    unique_field BOOLEAN DEFAULT FALSE,
    sort_order INTEGER DEFAULT 0,
    show_in_list BOOLEAN DEFAULT TRUE,
    show_in_form BOOLEAN DEFAULT TRUE,
    width INTEGER,
    options TEXT,
    validation TEXT,
    description TEXT,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 数据模型记录表（主表，实际数据存储在动态创建的 images_xxx 表中）
CREATE TABLE IF NOT EXISTS data_model_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    model_id UUID NOT NULL REFERENCES data_models(id) ON DELETE CASCADE,
    record_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 动态模型索引
CREATE INDEX IF NOT EXISTS idx_data_models_code ON data_models(code);
CREATE INDEX IF NOT EXISTS idx_data_models_company ON data_models(company);
CREATE INDEX IF NOT EXISTS idx_data_model_fields_model_id ON data_model_fields(model_id);
CREATE INDEX IF NOT EXISTS idx_data_model_records_model_id ON data_model_records(model_id);

-- ============================================================
-- 9. 通知与消息相关表
-- ============================================================

-- 通知表
CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    link VARCHAR(500),
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 系统消息表
CREATE TABLE IF NOT EXISTS system_messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    priority INTEGER DEFAULT 0,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 通知索引
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_is_read ON notifications(is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_type ON notifications(type);

-- ============================================================
-- 10. 岗位知识卡片相关表
-- ============================================================

-- 岗位知识卡片表
CREATE TABLE IF NOT EXISTS position_knowledge_cards (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(100),
    difficulty VARCHAR(20) DEFAULT 'medium',
    source VARCHAR(50),
    tags TEXT,
    user_id VARCHAR(36) NOT NULL,
    company VARCHAR(50),
    embedding_status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 岗位知识卡片索引
CREATE INDEX IF NOT EXISTS idx_position_knowledge_cards_category ON position_knowledge_cards(category);
CREATE INDEX IF NOT EXISTS idx_position_knowledge_cards_company ON position_knowledge_cards(company);
CREATE INDEX IF NOT EXISTS idx_position_knowledge_cards_embedding_status ON position_knowledge_cards(embedding_status);

-- ============================================================
-- 11. 审计与日志相关表
-- ============================================================

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(36),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(36),
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 审计日志索引
CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_type ON audit_logs(entity_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);

-- ============================================================
-- 12. 运维监控相关表
-- ============================================================

-- API 指标监控表
CREATE TABLE IF NOT EXISTS api_metrics (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    endpoint VARCHAR(200) NOT NULL,
    method VARCHAR(10) NOT NULL,
    response_time INTEGER NOT NULL,
    status_code INTEGER NOT NULL,
    user_id VARCHAR(36),
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 系统错误表
CREATE TABLE IF NOT EXISTS system_errors (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    error_type VARCHAR(50) NOT NULL,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    endpoint VARCHAR(200),
    user_id VARCHAR(36),
    ip_address VARCHAR(50),
    resolved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 备份记录表
CREATE TABLE IF NOT EXISTS backup_records (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    backup_type VARCHAR(50) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT,
    status VARCHAR(20) DEFAULT 'pending',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 运维索引
CREATE INDEX IF NOT EXISTS idx_api_metrics_endpoint ON api_metrics(endpoint);
CREATE INDEX IF NOT EXISTS idx_api_metrics_created_at ON api_metrics(created_at);
CREATE INDEX IF NOT EXISTS idx_system_errors_error_type ON system_errors(error_type);
CREATE INDEX IF NOT EXISTS idx_system_errors_resolved ON system_errors(resolved);

-- ============================================================
-- 13. 记忆库遗留表（保留数据兼容）
-- ============================================================

-- 知识域表
CREATE TABLE IF NOT EXISTS knowledge_domains (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE,
    description TEXT,
    icon VARCHAR(100),
    user_id VARCHAR(36),
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识卡片表
CREATE TABLE IF NOT EXISTS knowledge_cards (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    domain_id INTEGER REFERENCES knowledge_domains(id),
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    summary TEXT,
    tags TEXT,
    source VARCHAR(50),
    user_id VARCHAR(36),
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识文档表
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    domain_id INTEGER REFERENCES knowledge_domains(id),
    file_name VARCHAR(500),
    file_url VARCHAR(1000),
    file_size BIGINT,
    file_type VARCHAR(50),
    chunk_count INTEGER DEFAULT 0,
    user_id VARCHAR(36),
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 知识对话历史表
CREATE TABLE IF NOT EXISTS knowledge_chat_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    domain_id INTEGER REFERENCES knowledge_domains(id),
    user_id VARCHAR(36),
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sources TEXT,
    company VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 记忆库索引
CREATE INDEX IF NOT EXISTS idx_knowledge_domains_code ON knowledge_domains(code);
CREATE INDEX IF NOT EXISTS idx_knowledge_domains_company ON knowledge_domains(company);
CREATE INDEX IF NOT EXISTS idx_knowledge_cards_domain_id ON knowledge_cards(domain_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_documents_domain_id ON knowledge_documents(domain_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_chat_history_domain_id ON knowledge_chat_history(domain_id);

-- ============================================================
-- 14. 其他辅助表
-- ============================================================

-- 健康检查表
CREATE TABLE IF NOT EXISTS health_check (
    id INTEGER PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    status VARCHAR(20) NOT NULL DEFAULT 'healthy',
    message TEXT,
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 审计表（旧版，保留兼容）
CREATE TABLE IF NOT EXISTS audit_log (
    id VARCHAR(36) PRIMARY KEY DEFAULT uuid_generate_v4()::TEXT,
    user_id VARCHAR(36),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(36),
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 15. 默认数据
-- ============================================================

-- 默认管理员账号（密码: Admin@123，BCrypt加密）
INSERT INTO users (id, username, email, password_hash, role, status, company) 
VALUES ('admin-default-id', 'admin', 'admin@yingyun.com', 
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
    'admin', 'active', '盈云') 
ON CONFLICT (id) DO NOTHING;

-- 默认普通用户账号（密码: User@123）
INSERT INTO users (id, username, email, password_hash, role, status, company) 
VALUES ('user-default-id', 'user', 'user@yingyun.com', 
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 
    'user', 'active', '盈云') 
ON CONFLICT (id) DO NOTHING;

-- 默认知识库分类
INSERT INTO knowledge_base_categories (id, name, description, company) 
VALUES (uuid_generate_v4(), '通用知识', '默认知识库分类', '盈云') 
ON CONFLICT DO NOTHING;

-- ============================================================
-- 完成建表
-- ============================================================
