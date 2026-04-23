-- 图片管理系统数据库增量更新脚本
-- 适用于 PostgreSQL 数据库（部分表已存在的情况）

-- ============================================
-- 1. 用户设置表（新增）
-- ============================================
CREATE TABLE IF NOT EXISTS user_settings (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    theme VARCHAR(20) DEFAULT 'system',
    language VARCHAR(10) DEFAULT 'zh-CN',
    page_size INTEGER DEFAULT 40,
    default_sort VARCHAR(50) DEFAULT 'createdAt',
    ai_recognition_enabled BOOLEAN DEFAULT true,
    email_notifications BOOLEAN DEFAULT true,
    system_notifications BOOLEAN DEFAULT true,
    upload_notifications BOOLEAN DEFAULT true,
    auto_play_videos BOOLEAN DEFAULT true,
    high_quality_previews BOOLEAN DEFAULT true,
    compact_mode BOOLEAN DEFAULT false,
    show_file_info BOOLEAN DEFAULT true,
    default_view VARCHAR(20) DEFAULT 'grid'
);

-- 为 user_settings 表添加外键约束（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_user_settings_user' 
        AND table_name = 'user_settings'
    ) THEN
        ALTER TABLE user_settings 
        ADD CONSTRAINT fk_user_settings_user 
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

-- ============================================
-- 2. 检查并添加 users 表可能缺失的列
-- ============================================
DO $$
BEGIN
    -- avatar_url
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'avatar_url') THEN
        ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500);
    END IF;
    
    -- nickname
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'nickname') THEN
        ALTER TABLE users ADD COLUMN nickname VARCHAR(50);
    END IF;
    
    -- bio
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'bio') THEN
        ALTER TABLE users ADD COLUMN bio TEXT;
    END IF;
    
    -- phone
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'phone') THEN
        ALTER TABLE users ADD COLUMN phone VARCHAR(20);
    END IF;
    
    -- role
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'role') THEN
        ALTER TABLE users ADD COLUMN role VARCHAR(20);
    END IF;
    
    -- membership
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'membership') THEN
        ALTER TABLE users ADD COLUMN membership VARCHAR(20);
    END IF;
    
    -- storage_used
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'storage_used') THEN
        ALTER TABLE users ADD COLUMN storage_used BIGINT;
    END IF;
    
    -- storage_limit
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'storage_limit') THEN
        ALTER TABLE users ADD COLUMN storage_limit BIGINT;
    END IF;
    
    -- created_at
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'created_at') THEN
        ALTER TABLE users ADD COLUMN created_at TIMESTAMP;
    END IF;
    
    -- last_login_at
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'last_login_at') THEN
        ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;
    END IF;
END $$;

-- ============================================
-- 3. 检查并添加 albums 表可能缺失的列
-- ============================================
-- ============================================
-- 3. 检查并添加 albums 表可能缺失的列
-- ============================================
DO $$
BEGIN
    -- matching_config
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'albums' AND column_name = 'matching_config') THEN
        ALTER TABLE albums ADD COLUMN matching_config TEXT;
    END IF;
    
    -- parent_id (支持层级结构)
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'albums' AND column_name = 'parent_id') THEN
        ALTER TABLE albums ADD COLUMN parent_id VARCHAR(36);
    END IF;
    
    -- path (层级路径，如 "松野湃/速干T恤")
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'albums' AND column_name = 'path') THEN
        ALTER TABLE albums ADD COLUMN path VARCHAR(500);
    END IF;
    
    -- full_name (完整显示名称，如 "松野湃-速干T恤")
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'albums' AND column_name = 'full_name') THEN
        ALTER TABLE albums ADD COLUMN full_name VARCHAR(200);
    END IF;
    
    -- 添加 path 的唯一约束（同一用户下 path 不能重复）
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'albums_user_path_unique') THEN
        ALTER TABLE albums ADD CONSTRAINT albums_user_path_unique UNIQUE (user_id, path);
    END IF;
END $$;

-- ============================================
-- 4. 检查并添加 images 表可能缺失的列
-- ============================================
DO $$
BEGIN
    -- original_name
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'original_name') THEN
        ALTER TABLE images ADD COLUMN original_name VARCHAR(255);
    END IF;
    
    -- thumbnail_url
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'thumbnail_url') THEN
        ALTER TABLE images ADD COLUMN thumbnail_url VARCHAR(500);
    END IF;
    
    -- mime_type
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'mime_type') THEN
        ALTER TABLE images ADD COLUMN mime_type VARCHAR(100);
    END IF;
    
    -- width
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'width') THEN
        ALTER TABLE images ADD COLUMN width INTEGER;
    END IF;
    
    -- height
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'height') THEN
        ALTER TABLE images ADD COLUMN height INTEGER;
    END IF;
    
    -- album_id
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'album_id') THEN
        ALTER TABLE images ADD COLUMN album_id VARCHAR(36);
    END IF;
    
    -- user_id
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'user_id') THEN
        ALTER TABLE images ADD COLUMN user_id VARCHAR(36);
    END IF;
    
    -- created_at
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'created_at') THEN
        ALTER TABLE images ADD COLUMN created_at TIMESTAMP;
    END IF;
    
    -- updated_at
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'updated_at') THEN
        ALTER TABLE images ADD COLUMN updated_at TIMESTAMP;
    END IF;
    
    -- favorite
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'favorite') THEN
        ALTER TABLE images ADD COLUMN favorite BOOLEAN DEFAULT false;
    END IF;
    
    -- trash
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'trash') THEN
        ALTER TABLE images ADD COLUMN trash BOOLEAN DEFAULT false;
    END IF;
    
    -- ai_tags
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'ai_tags') THEN
        ALTER TABLE images ADD COLUMN ai_tags TEXT;
    END IF;
    
    -- ai_category
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'ai_category') THEN
        ALTER TABLE images ADD COLUMN ai_category VARCHAR(100);
    END IF;
    
    -- description
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'description') THEN
        ALTER TABLE images ADD COLUMN description TEXT;
    END IF;
    
    -- product_id（用于关联同一商品的所有图片）
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'product_id') THEN
        ALTER TABLE images ADD COLUMN product_id VARCHAR(255);
    END IF;
    
    -- is_main_image（是否为主图）
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'is_main_image') THEN
        ALTER TABLE images ADD COLUMN is_main_image BOOLEAN DEFAULT false;
    END IF;
    
    -- display_order（显示顺序）
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'images' AND column_name = 'display_order') THEN
        ALTER TABLE images ADD COLUMN display_order INTEGER;
    END IF;
END $$;

-- ============================================
-- 5. 检查并创建 notifications 表（如果不存在）
-- ============================================
CREATE TABLE IF NOT EXISTS notifications (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    type VARCHAR(50),
    read BOOLEAN DEFAULT false,
    created_at TIMESTAMP,
    user_id VARCHAR(36) NOT NULL
);

-- 为 notifications 表添加外键约束（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_notifications_user' 
        AND table_name = 'notifications'
    ) THEN
        ALTER TABLE notifications 
        ADD CONSTRAINT fk_notifications_user 
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;
END $$;

-- ============================================
-- 6. 创建索引（如果不存在）
-- ============================================
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_images_user_id ON images(user_id);
CREATE INDEX IF NOT EXISTS idx_images_album_id ON images(album_id);
CREATE INDEX IF NOT EXISTS idx_images_trash ON images(trash);
CREATE INDEX IF NOT EXISTS idx_images_favorite ON images(favorite);
CREATE INDEX IF NOT EXISTS idx_albums_user_id ON albums(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);

-- ============================================
-- 7. 插入默认用户数据（如果不存在）
-- ============================================
INSERT INTO users (id, username, password, email, avatar_url, nickname, bio, phone, role, membership, storage_used, storage_limit, created_at, last_login_at)
VALUES (
    'user-1',
    'Alex Wang',
    'password123',
    'alex@example.com',
    NULL,
    'Alex',
    '摄影爱好者',
    '13800138000',
    'user',
    'pro',
    5368709120,
    53687091200,
    NOW() - INTERVAL '1 year',
    NOW()
) ON CONFLICT (id) DO UPDATE SET
    nickname = EXCLUDED.nickname,
    bio = EXCLUDED.bio,
    phone = EXCLUDED.phone,
    role = EXCLUDED.role,
    membership = EXCLUDED.membership;

-- ============================================
-- 8. 插入默认用户设置（如果不存在）
-- ============================================
INSERT INTO user_settings (id, user_id, theme, language, page_size, default_sort, ai_recognition_enabled, email_notifications, system_notifications, upload_notifications, auto_play_videos, high_quality_previews, compact_mode, show_file_info, default_view)
VALUES (
    'settings-1',
    'user-1',
    'system',
    'zh-CN',
    40,
    'createdAt',
    true,
    true,
    true,
    true,
    true,
    true,
    false,
    true,
    'grid'
) ON CONFLICT (id) DO UPDATE SET
    theme = EXCLUDED.theme,
    language = EXCLUDED.language,
    page_size = EXCLUDED.page_size,
    default_sort = EXCLUDED.default_sort,
    ai_recognition_enabled = EXCLUDED.ai_recognition_enabled,
    email_notifications = EXCLUDED.email_notifications,
    system_notifications = EXCLUDED.system_notifications,
    upload_notifications = EXCLUDED.upload_notifications,
    auto_play_videos = EXCLUDED.auto_play_videos,
    high_quality_previews = EXCLUDED.high_quality_previews,
    compact_mode = EXCLUDED.compact_mode,
    show_file_info = EXCLUDED.show_file_info,
    default_view = EXCLUDED.default_view;

-- ============================================
-- 9. 插入默认通知（如果不存在）
-- ============================================
INSERT INTO notifications (id, title, content, type, read, created_at, user_id)
VALUES 
    ('notif-1', '上传成功', '5张新图片上传成功', 'upload', false, NOW() - INTERVAL '2 minutes', 'user-1'),
    ('notif-2', '相册更新', '相册"风景"已更新', 'album', false, NOW() - INTERVAL '1 hour', 'user-1'),
    ('notif-3', '系统通知', '系统维护通知', 'system', true, NOW() - INTERVAL '2 days', 'user-1')
ON CONFLICT (id) DO NOTHING;

-- ============================================
-- 10. 插入默认相册（户外服装分类，如果不存在）
-- ============================================
INSERT INTO albums (id, name, description, cover_url, user_id, created_at, updated_at, image_count, is_public)
VALUES 
    ('album-tshirt', 'T恤', '各类T恤服装图片', NULL, 'user-1', NOW(), NOW(), 0, false),
    ('album-underwear', '内衣', '各类内衣服装图片', NULL, 'user-1', NOW(), NOW(), 0, false),
    ('album-fleece', '抓绒衣', '各类抓绒衣服装图片', NULL, 'user-1', NOW(), NOW(), 0, false),
    ('album-jacket', '冲锋衣', '各类冲锋衣服装图片', NULL, 'user-1', NOW(), NOW(), 0, false),
    ('album-softshell', '软壳', '各类软壳服装图片', NULL, 'user-1', NOW(), NOW(), 0, false)
ON CONFLICT (id) DO UPDATE SET
    description = EXCLUDED.description,
    updated_at = NOW();

-- 完成
SELECT '数据库更新完成！' AS status;
