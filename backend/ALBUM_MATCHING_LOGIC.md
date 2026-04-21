# 相册匹配逻辑修改说明

## 修改内容

### 1. 修改方法名
- **旧方法**：`createNewAlbum(String albumName)`
- **新方法**：`findOrMatchAlbum(String albumName)`

### 2. 修改逻辑

#### 旧逻辑（创建新相册）
```java
// 生成随机相册ID
String albumId = "album-" + UUID.randomUUID().toString().substring(0, 8);

// 创建新相册并保存到数据库
Album album = Album.builder()
        .id(albumId)
        .name(albumName)
        .description("自动创建的相册")
        .build();
album = albumRepository.save(album);
```

#### 新逻辑（匹配已有相册）
```java
// 1. 精确匹配相册名称
Album exactMatch = albumRepository.findByName(albumName).orElse(null);
if (exactMatch != null) {
    return exactMatch;
}

// 2. 模糊匹配：检查相册名称是否包含关键词
for (Album album : allAlbums) {
    if (album.getName().contains(albumName)) {
        return album;
    }
    // 检查相册名称是否被关键词包含
    if (albumName.contains(album.getName())) {
        return album;
    }
}

// 3. 未找到匹配的相册，返回null
return null;
```

### 3. 调用位置修改

#### 位置1：单张图片上传
```java
// 旧代码
if (result.shouldCreateNewAlbum()) {
    Album newAlbum = createNewAlbum(result.getSuggestedAlbumName());
    finalAlbumId = newAlbum.getId();
    finalAlbumName = newAlbum.getName();
}

// 新代码
if (result.shouldCreateNewAlbum()) {
    Album matchedAlbum = findOrMatchAlbum(result.getSuggestedAlbumName());
    if (matchedAlbum != null) {
        finalAlbumId = matchedAlbum.getId();
        finalAlbumName = matchedAlbum.getName();
        classifyMethod = "auto-matched";
    } else {
        // 不创建新相册，也不分配相册
        classifyMethod = "unmatched";
    }
}
```

#### 位置2：批量图片上传
```java
// 旧代码
if (result.shouldCreateNewAlbum()) {
    log.info("批量上传 - 自动创建新相册: {}", result.getSuggestedAlbumName());
    Album newAlbum = createNewAlbum(result.getSuggestedAlbumName());
    finalAlbumId = newAlbum.getId();
    finalAlbumName = newAlbum.getName();
}

// 新代码
if (result.shouldCreateNewAlbum()) {
    log.info("批量上传 - 尝试根据名称匹配已有相册: {}", result.getSuggestedAlbumName());
    Album matchedAlbum = findOrMatchAlbum(result.getSuggestedAlbumName());
    if (matchedAlbum != null) {
        finalAlbumId = matchedAlbum.getId();
        finalAlbumName = matchedAlbum.getName();
        classifyMethod = "auto-matched";
        log.info("批量上传 - 成功匹配到已有相册: ID={}, 名称={}", finalAlbumId, finalAlbumName);
    } else {
        log.warn("批量上传 - 未找到匹配的相册，跳过相册分配: {}", result.getSuggestedAlbumName());
        classifyMethod = "unmatched";
    }
}
```

### 4. 匹配规则

#### 精确匹配
- 如果相册名称完全相同，直接返回该相册

#### 模糊匹配
- 如果已有相册名称包含输入名称，返回该相册
- 例如：输入"T恤"，数据库中有"短袖T恤"，会匹配成功

#### 反向模糊匹配
- 如果输入名称包含已有相册名称，返回该相册
- 例如：输入"抓绒衣保暖"，数据库中有"抓绒衣"，会匹配成功

#### 未匹配
- 如果没有匹配到任何相册，返回 null
- 图片不会被分配到任何相册

### 5. 保留的功能

以下功能保持不变：
- 用户手动创建相册：`AlbumServiceImpl.createAlbum()`
- Excel 批量下载时的相册匹配：`batchDownloadImages()`
  - 已使用正确的匹配逻辑，无需修改

### 6. 日志记录

添加了详细的日志记录：
```java
log.info("尝试匹配已有相册: {}", albumName);
log.info("精确匹配到相册: ID={}, 名称={}", album.getId(), album.getName());
log.info("模糊匹配到相册: ID={}, 原名称={}, 匹配名称={}", ...);
log.warn("未找到匹配的相册: {}", albumName);
```

## 测试建议

### 测试场景1：精确匹配
1. 数据库中有相册：T恤
2. 上传图片，AI识别为"T恤"
3. 期望：匹配成功，图片分配到"album-tshirt"

### 测试场景2：模糊匹配
1. 数据库中有相册：抓绒衣
2. 上传图片，AI识别为"抓绒衣保暖"
3. 期望：匹配成功，图片分配到"album-fleece"

### 测试场景3：未匹配
1. 数据库中只有5个预置相册
2. 上传图片，AI识别为"背包"
3. 期望：匹配失败，图片不分配相册

### 测试场景4：批量上传
1. 上传多张图片，AI识别为不同的分类
2. 期望：根据匹配结果分配相册，未匹配的不分配相册

## 修改的文件

- `backend/src/main/java/com/imagemanager/service/impl/ImageServiceImpl.java`
  - 修改方法：`createNewAlbum()` → `findOrMatchAlbum()`
  - 修改调用位置：2处（单张上传、批量上传）

## 注意事项

1. 不再自动创建新相册，只匹配已有相册
2. 匹配失败时，图片不会分配到任何相册
3. 用户手动创建相册功能不受影响
4. Excel 批量下载功能使用独立的匹配逻辑，不受影响
