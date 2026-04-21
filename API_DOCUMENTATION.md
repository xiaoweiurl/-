# 图片管理系统 - 接口文档

## 业务逻辑说明

**核心原则：只返回主图，不返回详情图**

- **商品层级管理**：一个商品包含1张主图和多张详情图
- **主图定义**：`is_main_image = true` 的图片
- **详情图定义**：`is_main_image = false` 的图片
- **显示规则**：列表页只显示主图，点击主图后在预览中查看该商品的所有图片（主图+详情图）

---

## 1. 查询全部图片

**用途**：点击"全部图片"时，显示所有商品的主图

### 接口信息
- **路径**：`GET /api/products/main-images`
- **描述**：获取所有商品的主图列表

### 请求参数
| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| page | Integer | 否 | 页码，默认1 | 1 |
| pageSize | Integer | 否 | 每页大小，默认20 | 20 |
| category | String | 否 | 分类筛选（T恤、内衣等） | "内衣" |
| keyword | String | 否 | 搜索关键词 | "Patagonia" |

### 请求示例
```bash
# 获取全部主图
curl -X GET "http://localhost:5000/api/products/main-images?page=1&pageSize=20"

# 按分类筛选
curl -X GET "http://localhost:5000/api/products/main-images?category=内衣"

# 搜索
curl -X GET "http://localhost:5000/api/products/main-images?keyword=Patagonia"
```

### 返回示例
```json
{
  "success": true,
  "data": {
    "list": [
      {
        "id": "img-101",
        "productId": "prod-101",
        "title": "Icebreaker 美利奴羊毛内衣",
        "url": "/assets/icebreaker-underwear.png",
        "isMainImage": true,
        "deleted": false,
        "albumId": "album-underwear",
        "albumName": "内衣"
      }
    ],
    "total": 27,
    "page": 1,
    "pageSize": 20
  }
}
```

### 后端实现逻辑
```java
@GetMapping("/main-images")
public ApiResponse<PageResponse<Image>> getMainImages(...) {
    if (keyword != null) {
        // 按商品名称搜索
        List<Product> products = productRepository.searchByName("user-1", keyword);
        List<String> productIds = products.stream().map(Product::getId).collect(Collectors.toList());
        result = imageRepository.findByProductIdInAndIsMainImageAndDeleted(productIds, true, false, pageRequest);
    } else if (category != null) {
        // 按分类筛选
        List<Product> products = productRepository.findByUserIdAndCategory("user-1", category);
        List<String> productIds = products.stream().map(Product::getId).collect(Collectors.toList());
        result = imageRepository.findByProductIdInAndIsMainImageAndDeleted(productIds, true, false, pageRequest);
    } else {
        // 获取所有主图
        result = imageRepository.findByIsMainImageAndDeleted(true, false, pageRequest);
    }
    return ApiResponse.success(response);
}
```

---

## 2. 查询相册图片

**用途**：点击相册（内衣、T恤、抓绒衣等）时，显示该相册下所有商品的主图

### 接口信息
- **路径**：`GET /api/images`
- **描述**：按条件查询图片列表

### 请求参数
| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| albumId | String | 否 | 相册ID | "album-underwear" |
| onlyMainImage | Boolean | 否 | 是否只返回主图 | true |
| page | Integer | 否 | 页码，默认1 | 1 |
| pageSize | Integer | 否 | 每页大小，默认20 | 20 |

### 请求示例
```bash
# 查询内衣相册的主图
curl -X GET "http://localhost:5000/api/images?albumId=album-underwear&onlyMainImage=true"

# 查询T恤相册的主图
curl -X GET "http://localhost:5000/api/images?albumId=album-tshirt&onlyMainImage=true"
```

### 返回示例
```json
{
  "success": true,
  "data": {
    "list": [
      {
        "id": "img-101",
        "productId": "prod-101",
        "title": "Icebreaker 美利奴羊毛内衣",
        "url": "/assets/icebreaker-underwear.png",
        "isMainImage": true,
        "deleted": false,
        "albumId": "album-underwear",
        "albumName": "内衣"
      }
    ],
    "total": 7,
    "page": 1,
    "pageSize": 20
  }
}
```

### 后端实现逻辑
```java
public PageResponse<Image> queryImages(ImageQueryRequest request) {
    if (request.getAlbumId() != null) {
        // 1. 通过 Product.album_id 查询该相册下的商品
        List<Product> productsByAlbumId = productRepository.findByAlbumId(albumId);
        List<String> productIds = productsByAlbumId.stream().map(Product::getId).collect(Collectors.toList());

        // 2. 查询这些商品的主图
        imagePage = imageRepository.findByProductIdInAndIsMainImageAndDeleted(
            productIds, true, false, pageable
        );
    }
    return PageResponse.of(...);
}
```

---

## 3. 查询商品所有图片

**用途**：点击主图预览时，加载该商品的所有图片（主图+详情图）

### 接口信息
- **路径**：`GET /api/products/{productId}/images`
- **描述**：根据商品ID获取该商品的所有图片

### 请求参数
| 参数名 | 类型 | 必填 | 说明 | 示例 |
|--------|------|------|------|------|
| productId | String | 是 | 商品ID（路径参数） | "prod-101" |

### 请求示例
```bash
# 获取商品的所有图片（主图+详情图）
curl -X GET "http://localhost:5000/api/products/prod-101/images"
```

### 返回示例
```json
{
  "success": true,
  "data": [
    {
      "id": "img-101",
      "productId": "prod-101",
      "title": "Icebreaker 美利奴羊毛内衣",
      "url": "/assets/icebreaker-main.png",
      "isMainImage": true,
      "displayOrder": 0,
      "deleted": false
    },
    {
      "id": "img-102",
      "productId": "prod-101",
      "title": "Icebreaker 美利奴羊毛内衣_详情1",
      "url": "/assets/icebreaker-detail1.png",
      "isMainImage": false,
      "displayOrder": 1,
      "deleted": false
    },
    {
      "id": "img-103",
      "productId": "prod-101",
      "title": "Icebreaker 美利奴羊毛内衣_详情2",
      "url": "/assets/icebreaker-detail2.png",
      "isMainImage": false,
      "displayOrder": 2,
      "deleted": false
    }
  ]
}
```

### 后端实现逻辑
```java
@GetMapping("/{productId}/images")
public ApiResponse<List<Image>> getProductImages(@PathVariable String productId) {
    // 查询该商品的所有图片，按 display_order 排序
    List<Image> images = imageRepository.findByProductIdAndDeletedOrderByDisplayOrderAsc(productId, false);
    return ApiResponse.success(images);
}
```

---

## 4. 统计数字

### 统计规则
- **全部图片**：统计所有 `is_main_image = true` 且 `deleted = false` 的图片数量
- **相册数量**：统计各相册下主图的数量
- **收藏数量**：统计 `favorite = true` 的主图数量
- **最近上传**：统计7天内上传的主图数量
- **回收站**：统计 `deleted = true` 的主图数量

### 前端实现
```typescript
const statistics = React.useMemo(() => {
  // 只统计主图（商品），不统计详情图
  const mainImages = allImages.filter(img => img.isMainImage && !img.deleted);

  // 全部图片数（只统计主图）
  const allCount = mainImages.length;

  // 各相册图片数量 - 只统计主图
  const albumStats = albums.map(album => {
    const count = mainImages.filter(img => img.albumId === album.id).length;
    return { id: album.id, name: album.name, count };
  });

  return { allCount, albumStats, ... };
}, [allImages, albums]);
```

### 当前数据（示例）
| 分类 | 主图数量 |
|------|----------|
| 全部图片 | 27 |
| T恤 | 7 |
| 内衣 | 7 |
| 抓绒衣 | 5 |
| 冲锋衣 | 4 |
| 软壳 | 4 |

---

## 5. 前端调用逻辑

### 点击不同菜单项的API调用

```typescript
const fetchImages = async (page: number = 1) => {
  let apiUrl = '';

  if (activeMenuItem === 'all') {
    // 点击"全部图片"
    apiUrl = `/api/products/main-images?page=${page}&pageSize=${pageSize}`;
  } else if (activeMenuItem.startsWith('album-')) {
    // 点击相册（如内衣、T恤等）
    apiUrl = `/api/images?albumId=${activeMenuItem}&onlyMainImage=true&page=${page}&pageSize=${pageSize}`;
  } else if (activeMenuItem === 'favorites') {
    // 点击收藏夹
    apiUrl = `/api/images?favorite=true&page=${page}&pageSize=${pageSize}`;
  } else if (activeMenuItem === 'recent') {
    // 点击最近上传
    apiUrl = `/api/images/recent?page=${page}&pageSize=${pageSize}`;
  } else if (activeMenuItem === 'trash') {
    // 点击回收站
    apiUrl = `/api/images/trash?page=${page}&pageSize=${pageSize}`;
  }

  const response = await fetch(apiUrl);
  const result = await response.json();
  setImages(result.data.list);
};
```

### 点击主图预览时的逻辑

```typescript
// 在 ImagePreview 组件中
React.useEffect(() => {
  if (productId) {
    // 加载该商品的所有图片（主图+详情图）
    loadProductImages(productId);
  }
}, [productId]);

const loadProductImages = async (productId: string) => {
  const response = await fetch(`/api/products/${productId}/images`);
  const result = await response.json();
  setProductImages(result.data); // 该商品的所有图片
};
```

---

## 数据库表关系

### products 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar | 商品ID |
| name | varchar | 商品名称 |
| album_id | varchar | 相册ID |
| cover_image_id | varchar | 封面图ID（主图ID） |
| image_count | integer | 图片总数（主图+详情图） |

### images 表
| 字段 | 类型 | 说明 |
|------|------|------|
| id | varchar | 图片ID |
| product_id | varchar | 商品ID |
| is_main_image | boolean | 是否主图 |
| display_order | integer | 显示顺序 |
| deleted | boolean | 是否删除 |

### 关系
- 一个商品（`products`）对应多张图片（`images`）
- 主图：`is_main_image = true`（每个商品只有1张主图）
- 详情图：`is_main_image = false`（每个商品可能有0或多张详情图）
- 列表页只显示主图
- 预览页显示该商品的所有图片（主图+详情图）

---

## 验证SQL

### 验证主图数量
```sql
-- 查询各相册的主图数量
SELECT
    p.album_id,
    COUNT(DISTINCT CASE WHEN i.is_main_image = true AND i.deleted = false THEN i.id END) as main_image_count
FROM products p
LEFT JOIN images i ON p.id = i.product_id
WHERE p.album_id IN ('album-underwear', 'album-tshirt', 'album-fleece', 'album-jacket', 'album-softshell')
GROUP BY p.album_id;
```

### 验证商品与主图关系
```sql
-- 查询某商品的所有图片
SELECT
    i.id,
    i.title,
    i.is_main_image,
    i.display_order,
    i.deleted
FROM images i
WHERE i.product_id = 'prod-101'
ORDER BY i.display_order;
```

---

## 总结

### 核心逻辑
1. **列表页**：只显示主图（`is_main_image = true` 且 `deleted = false`）
2. **相册查询**：通过 `Product.album_id` → `Product.id` → `Image.product_id` 查询主图
3. **预览页**：通过 `productId` 加载该商品的所有图片（主图+详情图）
4. **统计数字**：只统计主图数量

### API映射
| 用户操作 | 前端API | 后端实现 |
|----------|---------|----------|
| 点击"全部图片" | `GET /api/products/main-images` | 查询所有主图 |
| 点击相册 | `GET /api/images?albumId=xxx&onlyMainImage=true` | 查询相册主图 |
| 点击主图预览 | `GET /api/products/{productId}/images` | 查询商品所有图片 |
