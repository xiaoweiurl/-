-- 用户会话表：持久化存储 session，解决后端重启后 session 丢失问题

CREATE TABLE IF NOT EXISTS user_sessions (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100),
    avatar_url VARCHAR(500),
    role VARCHAR(20),
    membership VARCHAR(20),
    remember_me BOOLEAN DEFAULT false,
    created_at BIGINT NOT NULL,
    last_access_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL
);

-- 为 session 查询添加索引
CREATE INDEX IF NOT EXISTS idx_user_sessions_expires_at ON user_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_user_sessions_user_id ON user_sessions(user_id);
