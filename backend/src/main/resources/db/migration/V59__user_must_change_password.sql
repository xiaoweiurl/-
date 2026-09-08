-- V59: users 表增加"首次登录强制改密"标志
-- 种子账号创建时置 true，用户首次修改密码后置 false
ALTER TABLE users ADD COLUMN IF NOT EXISTS must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN users.must_change_password IS '是否强制首次登录修改密码（种子账号为 true）';

-- 存量预置种子账号（若仍使用初始密码）标记为强制改密，首次登录后必须修改
UPDATE users SET must_change_password = TRUE WHERE username IN ('superadmin', 'admin', 'user');
