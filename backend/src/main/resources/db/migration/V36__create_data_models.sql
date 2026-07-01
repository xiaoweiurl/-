-- V36: 数据模型（元数据驱动：动态字段、自定义表单、可配置数据模型）

-- 数据模型定义
CREATE TABLE IF NOT EXISTS data_models (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,          -- 模型编码，如 product_spec, supplier_info
    description TEXT,
    icon VARCHAR(50),                           -- 图标名
    category VARCHAR(100),                      -- 分类（如 产品/供应商/订单）
    version INT NOT NULL DEFAULT 1,             -- 模型版本号
    is_system BOOLEAN NOT NULL DEFAULT FALSE,   -- 系统内置模型不可删除
    status VARCHAR(20) NOT NULL DEFAULT 'active', -- active/draft/archived
    sort_order INT NOT NULL DEFAULT 0,
    config JSONB DEFAULT '{}',                  -- 模型级配置（如列表视图字段、默认排序等）
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 数据模型字段定义（动态字段）
CREATE TABLE IF NOT EXISTS data_model_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id UUID NOT NULL REFERENCES data_models(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,                 -- 字段显示名
    code VARCHAR(100) NOT NULL,                 -- 字段编码，如 price, color, size
    field_type VARCHAR(50) NOT NULL,            -- 字段类型：text/number/decimal/boolean/date/datetime/select/multi_select/url/email/textarea/json/relation/file/image
    required BOOLEAN NOT NULL DEFAULT FALSE,
    unique_field BOOLEAN NOT NULL DEFAULT FALSE,
    searchable BOOLEAN NOT NULL DEFAULT TRUE,   -- 是否可搜索
    show_in_list BOOLEAN NOT NULL DEFAULT TRUE, -- 列表页是否显示
    sort_order INT NOT NULL DEFAULT 0,
    default_value TEXT,                         -- 默认值
    placeholder VARCHAR(500),                   -- 占位提示
    options JSONB,                              -- select/multi_select 的选项列表 [{label,value}]
    validation JSONB,                           -- 验证规则 {min,max,pattern,message}
    relation_config JSONB,                      -- relation 类型的配置 {modelId,fieldId,multiple}
    group_name VARCHAR(100),                    -- 字段分组（如 基本信息/规格参数/价格信息）
    description TEXT,                           -- 字段说明
    width INT,                                  -- 列表页列宽
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(model_id, code)
);

-- 数据模型记录（动态数据，EAV 模式）
CREATE TABLE IF NOT EXISTS data_model_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id UUID NOT NULL REFERENCES data_models(id) ON DELETE CASCADE,
    data JSONB NOT NULL DEFAULT '{}',           -- 动态字段数据 {field_code: value, ...}
    status VARCHAR(20) NOT NULL DEFAULT 'active', -- active/archived/draft
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_by VARCHAR(100)
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_dmf_model_id ON data_model_fields(model_id);
CREATE INDEX IF NOT EXISTS idx_dmr_model_id ON data_model_records(model_id);
CREATE INDEX IF NOT EXISTS idx_dm_category ON data_models(category);
CREATE INDEX IF NOT EXISTS idx_dm_code ON data_models(code);
CREATE INDEX IF NOT EXISTS idx_dm_status ON data_models(status);

-- 插入示例模型：产品规格
INSERT INTO data_models (id, name, code, description, icon, category, is_system, config, created_by)
VALUES (
    'a0000001-0000-0000-0000-000000000001',
    '产品规格', 'product_spec', '产品规格参数数据模型，支持动态字段配置', 'Package', '产品', true,
    '{"listFields":["name","category","season","price"],"defaultSort":"name"}'::jsonb,
    'system'
);

INSERT INTO data_model_fields (model_id, name, code, field_type, required, show_in_list, sort_order, group_name, options, validation)
VALUES
    ('a0000001-0000-0000-0000-000000000001', '产品名称', 'name', 'text', true, true, 1, '基本信息', NULL, NULL),
    ('a0000001-0000-0000-0000-000000000001', '品类', 'category', 'select', true, true, 2, '基本信息', '[{"label":"上衣","value":"上衣"},{"label":"裤装","value":"裤装"},{"label":"内衣","value":"内衣"},{"label":"配饰","value":"配饰"},{"label":"鞋靴","value":"鞋靴"}]'::jsonb, NULL),
    ('a0000001-0000-0000-0000-000000000001', '季节', 'season', 'select', false, true, 3, '基本信息', '[{"label":"春夏","value":"春夏"},{"label":"秋冬","value":"秋冬"},{"label":"全季","value":"全季"}]'::jsonb, NULL),
    ('a0000001-0000-0000-0000-000000000001', '品牌', 'brand', 'text', false, true, 4, '基本信息', NULL, NULL),
    ('a0000001-0000-0000-0000-000000000001', '单价(元)', 'price', 'decimal', true, true, 5, '价格信息', NULL, '{"min":0}'::jsonb),
    ('a0000001-0000-0000-0000-000000000001', '成本价', 'cost_price', 'decimal', false, false, 6, '价格信息', NULL, '{"min":0}'::jsonb),
    ('a0000001-0000-0000-0000-000000000001', '颜色', 'color', 'text', false, true, 7, '规格参数', NULL, NULL),
    ('a0000001-0000-0000-0000-000000000001', '尺码', 'size', 'select', false, true, 8, '规格参数', '[{"label":"XS","value":"XS"},{"label":"S","value":"S"},{"label":"M","value":"M"},{"label":"L","value":"L"},{"label":"XL","value":"XL"},{"label":"XXL","value":"XXL"}]'::jsonb, NULL),
    ('a0000001-0000-0000-0000-000000000001', '面料', 'fabric', 'text', false, false, 9, '规格参数', NULL, NULL),
    ('a0000001-0000-0000-0000-000000000001', '重量(g)', 'weight', 'number', false, false, 10, '规格参数', NULL, '{"min":0}'::jsonb),
    ('a0000001-0000-0000-0000-000000000001', '备注', 'remark', 'textarea', false, false, 11, '其他', NULL, NULL);

-- 示例模型：供应商信息
INSERT INTO data_models (id, name, code, description, icon, category, is_system, config, created_by)
VALUES (
    'a0000001-0000-0000-0000-000000000002',
    '供应商信息', 'supplier_info', '供应商基础信息与资质数据模型', 'Building2', '供应链', true,
    '{"listFields":["name","contact","phone","region"],"defaultSort":"name"}'::jsonb,
    'system'
);

INSERT INTO data_model_fields (model_id, name, code, field_type, required, show_in_list, sort_order, group_name, options, validation)
VALUES
    ('a0000001-0000-0000-0000-000000000002', '供应商名称', 'name', 'text', true, true, 1, '基本信息', NULL, NULL),
    ('a0000001-0000-0000-0000-000000000002', '联系人', 'contact', 'text', true, true, 2, '基本信息', NULL, NULL),
    ('a0000001-0000-0000-0000-000000000002', '电话', 'phone', 'text', true, true, 3, '基本信息', NULL, NULL),
    ('a0000001-0000-0000-0000-000000000002', '邮箱', 'email', 'email', false, true, 4, '基本信息', NULL, NULL),
    ('a0000001-0000-0000-0000-000000000002', '地区', 'region', 'select', false, true, 5, '基本信息', '[{"label":"浙江","value":"浙江"},{"label":"江苏","value":"江苏"},{"label":"广东","value":"广东"},{"label":"福建","value":"福建"},{"label":"上海","value":"上海"}]'::jsonb, NULL),
    ('a0000001-0000-0000-0000-000000000002', '主营类目', 'category', 'multi_select', false, true, 6, '业务信息', '[{"label":"面料","value":"面料"},{"label":"辅料","value":"辅料"},{"label":"成衣加工","value":"成衣加工"},{"label":"印染","value":"印染"}]'::jsonb, NULL),
    ('a0000001-0000-0000-0000-000000000002', '合作等级', 'level', 'select', false, true, 7, '业务信息', '[{"label":"A级","value":"A"},{"label":"B级","value":"B"},{"label":"C级","value":"C"}]'::jsonb, NULL),
    ('a0000001-0000-0000-0000-000000000002', '备注', 'remark', 'textarea', false, false, 8, '其他', NULL, NULL);
