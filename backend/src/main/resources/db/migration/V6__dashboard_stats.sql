-- =====================================================
-- 仪表盘统计功能 SQL 优化
-- 为仪表盘统计查询创建必要的索引
-- =====================================================

-- 1. 图片表统计查询优化索引
-- 用于快速查询未删除图片数量、总大小、收藏数量等
CREATE INDEX IF NOT EXISTS idx_images_deleted ON images(deleted);
CREATE INDEX IF NOT EXISTS idx_images_favorite_deleted ON images(favorite, deleted) WHERE deleted = false;
CREATE INDEX IF NOT EXISTS idx_images_created_at_deleted ON images(created_at, deleted);
CREATE INDEX IF NOT EXISTS idx_images_file_type_deleted ON images(file_type, deleted) WHERE deleted = false;

-- 复合索引：支持按相册和删除状态查询
CREATE INDEX IF NOT EXISTS idx_images_album_deleted ON images(album_id, deleted) WHERE deleted = false;

-- 2. 图片标签表统计查询优化索引
-- 用于热门标签统计
CREATE INDEX IF NOT EXISTS idx_image_tags_name ON image_tags(tag);
CREATE INDEX IF NOT EXISTS idx_image_tags_image_id ON image_tags(image_id);

-- 3. 查询统计视图（可选，用于复杂统计）
-- 每日上传统计视图
CREATE OR REPLACE VIEW v_daily_upload_stats AS
SELECT 
    DATE(created_at) as upload_date,
    COUNT(*) as upload_count,
    COALESCE(SUM(size), 0) as total_size
FROM images
WHERE deleted = false
GROUP BY DATE(created_at);

-- 相册图片统计视图
CREATE OR REPLACE VIEW v_album_stats AS
SELECT 
    a.id as album_id,
    a.name as album_name,
    COUNT(i.id) as image_count,
    COALESCE(SUM(i.size), 0) as total_size
FROM albums a
LEFT JOIN images i ON a.id = i.album_id AND i.deleted = false
GROUP BY a.id, a.name;

-- 标签使用统计视图
CREATE OR REPLACE VIEW v_tag_stats AS
SELECT 
    t.tag as tag_name,
    COUNT(*) as usage_count
FROM image_tags t
JOIN images i ON t.image_id = i.id AND i.deleted = false
GROUP BY t.tag;

-- 4. 统计函数
-- 获取概览统计的函数
CREATE OR REPLACE FUNCTION get_overview_stats()
RETURNS TABLE (
    total_images BIGINT,
    total_size BIGINT,
    total_albums BIGINT,
    total_tags BIGINT,
    favorites_count BIGINT,
    trash_count BIGINT,
    recent_uploads_7d BIGINT,
    recent_uploads_30d BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        (SELECT COUNT(*) FROM images WHERE deleted = false),
        (SELECT COALESCE(SUM(size), 0) FROM images WHERE deleted = false),
        (SELECT COUNT(*) FROM albums),
        (SELECT COUNT(DISTINCT tag) FROM image_tags),
        (SELECT COUNT(*) FROM images WHERE favorite = true AND deleted = false),
        (SELECT COUNT(*) FROM images WHERE deleted = true),
        (SELECT COUNT(*) FROM images WHERE created_at >= NOW() - INTERVAL '7 days' AND deleted = false),
        (SELECT COUNT(*) FROM images WHERE created_at >= NOW() - INTERVAL '30 days' AND deleted = false);
END;
$$ LANGUAGE plpgsql;

-- 获取每日统计的函数（指定天数）
CREATE OR REPLACE FUNCTION get_daily_stats(days INTEGER)
RETURNS TABLE (
    stat_date DATE,
    upload_count BIGINT,
    total_size BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        DATE(i.created_at),
        COUNT(*),
        COALESCE(SUM(i.size), 0)
    FROM images i
    WHERE i.created_at >= NOW() - (days || ' days')::INTERVAL
      AND i.deleted = false
    GROUP BY DATE(i.created_at)
    ORDER BY DATE(i.created_at);
END;
$$ LANGUAGE plpgsql;

-- 获取文件类型统计的函数
CREATE OR REPLACE FUNCTION get_file_type_stats()
RETURNS TABLE (
    file_type VARCHAR,
    type_count BIGINT,
    total_size BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        UPPER(COALESCE(i.file_type, 'UNKNOWN')),
        COUNT(*),
        COALESCE(SUM(i.size), 0)
    FROM images i
    WHERE i.deleted = false
    GROUP BY i.file_type
    ORDER BY COUNT(*) DESC;
END;
$$ LANGUAGE plpgsql;

-- 5. 评论说明
COMMENT ON VIEW v_daily_upload_stats IS '每日上传统计视图';
COMMENT ON VIEW v_album_stats IS '相册图片统计视图';
COMMENT ON VIEW v_tag_stats IS '标签使用统计视图';
COMMENT ON FUNCTION get_overview_stats() IS '获取仪表盘概览统计';
COMMENT ON FUNCTION get_daily_stats(INTEGER) IS '获取指定天数的每日统计';
COMMENT ON FUNCTION get_file_type_stats() IS '获取文件类型统计';
