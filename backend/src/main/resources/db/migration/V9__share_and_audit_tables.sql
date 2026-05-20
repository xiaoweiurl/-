-- 分享链接表
CREATE TABLE IF NOT EXISTS share_links (
    id VARCHAR(36) PRIMARY KEY,
    resource_type VARCHAR(20) NOT NULL COMMENT '资源类型: album, image, document',
    resource_id VARCHAR(36) NOT NULL COMMENT '资源ID',
    share_code VARCHAR(10) NOT NULL UNIQUE COMMENT '分享码',
    password VARCHAR(50) COMMENT '访问密码',
    expire_at DATETIME COMMENT '过期时间',
    max_views INT DEFAULT -1 COMMENT '最大访问次数，-1表示无限制',
    view_count INT DEFAULT 0 COMMENT '访问次数',
    download_count INT DEFAULT 0 COMMENT '下载次数',
    created_by VARCHAR(36) NOT NULL COMMENT '创建者ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    INDEX idx_share_code (share_code),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_created_by (created_by),
    INDEX idx_expire_at (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享链接表';

-- 相册表增加可见性字段
ALTER TABLE albums ADD COLUMN IF NOT EXISTS visibility VARCHAR(20) DEFAULT 'private' COMMENT '可见性: private, public, password_protected';
ALTER TABLE albums ADD COLUMN IF NOT EXISTS share_password VARCHAR(50) COMMENT '分享密码';
ALTER TABLE albums ADD COLUMN IF NOT EXISTS share_enabled BOOLEAN DEFAULT FALSE COMMENT '是否开启分享';

-- 存储配额表
CREATE TABLE IF NOT EXISTS storage_quotas (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE COMMENT '用户ID',
    max_storage_bytes BIGINT DEFAULT 10737418240 COMMENT '最大存储空间(字节)，默认10GB',
    used_storage_bytes BIGINT DEFAULT 0 COMMENT '已使用空间(字节)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存储配额表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) COMMENT '用户ID',
    username VARCHAR(100) COMMENT '用户名',
    action VARCHAR(50) NOT NULL COMMENT '操作类型',
    resource_type VARCHAR(20) COMMENT '资源类型',
    resource_id VARCHAR(36) COMMENT '资源ID',
    resource_name VARCHAR(255) COMMENT '资源名称',
    details JSON COMMENT '详细信息',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    user_agent VARCHAR(500) COMMENT '用户代理',
    status VARCHAR(20) DEFAULT 'success' COMMENT '操作状态: success, failed',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_action (action),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- 分享访问记录表
CREATE TABLE IF NOT EXISTS share_access_logs (
    id VARCHAR(36) PRIMARY KEY,
    share_link_id VARCHAR(36) NOT NULL COMMENT '分享链接ID',
    action VARCHAR(20) NOT NULL COMMENT '操作: view, download',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    user_agent VARCHAR(500) COMMENT '用户代理',
    referer VARCHAR(500) COMMENT '来源页面',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_share_link_id (share_link_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (share_link_id) REFERENCES share_links(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分享访问记录表';

-- 系统设置表
CREATE TABLE IF NOT EXISTS system_settings (
    id VARCHAR(36) PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE COMMENT '设置键',
    setting_value TEXT COMMENT '设置值',
    description VARCHAR(255) COMMENT '设置描述',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_setting_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置表';

-- 插入默认系统设置
INSERT INTO system_settings (id, setting_key, setting_value, description) VALUES
    (UUID(), 'trash_retention_days', '30', '回收站保留天数'),
    (UUID(), 'share_default_expire_days', '7', '分享链接默认过期天数'),
    (UUID(), 'default_user_storage_quota', '10737418240', '默认用户存储配额(字节)，10GB'),
    (UUID(), 'max_upload_size', '104857600', '单文件最大上传大小(字节)，100MB'),
    (UUID(), 'allowed_file_types', 'jpg,jpeg,png,gif,bmp,webp,pdf,doc,docx,xls,xlsx,ppt,pptx,zip,rar', '允许上传的文件类型')
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;

-- 为用户初始化存储配额
INSERT INTO storage_quotas (id, user_id, max_storage_bytes, used_storage_bytes)
SELECT UUID(), id, 10737418240, 0 FROM users
ON DUPLICATE KEY UPDATE updated_at = CURRENT_TIMESTAMP;
