-- V60: 钉钉组织架构（部门树 + 通讯录 + 本地用户绑定）
-- 办公/管理端使用；车间考勤不走钉钉。不破坏现有 users 登录账号。
-- 钉钉姓名注册：初始密码固定 123456，must_change_password=true；邮箱不要求（可空或占位符）。

-- 1) 部门树（钉钉 dept_id 为业务键）
CREATE TABLE IF NOT EXISTS org_departments (
    id                    VARCHAR(36) PRIMARY KEY,
    ding_dept_id          BIGINT NOT NULL,
    parent_ding_dept_id   BIGINT,
    name                  VARCHAR(200) NOT NULL,
    path                  VARCHAR(1000),
    company               VARCHAR(20) NOT NULL DEFAULT '宝娜斯集团',
    order_num             INTEGER NOT NULL DEFAULT 0,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    synced_at             TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (company, ding_dept_id)
);

CREATE INDEX IF NOT EXISTS idx_org_departments_parent
    ON org_departments (company, parent_ding_dept_id);
CREATE INDEX IF NOT EXISTS idx_org_departments_path
    ON org_departments (company, path);

COMMENT ON TABLE org_departments IS '钉钉同步的部门树（办公/管理端）';
COMMENT ON COLUMN org_departments.ding_dept_id IS '钉钉部门 ID（根部门为 1）';
COMMENT ON COLUMN org_departments.path IS '部门全路径，如 /宝娜斯集团/技术部/前端组';

-- 2) 钉钉通讯录人员（含尚未注册本地账号的联系人）
CREATE TABLE IF NOT EXISTS org_users (
    id                    VARCHAR(36) PRIMARY KEY,
    ding_userid           VARCHAR(64) NOT NULL,
    ding_unionid          VARCHAR(64),
    name                  VARCHAR(100) NOT NULL,
    job_title             VARCHAR(200),
    mobile                VARCHAR(32),
    email                 VARCHAR(200),
    avatar_url            VARCHAR(500),
    company               VARCHAR(20) NOT NULL DEFAULT '宝娜斯集团',
    primary_ding_dept_id  BIGINT,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    local_user_id         VARCHAR(36) REFERENCES users(id) ON DELETE SET NULL,
    synced_at             TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (company, ding_userid)
);

CREATE INDEX IF NOT EXISTS idx_org_users_name
    ON org_users (company, name);
CREATE INDEX IF NOT EXISTS idx_org_users_local_user
    ON org_users (local_user_id);
CREATE INDEX IF NOT EXISTS idx_org_users_unionid
    ON org_users (ding_unionid);

COMMENT ON TABLE org_users IS '钉钉通讯录缓存：注册时按姓名匹配';
COMMENT ON COLUMN org_users.job_title IS '钉钉职位头衔（自由文本，非结构化岗位表）';
COMMENT ON COLUMN org_users.local_user_id IS '已注册本地 users.id；未注册为空';

-- 3) 人员-部门多对多（钉钉一人可属多部门）
CREATE TABLE IF NOT EXISTS org_user_departments (
    org_user_id    VARCHAR(36) NOT NULL REFERENCES org_users(id) ON DELETE CASCADE,
    ding_dept_id   BIGINT NOT NULL,
    PRIMARY KEY (org_user_id, ding_dept_id)
);

CREATE INDEX IF NOT EXISTS idx_org_user_departments_dept
    ON org_user_departments (ding_dept_id);

-- 4) 同步状态（按公司一行）
CREATE TABLE IF NOT EXISTS org_sync_state (
    company        VARCHAR(20) PRIMARY KEY,
    last_sync_at   TIMESTAMP,
    last_status    VARCHAR(20),
    last_message   TEXT,
    dept_count     INTEGER,
    user_count     INTEGER,
    duration_ms    BIGINT
);

COMMENT ON TABLE org_sync_state IS '钉钉组织同步游标/结果';

-- 5) 本地用户绑定钉钉（增量列，不影响现有登录）
ALTER TABLE users ADD COLUMN IF NOT EXISTS dingtalk_userid VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS dingtalk_unionid VARCHAR(64);
ALTER TABLE users ADD COLUMN IF NOT EXISTS job_title VARCHAR(200);
ALTER TABLE users ADD COLUMN IF NOT EXISTS org_dept_id VARCHAR(36);
ALTER TABLE users ADD COLUMN IF NOT EXISTS ding_synced_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS idx_users_dingtalk_userid
    ON users (dingtalk_userid) WHERE dingtalk_userid IS NOT NULL;

COMMENT ON COLUMN users.dingtalk_userid IS '绑定的钉钉 userid；仅办公/管理端账号';
COMMENT ON COLUMN users.job_title IS '从钉钉同步的职位头衔';
COMMENT ON COLUMN users.org_dept_id IS '主部门 org_departments.id';

-- 6) 钉钉注册不要求邮箱：允许 email 为空；有值时仍走原唯一约束
-- 钉钉路径写入占位邮箱 dt-{userid}@dingtalk.invalid，避免与真实邮箱冲突
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
