-- 检查 is_main_image 字段的值分布
SELECT
    is_main_image,
    COUNT(*) as count
FROM images
WHERE album_id = 'album-underwear'
GROUP BY is_main_image;

-- 查看前10条记录的 is_main_image 值
SELECT id, title, is_main_image
FROM images
WHERE album_id = 'album-underwear'
ORDER BY created_at DESC
LIMIT 10;
