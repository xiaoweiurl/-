# 数字知识库 - 项目规范文档

## 项目概览

这是一款精美的知识库管理系统，采用现代化极简风格设计，支持知识上传、分类、筛选、批量操作、用户认证和权限管理等功能。

### 技术栈
- **框架**: Next.js 16 (App Router)
- **核心**: React 19
- **语言**: TypeScript 5
- **UI 组件**: shadcn/ui (基于 Radix UI)
- **样式**: Tailwind CSS 4
- **图标**: Lucide React
- **认证**: 基于 Session 的用户认证
- **权限**: 基于角色的访问控制 (RBAC)
- **对象存储**: S3 兼容存储 (通过 coze-coding-dev-sdk)
- **AI 识别**: 豆包 Vision 模型 (通过 coze-coding-dev-sdk)

### 后端 API 集成
本项目支持双模式运行：
1. **开发模式（降级模式）**: 当 Java 后端不可用时，自动使用模拟数据，支持基本的登录和数据展示功能
2. **生产模式**: 调用 Java Spring Boot 后端 API，支持完整的数据持久化

通过环境变量 `NEXT_PUBLIC_BACKEND_API_URL` 配置后端 API 地址。系统会自动检测后端可用性，并在后端不可用时切换到降级模式。

### 降级模式说明
当后端服务不可用时，系统会自动启用降级模式：
- **登录**: 使用模拟用户数据，任意用户名密码均可登录
- **会话验证**: 检查本地 Cookie 中的 session_id 即可通过验证
- **数据**: 使用前端内置的 Mock 数据展示

### 核心功能
1. **侧边导航栏**: 知识分类、上传、回收站、收藏夹、文档中心、设置
2. **顶部搜索栏**: 搜索知识、用户头像、通知中心
3. **知识展示**: 网格/瀑布流布局，支持切换
4. **知识预览**: 全屏预览、缩放、旋转、导航
5. **筛选排序**: 按日期、名称、大小排序，支持类型筛选
6. **批量操作**: 多选、移动、下载、删除、收藏
7. **用户认证**: 登录/登出、Session 管理
8. **权限管理**: 管理员/普通用户角色，差异化权限控制
9. **AI 知识识别**: 使用豆包 Vision API 自动分类和标签
10. **批量上传**: 支持一次上传多条知识，自动分类
11. **图片编辑**: 使用 TUI Image Editor 实现独立的图片编辑页面，支持裁剪、旋转、翻转、滤镜、绘图、文字、形状、水印等操作
12. **文档中心**: 支持 PDF、Word、Excel、PPT、压缩包等文档的上传、分类管理和预览

## 项目结构

```
src/
├── app/
│   ├── layout.tsx          # 根布局
│   ├── page.tsx            # 主页面（含权限检查）
│   ├── login/
│   │   └── page.tsx        # 登录页面
│   ├── edit/
│   │   └── [id]/
│   │       └── page.tsx    # 图片编辑页面（使用 TUI Image Editor）
│   ├── api/
│   │   ├── auth/
│   │   │   └── login/
│   │   │       └── route.ts # 登录/登出/检查登录状态 API
│   │   └── users/
│   │       └── route.ts    # 用户管理 API（管理员权限）
│   ├── api-docs/
│   │   └── page.tsx        # Swagger API 文档页面
│   └── globals.css         # 全局样式
├── components/
│   ├── Sidebar.tsx         # 侧边导航栏
│   ├── Header.tsx          # 顶部栏（含用户菜单）
│   ├── ImageCard.tsx       # 知识卡片
│   ├── ImageGrid.tsx       # 知识网格
│   ├── ImagePreview.tsx    # 知识预览
│   ├── FilterPanel.tsx     # 筛选面板
│   ├── BulkActions.tsx     # 批量操作
│   ├── SwaggerDocs.tsx      # Swagger UI 组件
│   └── ui/                 # shadcn/ui 组件库
└── lib/
    ├── utils.ts            # 工具函数
    ├── auth.ts             # 用户认证逻辑和权限配置
    └── swagger.ts          # Swagger 配置
```

## 构建与运行

### 开发环境
```bash
pnpm install              # 安装依赖
pnpm run dev              # 启动开发服务器 (端口 5000)
```

### 生产环境
```bash
pnpm run build            # 构建生产版本
pnpm run start            # 启动生产服务器
```

### 类型检查
```bash
npx tsc --noEmit          # TypeScript 类型检查
```

## 代码风格指南

### 组件命名
- 组件文件使用 PascalCase: `Sidebar.tsx`, `ImageCard.tsx`
- 组件导出使用 default export
- 使用 `'use client'` 指令标记客户端组件

### 样式规范
- 使用 Tailwind CSS 工具类
- 使用 `cn()` 函数合并样式
- 颜色使用 OKLCH 色彩空间
- 圆角统一使用 `rounded-xl` 或 `rounded-2xl`
- 阴影使用 `shadow-sm`, `shadow-lg`, `shadow-2xl`

### 配色方案
- 主色调: 紫色渐变 (`from-violet-500 to-purple-600`)
- 背景: 柔和灰白色 (`slate-50`, `slate-100`)
- 强调色: 紫色 (`violet-700`, `purple-600`)
- 成功色: 绿色 (`green-400`)
- 危险色: 红色 (`red-500`)

### 交互效果
- 过渡动画: `transition-all duration-200` 或 `duration-300`
- 悬停效果: `hover:shadow-lg`, `hover:-translate-y-1`
- 渐变背景: `bg-gradient-to-r from-violet-500/10 to-purple-500/10`

## 关键组件说明

### Sidebar (侧边导航栏)
- 支持展开/折叠菜单
- 显示知识数量统计
- 渐变背景高亮选中项
- 通知徽章显示未读数

### ImageCard (知识卡片)
- 支持网格/瀑布流两种布局
- 悬停显示操作按钮
- 加载状态骨架屏
- 收藏、下载、更多操作

### ImagePreview (知识预览)
- 全屏模态对话框
- 支持缩放 (0.5x - 3x)
- 支持旋转 (90度)
- 左右导航切换知识
- 底部工具栏操作

### FilterPanel (筛选面板)
- 排序: 日期、名称、大小
- 升序/降序切换
- 日期筛选: 全部、今天、本周、本月
- 类型筛选: JPG、PNG、GIF

## 性能优化

1. **知识优化**
   - 使用 Next.js Image 组件自动优化
   - 配置外部图片域名 (images.unsplash.com)
   - 懒加载知识

2. **组件优化**
   - 使用 React.memo 避免不必要的重渲染
   - 使用 useMemo 缓存计算结果
   - 使用 useCallback 缓存回调函数

3. **样式优化**
   - 使用 Tailwind CSS 的 JIT 模式
   - 避免内联样式
   - 使用 CSS 变量定义主题色

4. **分页与无限滚动优化**
   - API 接口支持分页参数 (`page`, `pageSize`, `cursor`)
   - 知识列表支持无限滚动加载
   - 分页组件支持页码导航和每页数量切换
   - 防止知识过多导致查询缓慢和内存占用过高

## 响应式设计

- 移动端: 2列知识网格
- 平板: 3列知识网格
- 桌面: 4-5列知识网格
- 侧边栏在小屏幕可折叠
- 搜索框自适应宽度

## 常见问题

### 知识加载失败
- 检查 next.config.ts 中的 remotePatterns 配置
- 确保图片 URL 可访问
- 检查图片格式是否支持

### 样式不生效
- 检查 Tailwind 类名拼写
- 确认 globals.css 已正确引入
- 清除缓存后重新构建

### 热更新失效
- 检查文件是否正确保存
- 重启开发服务器
- 清除 .next 缓存目录

## 未来优化方向

1. **后端集成**
   - 接入真实的数据库存储知识信息
   - 实现知识上传到对象存储
   - 用户认证与权限管理 ✅（已完成）

## 账户设置 API

### 前端 API 路由
所有用户相关API位于 `src/app/api/user/` 目录：

| 路由 | 方法 | 功能 | 后端对接 |
|------|------|------|----------|
| `/api/user/profile` | GET | 获取用户资料 | GET /api/user |
| `/api/user/profile` | PUT/PATCH | 更新用户资料 | PUT /api/user/profile |
| `/api/user/password` | PUT/PATCH | 修改密码 | PUT /api/user/password |
| `/api/user/settings` | GET | 获取用户设置 | GET /api/user/settings |
| `/api/user/settings` | PUT/PATCH | 更新用户设置 | PUT /api/user/settings |
| `/api/user/avatar` | POST | 上传头像 | POST /api/user/avatar |

### 后端 Java API
后端API位于 `backend/src/main/java/com/imagemanager/controller/UserController.java`：

| 接口 | 方法 | 功能 |
|------|------|------|
| `/api/user` | GET | 获取当前用户信息 |
| `/api/user/stats` | GET | 获取用户统计信息 |
| `/api/user/profile` | PUT/PATCH | 更新用户资料 |
| `/api/user/avatar` | POST | 上传头像 |
| `/api/user/password` | PUT | 修改密码 |
| `/api/user/settings` | GET/PUT/PATCH | 用户设置管理 |
| `/api/user/notifications` | GET | 获取通知列表 |
| `/api/user/notifications/unread-count` | GET | 获取未读通知数 |
| `/api/user/notifications/{id}/read` | POST | 标记通知已读 |
| `/api/user/notifications/read-all` | POST | 全部标记已读 |

### 数据库表结构
数据库初始化脚本：`backend/src/main/resources/schema.sql`

主要表：
- `users` - 用户表
- `user_settings` - 用户设置表
- `albums` - 相册表
- `images` - 图片表
- `notifications` - 通知表

2. **功能增强**
   - 知识标签系统
   - 智能分类管理
   - 知识编辑功能
   - 分享功能

3. **性能提升**
   - 虚拟滚动优化长列表
   - Service Worker 缓存
   - 知识预加载策略

## 用户认证与权限管理

### 用户角色
系统支持两种用户角色：
- **管理员 (admin)**: 拥有所有权限，包括用户管理、系统配置等
- **普通用户 (user)**: 基础权限，可管理自己的知识和分类

### 预置用户账号
| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| admin | Admin@123 | ADMIN | 系统管理员账号 |
| user | User@123 | user | 普通用户账号 |

### 权限配置
```typescript
// src/lib/auth.ts
export const PERMISSIONS = {
  // 知识管理
  UPLOAD_IMAGE: 'upload_image',
  DELETE_OWN_IMAGE: 'delete_own_image',
  DELETE_ANY_IMAGE: 'delete_any_image',
  
  // 分类管理
  CREATE_ALBUM: 'create_album',
  DELETE_OWN_ALBUM: 'delete_own_album',
  DELETE_ANY_ALBUM: 'delete_any_album',
  
  // 用户管理
  MANAGE_USERS: 'manage_users',
  VIEW_ALL_USERS: 'view_all_users',
};

export const ROLE_PERMISSIONS = {
  admin: Object.values(PERMISSIONS), // 管理员拥有所有权限
  user: [
    PERMISSIONS.UPLOAD_IMAGE,
    PERMISSIONS.DELETE_OWN_IMAGE,
    PERMISSIONS.CREATE_ALBUM,
    PERMISSIONS.DELETE_OWN_ALBUM,
  ], // 普通用户仅有基础权限
};
```

### API 端点

#### 认证相关
- `POST /api/auth/login` - 用户登录
- `GET /api/auth/login` - 检查登录状态
- `DELETE /api/auth/login` - 用户登出

## API 文档

### Swagger UI
项目集成了 Swagger UI 用于在线查看和测试 API 接口。

**访问地址**: `/api-docs`

### API 文档功能
- 在线查看所有 API 接口
- 支持在浏览器中直接测试 API 调用
- 完整的请求参数和响应示例
- 基于 OpenAPI 3.0 规范

### 相关文件
- `/src/lib/swagger.ts` - Swagger 配置
- `/src/components/SwaggerDocs.tsx` - Swagger UI 组件
- `/src/app/api-docs/page.tsx` - API 文档页面

#### 用户管理（管理员权限）
- `GET /api/users` - 获取用户列表
- `POST /api/users` - 创建新用户
- `GET /api/admin/users/{id}` - 获取用户详情
- `PUT /api/admin/users/{id}` - 更新用户信息
- `DELETE /api/admin/users/{id}` - 删除用户
- `POST /api/admin/users/{id}/reset-password` - 重置用户密码

#### 知识/图片管理
- `GET /api/images` - 获取知识列表（支持筛选、排序、分页）
  - 参数: `albumId`, `favorites`, `includeDeleted`, `page`, `pageSize`, `sortBy`, `sortOrder`
- `POST /api/images` - 移动知识到分类
- `PATCH /api/images` - 更新知识属性（收藏、删除等）
- `DELETE /api/images` - 批量删除知识
- `POST /api/images/upload` - 上传知识（支持批量、AI分类）
  - 参数: `files[]`, `enableAI`, `album`
  - AI 分类使用豆包 Vision API
- `POST /api/images/batch` - 批量操作（删除、收藏、移动）
- `POST /api/images/batch-download` - 批量下载网络图片
- `GET /api/images/tags` - 获取所有标签列表
- `POST /api/images/classify` - 分类图片
- `GET /api/images/export/{albumId}` - 导出单个相册图片
- `POST /api/images/export/batch` - 批量导出多个相册图片

#### 回收站
- `GET /api/images/trash` - 获取回收站知识列表
- `POST /api/images/trash` - 恢复回收站图片
- `DELETE /api/images/trash` - 清空回收站

#### 文档管理
- `GET /api/documents` - 获取文档列表
  - 参数: `category` (pdf/word/excel/ppt/zip/other/all)
- `POST /api/documents/upload` - 上传单个文档
  - **自动分类**: 上传时如果不指定 category，会根据文件扩展名自动分类（pdf/doc/docx/xls/xlsx/csv/ppt/pptx/zip/rar/7z）
- `POST /api/documents/upload/batch` - 批量上传文档
  - **自动分类**: 批量上传时自动根据每个文件的扩展名进行分类
- `DELETE /api/documents/{id}` - 删除文档
- `GET /api/documents/{id}` - 获取文档详情
- `GET /api/documents/{id}/download` - 获取文档下载链接
- `GET /api/documents/stats` - 获取各分类文档数量统计

#### 数据库表结构
数据库迁移脚本：`backend/src/main/resources/db/migration/V8__documents.sql`

**documents 表**：
- `id` - 文档ID (UUID)
- `name` - 显示名称
- `original_name` - 原始文件名
- `stored_name` - 存储文件名 (UUID.ext)
- `file_path` - 存储路径 (assets/xxx.ext)
- `url` - 访问URL
- `size` - 文件大小
- `content_type` - MIME类型
- `extension` - 扩展名
- `category` - 分类 (pdf/word/excel/ppt/zip/other)
- `user_id` - 用户ID
- `deleted` - 是否删除（软删除）
- `created_at` - 创建时间
- `updated_at` - 更新时间

#### 相册/分类管理
- `GET /api/albums` - 获取相册列表
- `POST /api/albums` - 创建相册
- `PUT /api/albums/{id}` - 更新相册信息
- `DELETE /api/albums/{id}` - 删除相册
- `PUT /api/albums/matching-mode` - 批量更新匹配模式
- `PUT /api/albums/matching-mode/reset` - 重置所有相册匹配模式

#### 用户设置
- `GET /api/user/profile` - 获取用户资料
- `PUT /api/user/profile` - 更新用户资料
- `POST /api/user/avatar` - 上传头像
- `PUT /api/user/password` - 修改密码
- `GET /api/user/settings` - 获取用户设置
- `PUT /api/user/settings` - 更新用户设置

#### 通知
- `GET /api/notifications` - 获取通知列表
- `PATCH /api/notifications` - 通知操作（标记已读、全部已读、清除）

#### 商品管理
- `GET /api/products/main-images` - 获取商品主图列表
- `GET /api/products/{id}` - 获取商品详情
- `GET /api/products/{id}/images` - 获取商品所有图片

#### AI 识别
- `POST /api/ai/recognize` - AI 识别图片
  - 支持关键词匹配和视觉识别（豆包Vision模型）

#### 系统设置
- `GET /api/settings` - 获取系统设置
- `POST /api/settings` - 更新单个设置
- `PUT /api/settings` - 批量更新设置

### 前端权限检查
```typescript
// 在组件中检查用户权限
const isAdmin = currentUser?.role === 'admin';

// 条件渲染管理员功能
{isAdmin && (
  <Button onClick={handleManageUsers}>用户管理</Button>
)}
```

### 登录流程
1. 用户访问登录页面 `/login`
2. 输入用户名和密码
3. 调用 `POST /api/auth/login` 进行认证
4. 认证成功后，Session 中存储用户信息
5. 重定向到主页面 `/`
6. 主页面通过 `GET /api/auth/login` 检查登录状态
7. 未登录用户自动重定向到登录页面

## 安全性

### 安全特性

#### 认证安全
- **安全的Session ID**: 使用 `crypto.randomUUID()` 或哈希生成不可预测的Session ID
- **Session续期**: 支持Session自动续期，防止频繁登录
- **Session数量限制**: 每个用户最多5个并发Session
- **密码强度验证**: 支持密码强度检查（长度、大小写、数字、特殊字符）

#### CSRF保护
- **来源验证**: 验证请求的Origin/Referer头
- **Cookie安全**: Session Cookie仅限HTTP传输

#### 输入验证
- **Schema验证**: 使用Zod进行类型安全的请求验证
- **ID格式验证**: 防止SQL注入和路径遍历
- **速率限制**: 针对不同操作设置合理的请求频率限制
  - 默认: 1分钟100次
  - 上传: 1分钟20次
  - 登录: 5分钟5次
  - 改密: 1小时3次

#### 安全头
- `X-Frame-Options: SAMEORIGIN` - 防止点击劫持
- `X-Content-Type-Options: nosniff` - 防止MIME类型嗅探
- `X-XSS-Protection: 1; mode=block` - XSS过滤
- `Referrer-Policy: strict-origin-when-cross-origin` - 引用来源策略
- `Permissions-Policy` - 功能策略限制

#### 图片域名白名单
- 生产环境仅允许预配置的域名
- 禁止内网地址访问（防SSRF）

### 敏感信息保护
- 日志中自动过滤敏感字段（password, token, secret等）
- 统一日志模块 `src/lib/logger.ts`

### 后端安全实现

#### 安全配置 (`backend/src/main/java/com/imagemanager/config/SecurityConfig.java`)
- **BCrypt 密码加密**: 使用 Spring Security 的 BCryptPasswordEncoder
- **CORS 配置**: 支持跨域请求，配置安全策略
- **Session 管理**: 每个用户最多 5 个并发 Session
- **权限控制**: 基于 Spring Security 的角色权限控制

#### 认证拦截器 (`backend/src/main/java/com/imagemanager/config/AuthInterceptor.java`)
- 验证每个请求的 Session ID
- 自动过期会话清理
- 管理员端点权限检查

#### 速率限制 (`backend/src/main/java/com/imagemanager/util/RateLimiter.java`)
- 登录: 5分钟最多 5 次
- 密码修改: 1小时最多 3 次
- 上传: 1分钟最多 20 次
- 默认: 1分钟最多 100 次

#### 密码强度验证 (`backend/src/main/java/com/imagemanager/util/PasswordValidator.java`)
- 支持 5 个强度等级（弱、中等、良好、强、非常强）
- 评估因素：长度、大小写、数字、特殊字符
- 提供密码改进建议

## 可扩展性

### API基础设施

#### 中间件系统 (`src/lib/api-middleware.ts`)
```typescript
import { withAuth, adminOnly, authOnly } from '@/lib/api-middleware';

// 需要认证的接口
export const GET = withAuth(async (request) => {
  // ...
});

// 仅管理员可访问
export const DELETE = adminOnly(async (request) => {
  // ...
});
```

#### Schema验证 (`src/lib/api-schemas.ts`)
```typescript
import { loginSchema, imageQuerySchema } from '@/lib/api-schemas';
import { validateRequest, validateQuery } from '@/lib/api-schemas';

// 在路由中使用
export async function POST(request: Request) {
  const validation = await validateRequest(loginSchema, request);
  if (!validation.success) return validation.response;
  
  const { username, password } = validation.data;
  // ...
}
```

#### 统一错误处理 (`src/lib/api-utils.ts`)
```typescript
import { APIError, handleAPIError, checkRateLimit } from '@/lib/api-utils';

export async function handler() {
  // 速率限制
  const rateLimit = checkRateLimit(clientIP, 'upload');
  if (!rateLimit.allowed) {
    return errorResponse('请求过于频繁', 429);
  }
  
  // 抛出统一错误
  throw new APIError('操作失败', 400, 'OPERATION_FAILED');
}
```

### 日志系统 (`src/lib/logger.ts`)
```typescript
import { createLogger, withRequestLog } from '@/lib/logger';

const logger = createLogger('api');

// 自动记录请求，自动过滤敏感信息
const result = await withRequestLog('api', {
  method: 'POST',
  path: '/api/images/upload',
  userId: '123',
}, handler);

// 直接使用
logger.info('用户登录成功', { username });
logger.error('上传失败', error, { albumId });
```

### 核心库文件

| 文件 | 功能 |
|------|------|
| `src/lib/auth.ts` | 认证、Session、权限管理 |
| `src/lib/api-utils.ts` | 安全检查、速率限制、输入验证 |
| `src/lib/api-middleware.ts` | API中间件、路由保护 |
| `src/lib/api-schemas.ts` | Zod验证Schema、类型定义 |
| `src/lib/backend-proxy.ts` | 后端API代理 |
| `src/lib/logger.ts` | 结构化日志、敏感信息过滤 |
| `src/lib/swagger.ts` | Swagger/OpenAPI配置 |
