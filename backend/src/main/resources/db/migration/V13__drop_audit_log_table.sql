-- ============================================================
-- V13__drop_audit_log_table.sql
-- 清理 V7 创建的 audit_log 表
--
-- 原因：
-- 1. 与 V11 创建的 audit_logs 表（应用层审计）功能重叠
-- 2. 统一使用 audit_logs（带 s）作为唯一的审计日志表
--
-- 操作：
-- 1. 删除所有使用 audit_log_trigger_function 的触发器
-- 2. 删除 audit_log_trigger_function 函数
-- 3. 删除 audit_log 表及其索引
-- ============================================================

-- ------------------------------------------------------------
-- 1. 删除所有相关触发器
-- ------------------------------------------------------------
DROP TRIGGER IF EXISTS audit_users_trigger ON users;
DROP TRIGGER IF EXISTS audit_images_trigger ON images;
DROP TRIGGER IF EXISTS audit_albums_trigger ON albums;
DROP TRIGGER IF EXISTS audit_notifications_trigger ON notifications;
DROP TRIGGER IF EXISTS audit_user_settings_trigger ON user_settings;

-- 兼容 products 表（如果存在）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'products') THEN
        DROP TRIGGER IF EXISTS audit_products_trigger ON products;
    END IF;
END $$;

-- ------------------------------------------------------------
-- 2. 删除触发器函数
-- ------------------------------------------------------------
DROP FUNCTION IF EXISTS audit_log_trigger_function();

-- ------------------------------------------------------------
-- 3. 删除 audit_log 表及其索引
-- ------------------------------------------------------------
DROP INDEX IF EXISTS idx_audit_log_table_name;
DROP INDEX IF EXISTS idx_audit_log_operation;
DROP INDEX IF EXISTS idx_audit_log_record_id;
DROP INDEX IF EXISTS idx_audit_log_changed_by;
DROP INDEX IF EXISTS idx_audit_log_changed_at;
DROP INDEX IF EXISTS idx_audit_log_table_record;

DROP TABLE IF EXISTS audit_log CASCADE;

-- ------------------------------------------------------------
-- 完成
-- ------------------------------------------------------------
RAISE NOTICE '已删除 audit_log 表及相关触发器和函数';
RAISE NOTICE '现在只使用 audit_logs（带 s）作为唯一的审计日志表';
