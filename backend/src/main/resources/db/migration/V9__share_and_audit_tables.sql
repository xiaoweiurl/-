-- 分享链接表
CREATE TABLE IF NOT EXISTS share_links (
    id VARCHAR(36) PRIMARY KEY,
    resource_type VARCHAR(20) NOT NULL,
    resource_id VARCHAR(36) NOT NULL,
    share_code VARCHAR(10) NOT NULL UNIQUE,
    password VARCHAR(50),
    expire_at TIMESTAMP,
    max_views INT DEFAULT -1,
    view_count INT DEFAULT 0,
    download_count INT DEFAULT 0,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_share_links_code ON share_links(share_code);
CREATE INDEX IF NOT EXISTS idx_share_links_resource ON share_links(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_share_links_created_by ON share_links(created_by);
CREATE INDEX IF NOT EXISTS idx_share_links_expire_at ON share_links(expire_at);

-- 添加表注释
COMMENT ON TABLE share_links IS '分享链接表';
COMMENT ON COLUMN share_links.resource_type IS '资源类型: album, image, document';
COMMENT ON COLUMN share_links.resource_id IS '资源ID';
COMMENT ON COLUMN share_links.share_code IS '分享码';
COMMENT ON COLUMN share_links.password IS '访问密码';
COMMENT ON COLUMN share_links.expire_at IS '过期时间';
COMMENT ON COLUMN share_links.max_views IS '最大访问次数，-1表示无限制';
COMMENT ON COLUMN share_links.view_count IS '访问次数';
COMMENT ON COLUMN share_links.download_count IS '下载次数';
COMMENT ON COLUMN share_links.created_by IS '创建者ID';

-- 相册表增加可见性字段
ALTER TABLE albums ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) DEFAULT 'private';
ALTER TABLE albums ADD COLUMN IF NOT EXISTS share_password VARCHAR(50);
ALTER TABLE albums ADD COLUMN IF NOT EXISTS share_enabled BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN albums.visibility IS '可见性: private, public, password_protected';
COMMENT ON COLUMN albums.share_password IS '分享密码';
COMMENT ON COLUMN albums.share_enabled IS '是否开启分享';

-- 存储配额表
CREATE TABLE IF NOT EXISTS storage_quotas (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    max_storage_bytes BIGINT DEFAULT 10737418240,
    used_storage_bytes BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_storage_quotas_user_id ON storage_quotas(user_id);

COMMENT ON TABLE storage_quotas IS '存储配额表';
COMMENT ON COLUMN storage_quotas.user_id IS '用户ID';
COMMENT ON COLUMN storage_quotas.max_storage_bytes IS '最大存储空间(字节)，默认10GB';
COMMENT ON COLUMN storage_quotas.used_storage_bytes IS '已使用空间(字节)';

-- 操作日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36),
    username VARCHAR(100),
    action VARCHAR(50) NOT NULL,
    resource_type VARCHAR(20),
    resource_id VARCHAR(36),
    resource_name VARCHAR(255),
    details JSONB,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    status VARCHAR(20) DEFAULT 'success',
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource ON audit_logs(resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);

COMMENT ON TABLE audit_logs IS '操作日志表';
COMMENT ON COLUMN audit_logs.user_id IS '用户ID';
COMMENT ON COLUMN audit_logs.username IS '用户名';
COMMENT ON COLUMN audit_logs.action IS '操作类型';
COMMENT ON COLUMN audit_logs.resource_type IS '资源类型';
COMMENT ON COLUMN audit_logs.resource_id IS '资源ID';
COMMENT ON COLUMN audit_logs.resource_name IS '资源名称';
COMMENT ON COLUMN audit_logs.details IS '详细信息';
COMMENT ON COLUMN audit_logs.ip_address IS 'IP地址';
COMMENT ON COLUMN audit_logs.user_agent IS '用户代理';
COMMENT ON COLUMN audit_logs.status IS '操作状态: success, failed';
COMMENT ON COLUMN audit_logs.error_message IS '错误信息';

-- 分享访问记录表
CREATE TABLE IF NOT EXISTS share_access_logs (
    id VARCHAR(36) PRIMARY KEY,
    share_link_id VARCHAR(36) NOT NULL,
    action VARCHAR(20) NOT NULL,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    referer VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_share_access_logs_share_link FOREIGN KEY (share_link_id) REFERENCES share_links(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_share_access_logs_share_link_id ON share_access_logs(share_link_id);
CREATE INDEX IF NOT EXISTS idx_share_access_logs_created_at ON share_access_logs(created_at);

COMMENT ON TABLE share_access_logs IS '分享访问记录表';
COMMENT ON COLUMN share_access_logs.share_link_id IS '分享链接ID';
COMMENT ON COLUMN share_access_logs.action IS '操作: view, download';
COMMENT ON COLUMN share_access_logs.ip_address IS 'IP地址';
COMMENT ON COLUMN share_access_logs.user_agent IS '用户代理';
COMMENT ON COLUMN share_access_logs.referer IS '来源页面';

-- 系统设置表
CREATE TABLE IF NOT EXISTS system_settings (
    id VARCHAR(36) PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_settings_setting_key ON system_settings(setting_key);

COMMENT ON TABLE system_settings IS '系统设置表';
COMMENT ON COLUMN system_settings.setting_key IS '设置键';
COMMENT ON COLUMN system_settings.setting_value IS '设置值';
COMMENT ON COLUMN system_settings.description IS '设置描述';

-- 插入默认系统设置 (使用 ON CONFLICT 处理重复)
INSERT INTO system_settings (id, setting_key, setting_value, description) VALUES
    (gen_random_uuid()::text, 'trash_retention_days', '30', '回收站保留天数'),
    (gen_random_uuid()::text, 'share_default_expire_days', '7', '分享链接默认过期天数'),
    (gen_random_uuid()::text, 'default_user_storage_quota', '10737418240', '默认用户存储配额(字节)，10GB'),
    (gen_random_uuid()::text, 'max_upload_size', '104857600', '单文件最大上传大小(字节)，100MB'),
    (gen_random_uuid()::text, 'allowed_file_types', 'jpg,jpeg,png,gif,bmp,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,rar', '允许上传的文件类型')
ON CONFLICT (setting_key) DO UPDATE SET updated_at = CURRENT_TIMESTAMP;

-- 为用户初始化存储配额
INSERT INTO storage_quotas (id, user_id, max_storage_bytes, used_storage_bytes)
SELECT gen_random_uuid()::text, id, 10737418240, 0 FROM users
ON CONFLICT (user_id) DO UPDATE SET updated_at = CURRENT_TIMESTAMP;

-- 创建更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 为各表创建触发器
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_share_links_updated_at') THEN
        CREATE TRIGGER update_share_links_updated_at BEFORE UPDATE ON share_links FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_storage_quotas_updated_at') THEN
        CREATE TRIGGER update_storage_quotas_updated_at BEFORE UPDATE ON storage_quotas FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
    
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_system_settings_updated_at') THEN
        CREATE TRIGGER update_system_settings_updated_at BEFORE UPDATE ON system_settings FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
