-- V40: 删除 users 表的遗留 password 列
-- 密码统一存储在 password_hash 列（JPA 通过 @Column(name="password_hash") 映射 User.password 字段）
-- 旧的 password 列是废弃的遗留列，JPA 不读写它，保留只会造成混淆

-- 安全检查：如果 password 列有数据但 password_hash 为空，先同步数据
UPDATE users SET password_hash = password WHERE password_hash IS NULL AND password IS NOT NULL;

-- 删除遗留列
ALTER TABLE users DROP COLUMN IF EXISTS password;
