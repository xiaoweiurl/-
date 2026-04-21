-- 图片管理系统商品表增量更新脚本
-- 适用于 PostgreSQL 数据库

-- ============================================
-- 1. 创建 products 商品表
-- ============================================
CREATE TABLE IF NOT EXISTS products (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,           -- 商品名称
    description TEXT,                     -- 商品描述
    category VARCHAR(100),                -- 商品分类（如：T恤、内衣、抓绒衣、冲锋衣、软壳）
    album_id VARCHAR(36),                 -- 关联的相册ID
    user_id VARCHAR(36),                  -- 所属用户ID
    cover_image_id VARCHAR(36),           -- 封面图片ID（主图）
    image_count INTEGER DEFAULT 0,        -- 图片总数
    created_at TIMESTAMP,                 -- 创建时间
    updated_at TIMESTAMP                  -- 更新时间
);

-- 为 products 表添加外键约束（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_products_user'
        AND table_name = 'products'
    ) THEN
        ALTER TABLE products
        ADD CONSTRAINT fk_products_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_products_album'
        AND table_name = 'products'
    ) THEN
        ALTER TABLE products
        ADD CONSTRAINT fk_products_album
        FOREIGN KEY (album_id) REFERENCES albums(id) ON DELETE SET NULL;
    END IF;
END $$;

-- ============================================
-- 2. 创建索引
-- ============================================
CREATE INDEX IF NOT EXISTS idx_products_user_id ON products(user_id);
CREATE INDEX IF NOT EXISTS idx_products_album_id ON products(album_id);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
CREATE INDEX IF NOT EXISTS idx_images_product_id ON images(product_id);
CREATE INDEX IF NOT EXISTS idx_images_is_main_image ON images(is_main_image);

-- ============================================
-- 3. 创建视图：只显示主图的商品列表
-- ============================================
CREATE OR REPLACE VIEW product_main_images AS
SELECT
    p.id AS product_id,
    p.name AS product_name,
    p.description AS product_description,
    p.category AS product_category,
    p.image_count AS total_image_count,
    i.id AS image_id,
    i.url AS image_url,
    i.thumbnail_url,
    i.title,
    i.description AS image_description,
    i.album_id,
    i.created_at
FROM products p
INNER JOIN images i ON p.id = i.product_id AND i.is_main_image = true AND i.trash = false
WHERE p.id IS NOT NULL
ORDER BY p.created_at DESC;

-- ============================================
-- 4. 创建视图：商品所有图片
-- ============================================
CREATE OR REPLACE VIEW product_all_images AS
SELECT
    p.id AS product_id,
    p.name AS product_name,
    p.category AS product_category,
    i.id AS image_id,
    i.url AS image_url,
    i.thumbnail_url,
    i.title,
    i.description AS image_description,
    i.is_main_image,
    i.display_order,
    i.album_id,
    i.created_at
FROM products p
INNER JOIN images i ON p.id = i.product_id AND i.trash = false
WHERE p.id IS NOT NULL
ORDER BY p.id, i.display_order ASC;

-- ============================================
-- 5. 添加注释
-- ============================================
COMMENT ON TABLE products IS '商品表，用于管理商品信息和关联商品图片';
COMMENT ON COLUMN products.id IS '商品ID（UUID）';
COMMENT ON COLUMN products.name IS '商品名称';
COMMENT ON COLUMN products.description IS '商品描述';
COMMENT ON COLUMN products.category IS '商品分类';
COMMENT ON COLUMN products.album_id IS '关联的相册ID';
COMMENT ON COLUMN products.user_id IS '所属用户ID';
COMMENT ON COLUMN products.cover_image_id IS '封面图片ID（主图ID）';
COMMENT ON COLUMN products.image_count IS '该商品的图片总数';
COMMENT ON VIEW product_main_images IS '商品主图视图，用于在全部图片列表中只显示每个商品的主图';
COMMENT ON VIEW product_all_images IS '商品所有图片视图，用于显示某个商品的所有图片';

-- 完成
SELECT '商品表创建完成！' AS status;
