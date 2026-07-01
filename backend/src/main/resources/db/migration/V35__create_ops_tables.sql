-- ============================================================
-- V35__create_ops_tables.sql
-- 系统运维中心数据库表
-- ============================================================

-- API 调用指标表（记录每个 API 端点的调用统计）
CREATE TABLE IF NOT EXISTS api_metrics (
    id VARCHAR(36) PRIMARY KEY,
    endpoint VARCHAR(255) NOT NULL,
    method VARCHAR(10) NOT NULL,
    status_code INT NOT NULL,
    response_time_ms INT NOT NULL,
    user_id VARCHAR(36),
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    request_size BIGINT DEFAULT 0,
    response_size BIGINT DEFAULT 0,
    error_message TEXT,
    company VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_api_metrics_endpoint ON api_metrics(endpoint, method);
CREATE INDEX IF NOT EXISTS idx_api_metrics_created_at ON api_metrics(created_at);
CREATE INDEX IF NOT EXISTS idx_api_metrics_status ON api_metrics(status_code);
CREATE INDEX IF NOT EXISTS idx_api_metrics_company ON api_metrics(company);

COMMENT ON TABLE api_metrics IS 'API调用指标表';
COMMENT ON COLUMN api_metrics.endpoint IS 'API端点路径';
COMMENT ON COLUMN api_metrics.method IS 'HTTP方法';
COMMENT ON COLUMN api_metrics.status_code IS 'HTTP状态码';
COMMENT ON COLUMN api_metrics.response_time_ms IS '响应时间(毫秒)';

-- 系统错误表（记录应用错误和异常）
CREATE TABLE IF NOT EXISTS system_errors (
    id VARCHAR(36) PRIMARY KEY,
    error_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'error',
    message TEXT NOT NULL,
    stack_trace TEXT,
    endpoint VARCHAR(255),
    method VARCHAR(10),
    user_id VARCHAR(36),
    ip_address VARCHAR(50),
    request_body TEXT,
    status_code INT,
    resolved BOOLEAN DEFAULT FALSE,
    resolved_by VARCHAR(36),
    resolved_at TIMESTAMP,
    occurrence_count INT DEFAULT 1,
    first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    company VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_errors_type ON system_errors(error_type);
CREATE INDEX IF NOT EXISTS idx_system_errors_severity ON system_errors(severity);
CREATE INDEX IF NOT EXISTS idx_system_errors_resolved ON system_errors(resolved);
CREATE INDEX IF NOT EXISTS idx_system_errors_last_seen ON system_errors(last_seen_at);
CREATE INDEX IF NOT EXISTS idx_system_errors_company ON system_errors(company);

COMMENT ON TABLE system_errors IS '系统错误表';
COMMENT ON COLUMN system_errors.severity IS '严重级别: warning, error, critical, fatal';
COMMENT ON COLUMN system_errors.resolved IS '是否已解决';


-- 备份记录表
CREATE TABLE IF NOT EXISTS backup_records (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'full',
    size_bytes BIGINT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    storage_path VARCHAR(500),
    description TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    created_by VARCHAR(36),
    company VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_backup_records_status ON backup_records(status);
CREATE INDEX IF NOT EXISTS idx_backup_records_type ON backup_records(type);
CREATE INDEX IF NOT EXISTS idx_backup_records_company ON backup_records(company);

COMMENT ON TABLE backup_records IS '备份记录表';
COMMENT ON COLUMN backup_records.type IS '备份类型: full, images, database, settings';
COMMENT ON COLUMN backup_records.status IS '状态: pending, running, completed, failed';
