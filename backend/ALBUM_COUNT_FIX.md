# 相册图片数量统计修复

## 问题描述

相册没有正确显示主图的图片数量，导致统计不准确。

**根本原因**：
- 相册的`image_count`字段统计的是所有图片的数量，而不是主图（商品）的数量
- 根据商品层级管理的设计，相册应该只统计主图数量（即商品数量）

---

## 修复方案

### 1. 后端修改

#### ImageRepository.java
**新增方法**：
```java
/**
 * 按相册统计主图数量（商品数量）
 */
@Query("SELECT COUNT(i) FROM Image i WHERE i.deleted = false AND i.isMainImage = true AND i.albumId = :albumId")
long countMainImagesByAlbumId(@Param("albumId") String albumId);
```

#### ImageServiceImpl.java
**修改方法**：`updateAlbumImageCount()`
```java
/**
 * 更新相册图片数量（只统计主图，即商品数量）
 */
private void updateAlbumImageCount(String albumId) {
    albumRepository.findById(albumId).ifPresent(album -> {
        // 只统计主图数量（商品数量）
        long count = imageRepository.countMainImagesByAlbumId(albumId);
        album.setImageCount((int) count);
        album.setUpdatedAt(LocalDateTime.now());
        albumRepository.save(album);
        log.debug("更新相册图片数量: albumId={}, count={} (主图数量)", albumId, count);
    });
}
```

**修改方法**：`initDefaultImages()`
```java
// 更新相册图片数量（只统计主图，即商品数量）
albumRepository.findAll().forEach(album -> {
    long count = imageRepository.countMainImagesByAlbumId(album.getId());
    album.setImageCount((int) count);
    album.setUpdatedAt(LocalDateTime.now());
    albumRepository.save(album);
});
```

---

## 统计逻辑

### 商品层级管理设计

```
商品（Product）
├── 主图（Image: isMainImage=true）← 统计入相册数量
└── 详情图（Image: isMainImage=false）← 不统计入相册数量
```

### 统计规则

| 统计项 | 统计方式 | 说明 |
|--------|----------|------|
| 全部图片数量 | 只统计主图 | `isMainImage=true AND deleted=false` |
| 收藏数量 | 只统计主图 | `favorite=true AND isMainImage=true` |
| 最近上传 | 只统计主图 | `createdAt>7天前 AND isMainImage=true` |
| 回收站数量 | 只统计主图 | `deleted=true AND isMainImage=true` |
| 相册图片数量 | 只统计主图 | `albumId=xxx AND isMainImage=true AND deleted=false` |

---

## 测试验证

### SQL验证

**查看相册主图数量**：
```sql
-- 查看每个相册的主图数量
SELECT
    a.name as album_name,
    a.image_count as stored_count,
    COUNT(CASE WHEN i.is_main_image = true AND i.deleted = false THEN 1 END) as main_image_count,
    COUNT(CASE WHEN i.deleted = false THEN 1 END) as total_image_count
FROM albums a
LEFT JOIN images i ON a.id = i.album_id
GROUP BY a.id, a.name, a.image_count
ORDER BY a.sort_order;
```

**预期结果**：
```
album_name | stored_count | main_image_count | total_image_count
-----------|--------------|------------------|------------------
T恤        | 1            | 1                | 1
内衣        | 1            | 1                | 1
抓绒衣      | 1            | 1                | 1
冲锋衣      | 1            | 1                | 1
软壳        | 1            | 1                | 1
```

### 前端验证

**检查statistics计算**：
```javascript
// page.tsx 第963-970行
const albumStats = albums.map(album => {
  const count = mainImages.filter(img => img.albumId === album.id).length;
  return {
    id: album.id,
    name: album.name,
    count,
  };
});
```

**说明**：
- `mainImages`是已经过滤过的主图列表
- 相册统计使用`mainImages`，确保只统计主图数量
- 前端逻辑已经正确，无需修改

---

## 修改文件清单

1. **ImageRepository.java**
   - 新增：`countMainImagesByAlbumId()`方法
   - 功能：统计某个相册的主图数量

2. **ImageServiceImpl.java**
   - 修改：`updateAlbumImageCount()`方法
   - 改进：使用`countMainImagesByAlbumId()`统计主图数量
   - 修改：`initDefaultImages()`方法
   - 改进：使用`countMainImagesByAlbumId()`更新相册数量

---

## 验证步骤

### 1. 重启后端服务
```bash
cd /workspace/projects/backend
bash start.sh
```

### 2. 查看日志
```bash
tail -f /app/work/logs/bypass/app.log
```

### 3. 执行SQL验证
```sql
-- 查看相册统计是否正确
SELECT name, image_count FROM albums ORDER BY sort_order;

-- 查看主图数量
SELECT album_name, COUNT(*) as main_count
FROM images
WHERE is_main_image = true AND deleted = false
GROUP BY album_name;
```

### 4. 前端验证
- 打开图片管理系统
- 查看侧边栏的相册列表
- 确认每个相册显示的数量是主图数量（商品数量）

---

## 预期效果

### 修复前
```
相册名称 | 显示数量 | 实际主图数 | 实际总图片数
---------|---------|----------|-----------
抓绒衣   | 3       | 1        | 3
```

### 修复后
```
相册名称 | 显示数量 | 实际主图数 | 实际总图片数
---------|---------|----------|-----------
抓绒衣   | 1       | 1        | 3
```

---

## 注意事项

1. **数据一致性**：确保数据库中所有图片的`is_main_image`字段正确设置
2. **相册初始化**：重启服务后，预置图片的相册数量会自动更新
3. **新上传图片**：上传图片时会自动更新相册数量（只统计主图）
4. **删除图片**：删除图片时会自动更新相册数量（只统计主图）

---

## 后续优化

1. **性能优化**：如果相册数量很多，可以考虑缓存统计结果
2. **实时更新**：使用数据库触发器自动更新相册数量
3. **批量更新**：添加批量更新相册数量的API接口
