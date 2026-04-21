# 内衣相册显示问题调试指南

## 问题现象
- 点击"全部图片"：能正常显示商品主图 ✅
- 点击"内衣相册"：显示不出来 ❌

## 问题分析

### 发现的问题

**Bug：`onlyMainImage` 参数未传递到后端**

在 `src/lib/backend-proxy.ts` 中，`imageApi.list` 方法的参数类型定义中**缺少** `onlyMainImage` 参数，导致该参数无法传递到后端。

### 影响范围

虽然后端在查询相册时（`request.getAlbumId() != null`）已经强制查询主图，但在某些情况下可能导致查询逻辑不一致。

### 已修复

✅ 已在 `src/lib/backend-proxy.ts` 中添加 `onlyMainImage` 参数支持

## 调试步骤

### 1. 前端调试（浏览器控制台）

打开浏览器开发者工具（F12），点击 Console 标签，然后：

1. 点击"全部图片"，查看日志：
   ```
   [Home] fetchImages 调用: { page: 1, append: false, activeMenuItem: "all", filterState: {...} }
   [Home] 请求URL: /api/products/main-images?page=1&pageSize=40&includeDeleted=false
   [Home] API 响应: { success: true, data: {...} }
   [Home] 解析后的图片列表: 27 张
   ```

2. 点击"内衣"相册，查看日志：
   ```
   [Home] fetchImages 调用: { page: 1, append: false, activeMenuItem: "album-underwear", filterState: {...} }
   [Home] 请求URL: /api/images?albumId=album-underwear&onlyMainImage=true&page=1&pageSize=40
   [Home] API 响应: { success: true, data: {...} }
   [Home] 解析后的图片列表: ? 张
   ```

**关键检查点**：
- 请求URL中的 `albumId` 参数是否为 `album-underwear`
- API响应的 `success` 是否为 `true`
- 解析后的图片列表数量是否为 7

### 2. 后端调试（日志文件）

查看后端日志文件：
```bash
tail -f /app/work/logs/bypass/backend.log
```

点击"内衣"相册时，应该看到类似日志：
```
查询图片列表：ImageQueryRequest(...)
查询相册，albumId=album-underwear
通过 albumId 查询到Product数量：7
Product: id=prod-101, name=Icebreaker 美利奴羊毛内衣, albumId=album-underwear, category=内衣, userId=user-1
...
查询的productIds: [prod-101, prod-102, prod-103, prod-104, prod-105, prod-106, prod-107]
开始查询这些商品的主图...
查询到的Image数量：7
Image详情: id=img-101, productId=prod-101, isMainImage=true, deleted=false, albumId=album-underwear, albumName=内衣, ...
...
```

**关键检查点**：
- `通过 albumId 查询到Product数量` 是否为 7
- `查询的productIds` 是否包含 7 个 product ID
- `查询到的Image数量` 是否为 7

### 3. 数据库验证

连接到您的本地数据库，执行以下 SQL：

```sql
-- 1. 检查内衣相册的商品
SELECT
    p.id as product_id,
    p.name as product_name,
    p.album_id,
    p.cover_image_id,
    p.image_count
FROM products p
WHERE p.album_id = 'album-underwear'
ORDER BY p.name;

-- 2. 检查这些商品的主图
SELECT
    i.id as image_id,
    i.product_id,
    i.title,
    i.url,
    i.is_main_image,
    i.deleted
FROM images i
WHERE i.product_id IN (
    SELECT id FROM products WHERE album_id = 'album-underwear'
)
AND i.is_main_image = true
AND i.deleted = false
ORDER BY i.display_order;

-- 3. 检查关联是否正确
SELECT
    p.id as product_id,
    p.name as product_name,
    p.album_id as product_album_id,
    i.id as image_id,
    i.product_id as image_product_id,
    i.album_id as image_album_id,
    i.is_main_image,
    CASE
        WHEN p.id = i.product_id THEN '✅ 关联正确'
        ELSE '❌ 关联错误'
    END as status
FROM products p
LEFT JOIN images i ON p.id = i.product_id AND i.is_main_image = true
WHERE p.album_id = 'album-underwear';
```

**关键检查点**：
- 商品数量是否为 7
- 主图数量是否为 7
- `product_album_id` 和 `image_product_id` 的关联是否正确
- 状态是否全部为 `✅ 关联正确`

## 可能的问题原因

### 原因1：数据关联错误

如果数据库中的 `products.album_id` 和 `images.product_id` 关联不正确，会导致查询失败。

**解决方案**：
```sql
-- 检查并修复数据关联
UPDATE images i
SET product_id = p.id
FROM products p
WHERE p.album_id = 'album-underwear'
AND i.album_id = 'album-underwear'
AND i.is_main_image = true;
```

### 原因2：后端服务未运行

如果后端服务未运行，前端会进入降级模式，使用 `allImages` 缓存数据。

**检查方法**：
```bash
# 检查后端服务是否运行
ps aux | grep java | grep -v grep

# 或者检查端口
ss -tuln | grep 8080
```

### 原因3：环境变量配置错误

后端数据库连接配置可能不正确。

**检查方法**：
```bash
# 检查环境变量
env | grep -E "(DATABASE|POSTGRES|PG_)"
```

## 代码修复总结

### 已修复的代码

**文件**：`src/lib/backend-proxy.ts`

**修改内容**：
```typescript
async list(params: {
  page?: number;
  size?: number;
  pageSize?: number;
  albumId?: string;
  favorite?: boolean;
  deleted?: boolean;
  sortBy?: string;
  sortOrder?: string;
  search?: string;
  keyword?: string;
  tag?: string;
  onlyMainImage?: boolean;  // ✅ 新增
} = {}, cookie?: string): Promise<Response> {
  const queryString = buildQueryString({
    page: params.page || 1,
    pageSize: params.pageSize || params.size || 40,
    albumId: params.albumId,
    favorite: params.favorite,
    deleted: params.deleted,
    sortBy: params.sortBy,
    sortOrder: params.sortOrder,
    keyword: params.search || params.keyword,
    tag: params.tag,
    onlyMainImage: params.onlyMainImage,  // ✅ 新增
  });
```

### 编译状态

✅ 前端编译成功
✅ 后端编译成功

## 下一步行动

1. **重新编译前端**：
   ```bash
   npx next build
   ```

2. **检查浏览器控制台**：
   - 打开开发者工具
   - 点击"内衣"相册
   - 查看日志输出

3. **检查后端日志**：
   ```bash
   tail -f /app/work/logs/bypass/backend.log
   ```

4. **验证数据库数据**：
   - 执行上述 SQL 验证查询
   - 检查数据关联是否正确

5. **如果后端未运行**：
   - 检查数据库连接配置
   - 启动后端服务
   - 验证 API 是否可用

## 预期结果

修复后，点击"内衣"相册应该：

1. 前端发送请求：`GET /api/images?albumId=album-underwear&onlyMainImage=true&page=1&pageSize=40`
2. 后端查询逻辑：
   - 查询 `products` 表：`WHERE album_id = 'album-underwear'`（7条记录）
   - 提取 `product_ids`：`['prod-101', 'prod-102', ..., 'prod-107']`
   - 查询 `images` 表：`WHERE product_id IN (...) AND is_main_image = true`（7条记录）
3. 返回结果：7张内衣主图
4. 前端显示：7张内衣商品图片

## 仍然无法解决？

如果按照上述步骤仍然无法解决问题，请提供以下信息：

1. **浏览器控制台日志**（点击"内衣"相册时的完整日志）
2. **后端日志**（点击"内衣"相册时的完整日志）
3. **数据库查询结果**（执行上述SQL的输出）
4. **网络请求信息**（浏览器Network标签中的请求详情）
