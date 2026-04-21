-- ============================================================
-- V7__audit_log.sql
-- 审计日志功能数据库迁移脚本
-- 
-- 功能：
-- 1. 创建审计日志表 (audit_log)
-- 2. 创建触发器自动记录所有数据变更操作
-- 3. 支持 INSERT/UPDATE/DELETE 操作的审计
-- 
-- 作者：Image Manager Team
-- 版本：1.0.0
-- ============================================================

-- ------------------------------------------------------------
-- 1. 创建审计日志表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id VARCHAR(36) PRIMARY KEY,
    table_name VARCHAR(100) NOT NULL,
    operation VARCHAR(20) NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    record_id VARCHAR(36),
    old_value JSONB,
    new_value JSONB,
    changed_by VARCHAR(36),
    changed_by_username VARCHAR(100),
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_ip VARCHAR(50),
    user_agent TEXT,
    request_path VARCHAR(255),
    request_method VARCHAR(10),
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------
-- 2. 创建索引以提高查询性能
-- ------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_audit_log_table_name ON audit_log(table_name);
CREATE INDEX IF NOT EXISTS idx_audit_log_operation ON audit_log(operation);
CREATE INDEX IF NOT EXISTS idx_audit_log_record_id ON audit_log(record_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_changed_by ON audit_log(changed_by);
CREATE INDEX IF NOT EXISTS idx_audit_log_changed_at ON audit_log(changed_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_table_record ON audit_log(table_name, record_id);

-- ------------------------------------------------------------
-- 3. 创建通用的审计日志记录函数
-- ------------------------------------------------------------
CREATE OR REPLACE FUNCTION audit_log_trigger_function()
RETURNS TRIGGER AS $$
DECLARE
    v_old_value JSONB;
    v_new_value JSONB;
    v_operation VARCHAR(20);
    v_record_id VARCHAR(36);
BEGIN
    -- 判断操作类型
    IF (TG_OP = 'INSERT') THEN
        v_operation := 'INSERT';
        v_old_value := NULL;
        v_new_value := row_to_json(NEW)::jsonb;
        -- 获取记录ID（优先使用id字段）
        IF (NEW.id IS NOT NULL) THEN
            v_record_id := NEW.id::VARCHAR;
        ELSIF (NEW.user_id IS NOT NULL) THEN
            v_record_id := NEW.user_id::VARCHAR;
        ELSIF (NEW.image_id IS NOT NULL) THEN
            v_record_id := NEW.image_id::VARCHAR;
        ELSIF (NEW.album_id IS NOT NULL) THEN
            v_record_id := NEW.album_id::VARCHAR;
        ELSE
            v_record_id := NULL;
        END IF;
    ELSIF (TG_OP = 'UPDATE') THEN
        v_operation := 'UPDATE';
        v_old_value := row_to_json(OLD)::jsonb;
        v_new_value := row_to_json(NEW)::jsonb;
        -- 获取记录ID
        IF (NEW.id IS NOT NULL) THEN
            v_record_id := NEW.id::VARCHAR;
        ELSIF (NEW.user_id IS NOT NULL) THEN
            v_record_id := NEW.user_id::VARCHAR;
        ELSIF (NEW.image_id IS NOT NULL) THEN
            v_record_id := NEW.image_id::VARCHAR;
        ELSIF (NEW.album_id IS NOT NULL) THEN
            v_record_id := NEW.album_id::VARCHAR;
        ELSIF (OLD.id IS NOT NULL) THEN
            v_record_id := OLD.id::VARCHAR;
        ELSE
            v_record_id := NULL;
        END IF;
    ELSIF (TG_OP = 'DELETE') THEN
        v_operation := 'DELETE';
        v_old_value := row_to_json(OLD)::jsonb;
        v_new_value := NULL;
        -- 获取记录ID
        IF (OLD.id IS NOT NULL) THEN
            v_record_id := OLD.id::VARCHAR;
        ELSIF (OLD.user_id IS NOT NULL) THEN
            v_record_id := OLD.user_id::VARCHAR;
        ELSIF (OLD.image_id IS NOT NULL) THEN
            v_record_id := OLD.image_id::VARCHAR;
        ELSIF (OLD.album_id IS NOT NULL) THEN
            v_record_id := OLD.album_id::VARCHAR;
        ELSE
            v_record_id := NULL;
        END IF;
    END IF;

    -- 插入审计日志记录
    INSERT INTO audit_log (
        id,
        table_name,
        operation,
        record_id,
        old_value,
        new_value,
        changed_at,
        created_at
    ) VALUES (
        gen_random_uuid()::VARCHAR,
        TG_TABLE_NAME,
        v_operation,
        v_record_id,
        v_old_value,
        v_new_value,
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

    IF (TG_OP = 'DELETE') THEN
        RETURN OLD;
    ELSE
        RETURN NEW;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ------------------------------------------------------------
-- 4. 为 users 表创建审计触发器
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS audit_users_trigger ON users;
CREATE TRIGGER audit_users_trigger
AFTER INSERT OR UPDATE OR DELETE ON users
FOR EACH ROW EXECUTE FUNCTION audit_log_trigger_function();

-- ------------------------------------------------------------
-- 5. 为 images 表创建审计触发器
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS audit_images_trigger ON images;
CREATE TRIGGER audit_images_trigger
AFTER INSERT OR UPDATE OR DELETE ON images
FOR EACH ROW EXECUTE FUNCTION audit_log_trigger_function();

-- ------------------------------------------------------------
-- 6. 为 albums 表创建审计触发器
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS audit_albums_trigger ON albums;
CREATE TRIGGER audit_albums_trigger
AFTER INSERT OR UPDATE OR DELETE ON albums
FOR EACH ROW EXECUTE FUNCTION audit_log_trigger_function();

-- ------------------------------------------------------------
-- 7. 为 notifications 表创建审计触发器
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS audit_notifications_trigger ON notifications;
CREATE TRIGGER audit_notifications_trigger
AFTER INSERT OR UPDATE OR DELETE ON notifications
FOR EACH ROW EXECUTE FUNCTION audit_log_trigger_function();

-- ------------------------------------------------------------
-- 8. 为 user_settings 表创建审计触发器
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS audit_user_settings_trigger ON user_settings;
CREATE TRIGGER audit_user_settings_trigger
AFTER INSERT OR UPDATE OR DELETE ON user_settings
FOR EACH ROW EXECUTE FUNCTION audit_log_trigger_function();

-- ------------------------------------------------------------
-- 9. 为 products 表创建审计触发器（如果存在）
-- ------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'products') THEN
        DROP TRIGGER IF EXISTS audit_products_trigger ON products;
        CREATE TRIGGER audit_products_trigger
        AFTER INSERT OR UPDATE OR DELETE ON products
        FOR EACH ROW EXECUTE FUNCTION audit_log_trigger_function();
        RAISE NOTICE '已为 products 表创建审计触发器';
    END IF;
END $$;

-- ------------------------------------------------------------
-- 完成
-- ------------------------------------------------------------
COMMENT ON TABLE audit_log IS '审计日志表 - 记录所有数据变更操作';
COMMENT ON COLUMN audit_log.old_value IS '变更前的值（JSONB格式）';
COMMENT ON COLUMN audit_log.new_value IS '变更后的值（JSONB格式）';

RAISE NOTICE '审计日志功能初始化完成！';
RAISE NOTICE '已为以下表启用审计：users, images, albums, notifications, user_settings';
