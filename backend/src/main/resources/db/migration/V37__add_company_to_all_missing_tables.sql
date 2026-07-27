-- V37: 补全所有缺失表 + 给所有已有表添加 company 列
-- 策略：先 CREATE TABLE IF NOT EXISTS 确保所有表存在，再 ALTER TABLE ADD company

-- =====================================================
-- 第一步：创建所有可能缺失的表（都包含 company 列）
-- =====================================================

-- 用户相关
CREATE TABLE IF NOT EXISTS user_sessions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    session_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS login_attempts (
    id SERIAL PRIMARY KEY,
    username VARCHAR(100),
    ip_address VARCHAR(50),
    success BOOLEAN DEFAULT FALSE,
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS user_settings (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    theme VARCHAR(20) DEFAULT 'light',
    language VARCHAR(10) DEFAULT 'zh',
    layout VARCHAR(20) DEFAULT 'grid',
    page_size INTEGER DEFAULT 40,
    auto_play BOOLEAN DEFAULT TRUE,
    show_deleted BOOLEAN DEFAULT FALSE,
    notification_enabled BOOLEAN DEFAULT TRUE,
    email_notification BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

-- 相册与图片
CREATE TABLE IF NOT EXISTS albums (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    cover_image_url TEXT,
    user_id VARCHAR(36) NOT NULL,
    is_public BOOLEAN DEFAULT FALSE,
    matching_mode VARCHAR(20) DEFAULT 'auto',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS images (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    title VARCHAR(200),
    url TEXT NOT NULL,
    thumbnail_url TEXT,
    original_url TEXT,
    description TEXT,
    album_id VARCHAR(36),
    user_id VARCHAR(36) NOT NULL,
    file_size BIGINT DEFAULT 0,
    width INTEGER DEFAULT 0,
    height INTEGER DEFAULT 0,
    format VARCHAR(20),
    mime_type VARCHAR(50),
    source VARCHAR(20) DEFAULT 'upload',
    tags TEXT,
    is_favorite BOOLEAN DEFAULT FALSE,
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS favorites (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    image_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS tags (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS image_tags (
    id SERIAL PRIMARY KEY,
    image_id VARCHAR(36) NOT NULL,
    tag_id INTEGER NOT NULL,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS documents (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    original_name VARCHAR(200),
    stored_name VARCHAR(200),
    file_path VARCHAR(500),
    url TEXT,
    size BIGINT DEFAULT 0,
    content_type VARCHAR(100),
    extension VARCHAR(20),
    category VARCHAR(20) DEFAULT 'other',
    user_id VARCHAR(36) NOT NULL,
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS notifications (
    id UUID NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(200),
    message TEXT,
    data TEXT,
    read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

-- 供应链
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(200),
    code VARCHAR(100),
    category VARCHAR(100),
    description TEXT,
    main_image_url TEXT,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS product_images (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL,
    image_url TEXT NOT NULL,
    image_type VARCHAR(20) DEFAULT 'main',
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS materials (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(200),
    code VARCHAR(100),
    category VARCHAR(100),
    unit VARCHAR(20),
    specification TEXT,
    color VARCHAR(50),
    supplier VARCHAR(200),
    unit_price DECIMAL(10,2) DEFAULT 0,
    stock_quantity DECIMAL(10,2) DEFAULT 0,
    min_stock_quantity DECIMAL(10,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS product_materials (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL,
    material_id VARCHAR(36) NOT NULL,
    quantity DECIMAL(10,2) DEFAULT 0,
    unit VARCHAR(20),
    wastage_rate DECIMAL(5,2) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS production_plan (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL,
    plan_name VARCHAR(200),
    plan_date DATE,
    quantity INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS raw_material_purchase (
    id INTEGER NOT NULL PRIMARY KEY,
    material_code VARCHAR(100),
    material_name VARCHAR(200),
    supplier VARCHAR(200),
    unit VARCHAR(20),
    purchase_price DECIMAL(10,2),
    purchase_quantity DECIMAL(10,2),
    purchase_date DATE,
    delivery_date DATE,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS raw_material_warehouse (
    id INTEGER NOT NULL PRIMARY KEY,
    material_code VARCHAR(100),
    material_name VARCHAR(200),
    batch_number VARCHAR(100),
    warehouse_location VARCHAR(200),
    quantity DECIMAL(10,2),
    unit VARCHAR(20),
    inbound_date DATE,
    status VARCHAR(20) DEFAULT 'normal',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS product_quotation (
    id INTEGER NOT NULL PRIMARY KEY,
    product_code VARCHAR(100),
    production_code VARCHAR(100),
    document_no VARCHAR(100),
    period VARCHAR(50),
    customer VARCHAR(200),
    salesperson VARCHAR(100),
    product_category VARCHAR(100),
    front_quotation_no VARCHAR(100),
    approval_status VARCHAR(50),
    sales_type VARCHAR(50),
    raw_material_name1 VARCHAR(200),
    material_usage1 DECIMAL(10,2),
    material_unit_price1 DECIMAL(10,2),
    raw_material_name2 VARCHAR(200),
    material_usage2 DECIMAL(10,2),
    material_unit_price2 DECIMAL(10,2),
    raw_material_name3 VARCHAR(200),
    material_usage3 DECIMAL(10,2),
    material_unit_price3 DECIMAL(10,2),
    raw_material_name4 VARCHAR(200),
    material_usage4 DECIMAL(10,2),
    material_unit_price4 DECIMAL(10,2),
    raw_material_name5 VARCHAR(200),
    material_usage5 DECIMAL(10,2),
    material_unit_price5 DECIMAL(10,2),
    raw_material_name6 VARCHAR(200),
    material_usage6 DECIMAL(10,2),
    material_unit_price6 DECIMAL(10,2),
    accessory_name VARCHAR(200),
    accessory_price DECIMAL(10,2),
    weaving_seconds DECIMAL(10,2),
    daily_output INTEGER,
    equipment_daily_cost DECIMAL(10,2),
    weaving_cost DECIMAL(10,2),
    yield_rate DECIMAL(5,2),
    sewing_weight DECIMAL(10,2),
    sewing_cost DECIMAL(10,2),
    dyeing_unit_price DECIMAL(10,2),
    dyeing_cost DECIMAL(10,2),
    setting_cost DECIMAL(10,2),
    packaging_cost DECIMAL(10,2),
    manufacturing_total DECIMAL(10,2),
    net_cost DECIMAL(10,2),
    sales_cost DECIMAL(10,2),
    tax_amount DECIMAL(10,2),
    machine_hourly_rate DECIMAL(10,2),
    single_machine_output_hourly DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS accessory_purchase (
    id INTEGER NOT NULL PRIMARY KEY,
    accessory_name VARCHAR(200),
    accessory_category VARCHAR(100),
    unit VARCHAR(20),
    supplier VARCHAR(200),
    accessory_unit_price DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS manufacturing_costs (
    id INTEGER NOT NULL PRIMARY KEY,
    product_id VARCHAR(36),
    cost_type VARCHAR(50),
    cost_amount DECIMAL(10,2),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS other_costs (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    product_id VARCHAR(36) NOT NULL,
    cost_name VARCHAR(200),
    cost_amount DECIMAL(10,2),
    cost_unit VARCHAR(20),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

-- 知识库
CREATE TABLE IF NOT EXISTS knowledge_base_categories (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id UUID,
    description TEXT,
    sort_order INTEGER DEFAULT 0,
    doc_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS knowledge_base_docs (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    original_name VARCHAR(200),
    stored_name VARCHAR(200),
    file_path VARCHAR(500),
    url TEXT,
    size BIGINT DEFAULT 0,
    content_type VARCHAR(100),
    extension VARCHAR(20),
    category VARCHAR(20) DEFAULT 'other',
    category_id UUID,
    user_id VARCHAR(36) NOT NULL,
    description TEXT,
    file_content TEXT,
    chunk_count INTEGER DEFAULT 0,
    embedding_status VARCHAR(20) DEFAULT 'PENDING',
    deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS knowledge_embeddings (
    id UUID NOT NULL PRIMARY KEY,
    card_id UUID,
    chunk_index INTEGER,
    chunk_text TEXT,
    embedding FLOAT8[] NOT NULL,
    embedding_model VARCHAR(100),
    source_type VARCHAR(20),
    source_doc_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

-- 记忆库（已合并但保留表）
CREATE TABLE IF NOT EXISTS knowledge_domains (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50),
    description TEXT,
    card_count INTEGER DEFAULT 0,
    card_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS knowledge_cards (
    id UUID NOT NULL PRIMARY KEY,
    domain_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    tags TEXT,
    source VARCHAR(50) DEFAULT 'manual',
    source_doc_id UUID,
    created_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS knowledge_documents (
    id UUID NOT NULL PRIMARY KEY,
    domain_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    original_name VARCHAR(200),
    stored_name VARCHAR(200),
    file_path VARCHAR(500),
    url TEXT,
    size BIGINT DEFAULT 0,
    content_type VARCHAR(100),
    extension VARCHAR(20),
    status VARCHAR(20) DEFAULT 'pending',
    chunk_count INTEGER DEFAULT 0,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS knowledge_chat_history (
    id UUID NOT NULL PRIMARY KEY,
    domain_id UUID NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    sources TEXT,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

-- AI 对话
CREATE TABLE IF NOT EXISTS smart_chat_conversations (
    id UUID NOT NULL PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS smart_chat_history (
    id UUID NOT NULL PRIMARY KEY,
    conversation_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    sources TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

-- 岗位知识卡片
CREATE TABLE IF NOT EXISTS position_knowledge_cards (
    id VARCHAR(36) PRIMARY KEY,
    card_code VARCHAR(50) NOT NULL,
    submit_date VARCHAR(20),
    department VARCHAR(100),
    position_name VARCHAR(100) NOT NULL,
    on_duty_person VARCHAR(100),
    report_to VARCHAR(100),
    team VARCHAR(100),
    position_nature VARCHAR(50),
    core_duties TEXT,
    auxiliary_duties TEXT,
    key_outputs TEXT,
    hard_skills TEXT,
    soft_skills TEXT,
    upstream_inputs TEXT,
    downstream_outputs TEXT,
    completed_work TEXT,
    in_progress TEXT,
    bottlenecks TEXT,
    support_needed TEXT,
    improvement_direction TEXT,
    process_optimization TEXT,
    tool_resource_needs TEXT,
    additional_notes TEXT,
    embedding_status VARCHAR(20) DEFAULT 'PENDING',
    user_id VARCHAR(100) NOT NULL,
    company VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 动态模型
CREATE TABLE IF NOT EXISTS data_models (
    id UUID NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT,
    table_name VARCHAR(100),
    icon VARCHAR(50),
    color VARCHAR(20),
    is_system BOOLEAN DEFAULT FALSE,
    record_count INTEGER DEFAULT 0,
    sort_order INTEGER DEFAULT 0,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS data_model_fields (
    id UUID NOT NULL PRIMARY KEY,
    model_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(100),
    type VARCHAR(30) NOT NULL,
    required BOOLEAN DEFAULT FALSE,
    default_value TEXT,
    options TEXT,
    sort_order INTEGER DEFAULT 0,
    show_in_list BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS data_model_records (
    id UUID NOT NULL PRIMARY KEY,
    model_id UUID NOT NULL,
    data JSONB NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

-- 审计日志
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID NOT NULL PRIMARY KEY,
    user_id VARCHAR(36),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(36),
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

-- 运维监控
CREATE TABLE IF NOT EXISTS api_metrics (
    id UUID NOT NULL PRIMARY KEY,
    endpoint VARCHAR(200) NOT NULL,
    method VARCHAR(10) NOT NULL,
    status_code INTEGER,
    response_time INTEGER,
    user_id VARCHAR(36),
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS system_errors (
    id UUID NOT NULL PRIMARY KEY,
    error_type VARCHAR(50) NOT NULL,
    error_message TEXT,
    stack_trace TEXT,
    user_id VARCHAR(36),
    endpoint VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS backup_records (
    id UUID NOT NULL PRIMARY KEY,
    backup_type VARCHAR(20) NOT NULL,
    file_path VARCHAR(500),
    size BIGINT,
    status VARCHAR(20) DEFAULT 'pending',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

-- 系统消息
CREATE TABLE IF NOT EXISTS system_messages (
    id UUID NOT NULL PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    sender_id VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(50)
);

-- =====================================================
-- 第二步：给已有但缺少 company 列的表添加 company
-- =====================================================
DO $$ BEGIN
    -- 用户相关
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_sessions' AND table_schema = 'public') THEN
        ALTER TABLE user_sessions ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'login_attempts' AND table_schema = 'public') THEN
        ALTER TABLE login_attempts ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_settings' AND table_schema = 'public') THEN
        ALTER TABLE user_settings ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;

    -- 相册与图片
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'albums' AND table_schema = 'public') THEN
        ALTER TABLE albums ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'images' AND table_schema = 'public') THEN
        ALTER TABLE images ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'favorites' AND table_schema = 'public') THEN
        ALTER TABLE favorites ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'tags' AND table_schema = 'public') THEN
        ALTER TABLE tags ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'image_tags' AND table_schema = 'public') THEN
        ALTER TABLE image_tags ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'documents' AND table_schema = 'public') THEN
        ALTER TABLE documents ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'notifications' AND table_schema = 'public') THEN
        ALTER TABLE notifications ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;

    -- 供应链
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'products' AND table_schema = 'public') THEN
        ALTER TABLE products ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'product_images' AND table_schema = 'public') THEN
        ALTER TABLE product_images ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'materials' AND table_schema = 'public') THEN
        ALTER TABLE materials ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'product_materials' AND table_schema = 'public') THEN
        ALTER TABLE product_materials ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'production_plan' AND table_schema = 'public') THEN
        ALTER TABLE production_plan ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'raw_material_purchase' AND table_schema = 'public') THEN
        ALTER TABLE raw_material_purchase ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'raw_material_warehouse' AND table_schema = 'public') THEN
        ALTER TABLE raw_material_warehouse ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'product_quotation' AND table_schema = 'public') THEN
        ALTER TABLE product_quotation ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'accessory_purchase' AND table_schema = 'public') THEN
        ALTER TABLE accessory_purchase ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'manufacturing_costs' AND table_schema = 'public') THEN
        ALTER TABLE manufacturing_costs ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'other_costs' AND table_schema = 'public') THEN
        ALTER TABLE other_costs ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;

    -- 知识库
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_base_categories' AND table_schema = 'public') THEN
        ALTER TABLE knowledge_base_categories ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_base_docs' AND table_schema = 'public') THEN
        ALTER TABLE knowledge_base_docs ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_embeddings' AND table_schema = 'public') THEN
        ALTER TABLE knowledge_embeddings ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;

    -- 记忆库
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_domains' AND table_schema = 'public') THEN
        ALTER TABLE knowledge_domains ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_cards' AND table_schema = 'public') THEN
        ALTER TABLE knowledge_cards ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_documents' AND table_schema = 'public') THEN
        ALTER TABLE knowledge_documents ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_chat_history' AND table_schema = 'public') THEN
        ALTER TABLE knowledge_chat_history ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;

    -- AI 对话
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'smart_chat_conversations' AND table_schema = 'public') THEN
        ALTER TABLE smart_chat_conversations ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'smart_chat_history' AND table_schema = 'public') THEN
        ALTER TABLE smart_chat_history ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;

    -- 岗位知识卡片
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'position_knowledge_cards' AND table_schema = 'public') THEN
        ALTER TABLE position_knowledge_cards ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;

    -- 动态模型
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'data_models' AND table_schema = 'public') THEN
        ALTER TABLE data_models ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'data_model_records' AND table_schema = 'public') THEN
        ALTER TABLE data_model_records ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;

    -- 审计日志
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'audit_logs' AND table_schema = 'public') THEN
        ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;

    -- 运维监控
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'api_metrics' AND table_schema = 'public') THEN
        ALTER TABLE api_metrics ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'system_errors' AND table_schema = 'public') THEN
        ALTER TABLE system_errors ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'backup_records' AND table_schema = 'public') THEN
        ALTER TABLE backup_records ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;

    -- 系统消息
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'system_messages' AND table_schema = 'public') THEN
        ALTER TABLE system_messages ADD COLUMN IF NOT EXISTS company VARCHAR(50);
    END IF;
END $$;

-- =====================================================
-- 第三步：创建 company 列索引（用 IF EXISTS 检查表存在）
-- =====================================================
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'user_sessions' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_user_sessions_company ON user_sessions(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'albums' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_albums_company ON albums(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'images' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_images_company ON images(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'documents' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_documents_company ON documents(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'products' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_products_company ON products(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'materials' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_materials_company ON materials(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'production_plan' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_production_plan_company ON production_plan(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'raw_material_purchase' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_raw_material_purchase_company ON raw_material_purchase(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'raw_material_warehouse' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_raw_material_warehouse_company ON raw_material_warehouse(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'product_quotation' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_product_quotation_company ON product_quotation(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'accessory_purchase' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_accessory_purchase_company ON accessory_purchase(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_base_categories' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_knowledge_base_categories_company ON knowledge_base_categories(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_base_docs' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_knowledge_base_docs_company ON knowledge_base_docs(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_embeddings' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_knowledge_embeddings_company ON knowledge_embeddings(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_domains' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_knowledge_domains_company ON knowledge_domains(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_cards' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_knowledge_cards_company ON knowledge_cards(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'data_models' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_data_models_company ON data_models(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'data_model_records' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_data_model_records_company ON data_model_records(company); END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'notifications' AND table_schema = 'public') THEN CREATE INDEX IF NOT EXISTS idx_notifications_company ON notifications(company); END IF;
END $$;

-- =====================================================
-- 第四步：插入默认数据（只在表存在且数据不存在时）
-- =====================================================
DO $$ BEGIN
    -- 默认管理员
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'users' AND table_schema = 'public') THEN
        IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin') THEN
            INSERT INTO users (id, username, password, email, role, company) VALUES (gen_random_uuid(), 'admin', 'encrypted_password', 'admin@yingyun.com', 'admin', '盈云');
        END IF;
    END IF;

    -- 默认普通用户
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'users' AND table_schema = 'public') THEN
        IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'user') THEN
            INSERT INTO users (id, username, password, email, role, company) VALUES (gen_random_uuid(), 'user', 'encrypted_password', 'user@yingyun.com', 'user', '盈云');
        END IF;
    END IF;

    -- 默认知识域
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_domains' AND table_schema = 'public') THEN
        IF NOT EXISTS (SELECT 1 FROM knowledge_domains WHERE code = 'fashion') THEN
            INSERT INTO knowledge_domains (name, code, description, company) VALUES ('时尚知识库', 'fashion', '时尚行业知识库', '盈云');
        END IF;
    END IF;

    -- 默认知识库分类
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'knowledge_base_categories' AND table_schema = 'public') THEN
        IF NOT EXISTS (SELECT 1 FROM knowledge_base_categories WHERE name = '默认分类') THEN
            INSERT INTO knowledge_base_categories (id, name, description, company, user_id)
            VALUES (gen_random_uuid(), '默认分类', '默认知识库分类', '盈云', 'admin-1');
        END IF;
    END IF;
END $$;
