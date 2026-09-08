# 盈云产品智能中台 - 项目规范文档

## 项目概览

这是一款精美的知识库管理系统，采用现代化极简风格设计，支持知识上传、分类、筛选、批量操作、用户认证和权限管理等功能。同时包含供应链/工厂管理模块，支持智能报价和供应商对比。

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
- **AI 识别**: qwen3.6:35b 多模态能力 (本地部署)

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
9. **AI 知识识别**: 使用 qwen3.6:35b 多模态能力自动分类和标签
10. **批量上传**: 支持一次上传多条知识，自动分类
11. **图片编辑**: 使用 TUI Image Editor 实现独立的图片编辑页面，支持裁剪、旋转、翻转、滤镜、绘图、文字、形状、水印等操作
12. **文档中心**: 支持 PDF、Word、Excel、PPT、压缩包等文档的上传、分类管理和预览
13. **供应链管理**: 产品报价、原料入库、原料采购、生产计划、辅料采购的完整数据管理
14. **智能报价**: 基于原料用量×采购最低价自动计算总成本和建议报价
15. **供应商对比**: 按原料编码汇总供应商报价，展示最低价/最高价/节省比例
16. **商品库**: 文件夹式商品管理（货号+品名命名），图片（主图/侧面图/细节/产品图）上传至 OSS，支持卖点/竞品/功能/对应人群/使用场景备注

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
│   │   ├── knowledge/
│   │   │   └── [[...path]]/route.ts  # 知识库 API 代理 (/api/knowledge/docs/*)
│   │   ├── memory/
│   │   │   └── [[...path]]/route.ts  # 记忆库 API 代理 (/api/memory/*)
│   │   └── users/
│   │       └── route.ts    # 用户管理 API（管理员权限）
│   ├── knowledge/
│   │   └── page.tsx        # 知识库页面（独立文档管理、分类、向量化状态）
│   ├── memory/
│   │   └── page.tsx        # 记忆库页面（RAG 语义搜索 + AI 对话）
│   ├── chat/
│   │   └── page.tsx        # AI 对话页面
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

### 配色方案（iOS 原生设计）
- 主色: iOS 系统蓝 `#007AFF`（仅选中态/主按钮/关键强调）
- 功能色: 成功 `#34C759` / 警告 `#FF9500` / 错误 `#FF3B30` / 辅助 `#AF52DE`
- 背景: 全局 `#F2F2F7`，卡片 `#FFFFFF`
- 文字: 一级 `#1C1C1E` / 二级 `#3A3A3C` / 辅助 `#8E8E93`
- 分割线: `#E5E5EA`（1px）
- 详细规范见 `DESIGN.md`

### 交互效果
- 过渡动画: `transition-all duration-200` 或 `duration-300`
- 悬停效果: 卡片上浮 3px + 阴影加深
- 按钮点击: 轻微缩放反馈
- **禁止**: 渐变背景、深色混搭、发光效果、高饱和色

## 关键组件说明

### Sidebar (侧边导航栏)
- 支持展开/折叠菜单
- 显示知识数量统计
- iOS 选中态：蓝底白字圆角高亮
- 毛玻璃容器：`ios-glass`（blur 20px + rgba(255,255,255,0.72)）
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

#### 记忆库（Memory）表
数据库迁移脚本：`backend/src/main/resources/db/migration/V16__create_memory_knowledge_base.sql`

记忆库用于 AI 对话时的 RAG 知识检索，支持文档上传、自动切片、向量化存储：

| 表名 | 说明 |
|------|------|
| `knowledge_domains` | 知识域（如"时尚知识库"） |
| `knowledge_cards` | 知识卡片（手动创建 + 文档自动切片生成） |
| `knowledge_documents` | 上传的原始文档元数据 |
| `knowledge_embeddings` | 文档切片的向量嵌入（bge-m3 本地模型） |

#### 知识库（KnowledgeBase）表
数据库迁移脚本：`backend/src/main/resources/db/migration/V18__create_knowledge_base.sql`

知识库是独立的文件知识管理系统，与记忆库物理隔离：

| 表名 | 说明 |
|------|------|
| `knowledge_base_categories` | 知识库分类 |
| `knowledge_base_docs` | 知识库文档（上传的 PDF/Word/Excel/TXT） |

#### 知识库向量化（V20）
数据库迁移脚本：`backend/src/main/resources/db/migration/V20__knowledge_base_embedding.sql`

知识库文档支持自动向量化，与记忆库共用 `knowledge_embeddings` 表，通过 `source_type` 区分来源：

| 字段 | 说明 |
|------|------|
| `knowledge_base_docs.chunk_count` | 文档切片数量 |
| `knowledge_base_docs.embedding_status` | 向量化状态：PENDING/PROCESSING/COMPLETED/FAILED/SKIPPED |
| `knowledge_base_docs.file_content` | 提取的文本内容 |
| `knowledge_embeddings.source_type` | 来源类型：MEMORY(记忆库) / KNOWLEDGE_BASE(知识库) |
| `knowledge_embeddings.source_doc_id` | 知识库文档ID（KNOWLEDGE_BASE时有效） |

**向量化流程**：
1. 上传文本类文件（PDF/Word/Excel/TXT/Markdown）
2. 后台异步提取文本、切片（800字符/片，100字符重叠）
3. 调用 bge-m3 本地模型获取向量
4. 存入 `knowledge_embeddings`（source_type='KNOWLEDGE_BASE'）
5. 前端文档列表显示向量化状态标签

**删除清理**：删除知识库文档时，同步删除 `knowledge_embeddings` 中 source_type='KNOWLEDGE_BASE' 且 source_doc_id 匹配的记录。

**RAG检索**：AI对话时，`SmartChatServiceImpl` 同时调用 `MemoryService.search`（记忆库）和 `KnowledgeBaseService.search`（知识库），合并结果作为上下文。

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

### 用户角色（三级权限）
系统支持三种用户角色：
- **超级管理员 (superadmin)**: 最高权限，不受密码重置限制
- **管理员 (admin)**: 拥有管理权限，可访问 ERP 数据同步页；**不能重置其他管理员/超级管理员的密码**（本人除外）
- **普通用户 (user)**: 基础权限，可管理自己的知识和分类

### 预置用户账号（种子账号）
种子账号**仅在 local 环境播种**，密码从环境变量读取，未配置则跳过创建；**首次登录强制改密**（`users.must_change_password`，V59 迁移）。

| 用户名 | 角色 | 密码环境变量 | 说明 |
|--------|------|------|------|
| superadmin | superadmin | `SEED_SUPERADMIN_PASSWORD` | 超级管理员账号 |
| admin | admin | `SEED_ADMIN_PASSWORD` | 系统管理员账号 |
| user | user | `SEED_USER_PASSWORD` | 普通用户账号 |

- 本地默认密码在 `backend/src/main/resources/application-local.yml`（已被 .gitignore 排除，禁止提交）
- 生产环境不播种，账号由管理员手工创建
- 改密链路：`mustChangePassword=true` → 登录页跳转 `/settings?tab=security&forceChange=1` → 改密成功清标志并强制重新登录
- 管理员重置密码后同样标记强制改密（`UserServiceImpl.resetPassword`）

### 安全架构（2026-09 安全审计整改）
- **凭据管理**：`application.yml` 中 `DATABASE_PASSWORD`/`ERP_PASSWORD` 无默认值，非本地环境必须环境变量注入；本地默认值在 `application-local.yml`（gitignored）
- **CORS**：白名单制（`app.cors.allowed-origins`，默认 `http://localhost:5000`），`allowCredentials=true` 时禁止通配符；重复配置类 `CorsConfig.java` 已删除，`AuthController` 内硬编码 CORS 头已全部移除（统一由 `SecurityConfig.corsConfigurationSource` 输出）
- **会话传递**：仅接受 `X-Session-Id` 请求头 / Cookie / `Authorization: Bearer`；**禁止 URL 查询参数传 session_id**（`AuthInterceptor.extractSessionId` 已移除该兜底）
- **CSRF**：保持关闭 —— 鉴权依赖自定义头 `X-Session-Id`（跨站向量无法附加），Cookie 仅同站通道且 `SameSite=Lax`，详见 `SecurityConfig` 注释
- **上传目录**：`/uploads/**` 不再匿名公开（`SecurityConfig`/`AuthInterceptor`/`WebMvcConfig` 三处均已移除放行），浏览器同站 `<img>` 自动携带 Cookie 不受影响
- **运维接口**：`/fix/**`、`/ops/**`、`/audit/**`、`/backup/**` 需 ADMIN 角色（`SecurityConfig` + 类级 `@PreAuthorize` 双重保护）；`DataFixController` 另有功能开关 `app.datafix.enabled`（默认 false，`DATAFIX_ENABLED` 环境变量控制，关闭时 Bean 不注册）
- **日志脱敏**：sessionId 仅打印前 8 位；`AuthController` 登录成功日志、Next 代理 `Set-Cookie` 日志均已脱敏；种子账号明文密码日志已删除
- **角色映射**：`SessionIdAuthFilter` 中 admin 与 superadmin 均映射 `ROLE_ADMIN`（修复 superadmin 被降级为 ROLE_USER 的 bug）

### 权限配置
```typescript
// src/lib/auth.ts
export type UserRole = 'user' | 'admin' | 'superadmin';

// 角色能力配置对象（superadmin / admin / user 三键）
export const PERMISSIONS = {
  superadmin: { canUpload: true, canDelete: true, ..., canErpSync: true },
  admin:      { canUpload: true, canDelete: true, ..., canErpSync: true },
  user:       { canUpload: true, canDelete: false, ..., canErpSync: false },
} as const;

// 三级权限辅助函数
isAdminOrAbove(role)       // 是否管理员及以上（ERP 同步页可见性）
isSuperAdmin(role)         // 是否超级管理员
roleDisplayName(role)      // 角色显示名（超级管理员/管理员/普通用户）
canResetPasswordOf(operatorRole, operatorId, targetRole, targetId)
// 规则：superadmin 不限；admin 不能改其他 admin/superadmin 密码（本人除外）
```

**后端对应实现**：
- `AdminController.resetPassword`：X-Session-Id 解析操作者，非 superadmin 且目标为 admin/superadmin 且非本人 → 403
- `AuthServiceImpl.initDefaultData`：仅 local 环境播种，密码来自环境变量（见上文"预置用户账号"）
- 前端 `users/page.tsx`：角色三级徽章 + 重置密码按钮按 `canResetPasswordOf` 禁用

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
  - AI 分类使用 qwen3.6:35b 多模态能力
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

#### 记忆库（Memory）API
记忆库用于 AI 对话时的 RAG 知识检索，与知识库完全独立：

- `GET /api/memory/domains` - 获取知识域列表
- `POST /api/memory/domains` - 创建知识域
- `GET /api/memory/cards` - 获取知识卡片列表（按 domain）
- `POST /api/memory/cards` - 创建知识卡片
- `DELETE /api/memory/cards/{id}` - 删除知识卡片
- `POST /api/memory/upload` - 上传文档（自动切片、向量化）
- `GET /api/memory/documents` - 获取文档列表
- `DELETE /api/memory/documents/{id}` - 删除文档
- `GET /api/memory/search` - 语义搜索（向量检索）
- `POST /api/memory/chat` - AI 对话（基于 RAG）
- `GET /api/memory/chat/history` - 获取对话历史
- `DELETE /api/memory/chat/history` - 清空对话历史

#### 知识库（KnowledgeBase）API
知识库是独立的文件知识管理系统，与记忆库物理隔离：

- `GET /api/knowledge/docs` - 获取知识库文档列表
  - 参数: `categoryId`, `keyword`, `page`, `size`
- `POST /api/knowledge/docs` - 创建文本/URL 知识文档
- `POST /api/knowledge/upload` - 上传文件（PDF/Word/Excel/TXT）
- `GET /api/knowledge/docs/search` - 关键词搜索
  - 参数: `keyword`, `page`, `size`
- `DELETE /api/knowledge/docs/{id}` - 删除知识文档（同时删除向量记录）
- `GET /api/knowledge/search` - 向量语义搜索（查询知识库 embeddings）
  - 参数: `q` (查询文本), `minScore` (默认0.25), `limit` (默认5)
- `GET /api/knowledge/categories` - 获取分类列表
- `POST /api/knowledge/categories` - 创建分类

#### 知识批量导入 API（200G 级，流式直写 Milvus）
业务员资料批量导入：流式遍历 zip/文件夹，逐条目读取解析（PDF/Word/Excel/CSV/TXT/Markdown/图片OCR），切片后向量化直写 Milvus，不落 pgvector，不占堆内存。

- `POST /api/knowledge/import/path` - 按服务器路径导入（zip 或文件夹）
  - Body: `{"path": "/data/salesperson.zip"}`
- `POST /api/knowledge/import/upload` - 上传 zip 导入（multipart file 字段）
- `GET /api/knowledge/import/progress/{taskId}` - 查询进度（文件数/失败数/切片数/最近错误）
- `POST /api/knowledge/import/cancel/{taskId}` - 取消任务
- `GET /api/knowledge/import/tasks?limit=20` - 任务列表

**架构要点**（`KnowledgeImportService.java`）：
- 三级流水线：生产者（流式读 zip/目录）→ 解析池（解析+切片+批量向量化）→ 单写线程（攒批写 Milvus）
- 背压：有界缓冲队列（默认 2048 行）+ in-flight 信号量（默认 16 文件），内存占用恒定
- 幂等：doc_id = 文件内容 SHA-256 前 32 位，重导自动覆盖旧向量
- 嵌套 zip 递归处理（最深 3 层），条目边读边落临时文件（磁盘缓冲）
- OCR 用 qwen3.6:35b 多模态，信号量限流（默认 4 并发）
- 配置在 `application.yml` 的 `knowledge-import` 段（parse-threads/embed-batch-size/milvus-batch-size 等）

**Milvus Collection**（`salesperson_chunks`，启动时自动创建）：
- `chunk_id` Int64 自增主键
- `doc_id` VarChar(64) - 文件内容哈希
- `file_name` VarChar(1024) - zip 内虚拟路径（TRIE 索引）
- `doc_type` VarChar(16) - pdf/excel/word/txt/image（TRIE 索引）
- `chunk_index` Int32
- `content` VarChar(8192)
- `embedding` FloatVector(1024) - HNSW 索引（M=16, efConstruction=200, COSINE）

**导入任务表**（V43）：`knowledge_import_task`（进度）、`knowledge_import_error`（失败明细）

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

#### 历史订单（供应链）
已审核投入生产的销售订单展示，Java 后端（`HistoryOrderController` + `HistoryOrderServiceImpl`：JdbcTemplate 只读查询）+ Next.js 通配代理（`/api/history-orders/[[...path]]` → `/history-orders/*`）：

- `GET /api/history-orders` - 分页列表（固定只查 state='1' 已审核订单）
  - 参数: `page`, `size`, `keyword`(单号/业务单号/客户/货号/业务员), `zxtate`(0未审核/1已复审/其他终审), `sfplan`(是/否), `dateFrom`, `dateTo`, `sortField`(zhdate/jh_date/sl_sum/dh), `sortOrder`
  - 品名：LEFT JOIN 内衣工艺单（按货号 DISTINCT ON 去重取 spname）
  - 业务员空值：返回 `ywynameText` = "（业务员数据未维护）"
- `GET /api/history-orders/stats` - 统计（已审核订单数/数量合计/客户数/已下计划数/本月新增）
- `GET /api/history-orders/{dh}` - 订单详情（按单号）

**状态值**（order_xs_list 表注释）：`state`: 0→编辑、1→审核、其他→待审核；`zxtate`(执行状态): 0→未审核、1→已复审、其他→已经终审

**前端页面**：`/supply-chain/history-orders`（统计卡片+筛选+表格+分页+详情弹窗），入口在供应链主页 Tab 导航末尾

#### 商品管理
- `GET /api/products/main-images` - 获取商品主图列表
- `GET /api/products/{id}` - 获取商品详情
- `GET /api/products/{id}/images` - 获取商品所有图片

#### ERP 数据同步（仅管理员及以上）
外部 ERP（`http://mpro42.ywhzsoft.com/netWf2024Unitive_Ent/`）增量同步，Java 后端（`ErpSyncController` + `ErpSyncServiceImpl` + `ErpAuthServiceImpl` + `ErpClient` + `ErpProperties` + `ErpDataPersister`）+ Next.js 通配代理（`/api/erp-sync/[[...path]]` → `/erp-sync/*`）：

- `POST /api/erp-sync/login` - ERP 登录（body 可为空，凭证固定后端配置 → token 缓存服务端）
- `GET /api/erp-sync/auth-state` - 登录态（loggedIn/uid/loginTime/demo/baseUrl，不返 token）
- `POST /api/erp-sync/logout` - 登出清除 token
- `GET /api/erp-sync/status` - 同步状态概览（7 模块游标 + summary 汇总）
- `POST /api/erp-sync/sync/{moduleKey}` - 单模块增量同步（数据库最新时间→当前时间，业务数据落库）
- `POST /api/erp-sync/sync-all` - 全部模块串行同步
- `GET /api/erp-sync/logs?limit=50` / `DELETE /api/erp-sync/logs` - 日志查询/清空

**接口规范**：统一响应 `{code, message, result}`（1 成功 / 0 失败 / -100 鉴权失败 / -101 超时 / -200 无效账套码）；业务接口 Header 带 `Authorization: Bearer {token}`。

**凭证固定配置（用户要求定死，无需手动输入）**：`erp.uid=88888` / `erp.password=123` / `erp.custom-id=8D7C1BDE-C05F-4A11-BD96-D71D94D35633`；同步时 `ensureToken()` 自动登录换取 token；token 失效自动重登重试一次；登录接口独立地址 `Auth/checkLogin.aspx`（postman 实测路径）。

**业务数据「仅新增同步」落库（ErpDataPersister，分批独立事务）**：
- 核心规则：拉取 ERP 全量数据与本地表匹配比对，**仅插入匹配失败的新增数据；已匹配存量数据不做任何修改/更新**；重复执行幂等
- 有主键表：`order_xs_list`(PK dh) / `order_jfk_gongyidan`(PK bh) / `order_sw_gongyidan`(PK bh) → `INSERT ... ON CONFLICT (pk) DO NOTHING`（主键唯一索引匹配）
- 无唯一约束表（`order_buj_component` / `order_gongxu_process` / `order_gongxu_price` / `raw_material_warehouse`）→ V58 md5(业务键) 表达式索引（规避多列 varchar(500) 组合索引超 2704 字节上限），分批 `WHERE md5(...) IN (...)` 一次查询走索引批量比对，差集即新增；Java md5 计算与 SQL 表达式规则严格一致（UTF-8 小写 hex + `COALESCE(TRIM(col::text),'')` 以 IMMUTABLE 的 `||` 连接，Java 侧 null 键段按 "" 参与、取值带 trim——已实测含 integer 列/空格/null 混合场景两侧 md5 完全一致）
- ⚠️ 索引表达式陷阱：`concat_ws` 是 STABLE 函数不能用于索引（报"索引表达式中函数必需标记为 IMMUTABLE"），必须用 `||`(textcat)；TRIM/md5 是 IMMUTABLE，COALESCE 是表达式非函数
- ⚠️ 参数类型陷阱：varchar 列必须 `str()`（BigDecimal/Integer setObject 到 varchar 报类型错误）；int4/numeric 列可 str()（PG 隐式转换）或 decimal()/integer()；`order_buj_component.zbj/zs=int4`、`tongjing/kez/xjtime/llcl=numeric`；`order_gongxu_process` 仅 yongl/tims=numeric、sort=int4，zhenju~sline/yongl2 均为 varchar
- ⚠️ 参数类型陷阱：varchar 列必须 `str()`（BigDecimal/Integer setObject 到 varchar 报类型错误）；int4/numeric 列可 str()（PG 隐式转换）或 decimal()/integer()；`order_buj_component.zbj/zs=int4`、`tongjing/kez/xjtime/llcl=numeric`；`order_gongxu_process` 仅 yongl/tims=numeric、sort=int4，zhenju~sline/yongl2 均为 varchar
- 业务键：部件=hhname+color+chima+buj+zbj+jix；工序=hhname+wtname+jizhong+zhenju+zhenhao+zhenmu；工价=hhname+wtname；原料=huohao+color+size+component+material_name+specification+batch_no（原料本地维护字段 unit_price 等因"不更新"天然受保护）
- 事务控制：每批 `erp.sync-batch-size`（默认1000）独立 TransactionTemplate 事务；某批失败仅回滚当前批、记录失败明细（message 中"失败批次：批次N(X条)失败: 原因"），状态置 `partial`；批内业务键去重保幂等
- 杜绝 N+1：PK 表纯批量 INSERT；无约束表每批 1 次 IN 查询 + 1 次 batchUpdate
- state/zxtate 落库存中文文本（与 HistoryOrder 查询 `state='审核'` 实际口径一致，表注释 0/1 与实际数据不符）
- 同步状态/日志写库（persistSyncResult/clearLogs）同样 TransactionTemplate 事务（HikariCP auto-commit=false 陷阱）
- PersistResult 语义：inserted（实际插入）/ skipped（已存在跳过+批内重复）/ failed（失败批次条数）/ total（ERP 返回总数）

**7 个同步模块**（接口文档共 7 个业务接口，全部覆盖；报价单 order_bjd_query 在已提供的 4 份文档中无对应查询接口）：
| 模块 key | ERP 接口 | 落库表 | 时间过滤 |
|---|---|---|---|
| orders | `OrderPrice/getOrdeListQuery` | order_xs_list (PK dh) | 支持 dates/datee |
| neiyi-gongyidan | `Technology/NGyMainQuery` | order_jfk_gongyidan (PK bh) | 不支持 |
| siwa-gongyidan | `Technology/SGyMainQuery` | order_sw_gongyidan (PK bh) | 不支持 |
| gongyi-bujian | `Technology/NGyBujQuery` | order_buj_component | 不支持 |
| gongyi-gongxu | `Technology/NGyWorkTypeQuery` | order_gongxu_process | 不支持 |
| gongxu-gongjia | `Technology/NGyHuohaoPriceQuery` | order_gongxu_price | 不支持 |
| yuanliao-bom | `Material/MaterialYLQuery` | raw_material_warehouse | 不支持 |

`ErpProperties.resolveApiUrl` 自动补 `.aspx` 后缀；工艺类 ERP 端不支持时间过滤，本地游标记增量（落库幂等）。

**配置**（`application.yml` erp 段，全部环境变量可覆盖）：`erp.base-url` / `erp.login-url` / `erp.login-path` / `erp.uid` / `erp.password` / `erp.custom-id` / `erp.timeout` / `erp.demo-enabled`（默认 false 真实优先，ERP 不可达自动降级演示：只记条数不污染业务表）。

**数据表**（V57）：`erp_sync_state`（module_key PK + last_sync_time 游标 + total_records + last_status）、`erp_sync_log`（同步明细：sync_type/range_start/range_end/added/failed/status/duration_ms/source）。

**前端页面**：`/erp-sync`（权限守卫非管理员显示无权限页 + ERP 连接卡〔固定凭证一键连接，无登录表单〕+ 4 概览卡片 + 7 模块列表单模块同步 + 全部同步进度 + 日志表格清空），入口在供应链主页 TABS 区（仅 `isAdminOrAbove` 可见）。

#### 三单据统计（报价单/销售单/工艺单）
供应链主页「单据概览」Tab 数据源，Java 后端（`DocumentStatsController` + `DocumentStatsServiceImpl`：JdbcTemplate）+ Next.js 通配代理（`/api/document-stats/[[...path]]` → `/document-stats/*`）：

- `GET /api/document-stats/overview` - 6 概览卡片（quotationAmount/quotationCount/salesQuantity/salesOrderCount/gongyidanCount/passRate + 月度环比）
- `GET /api/document-stats/trend?days=30` - 报价金额&销售数量双系列趋势（按 zhdate 聚合 MM-DD；quotation 系列字段 amount，sales 系列字段 quantity）
- `GET /api/document-stats/gongyidan-status` - 工艺单按 hhtype 分布（空归"未分类"）
- `GET /api/document-stats/recent-quotations` - 最近报价单（dh/date/customer/huohao/spname/saleprice/cost/passRate）
- `GET /api/document-stats/recent-gongyidan` - 最近工艺单（bh/hhtype/huohao/spname + bomCount/machineCount/processCount/priceCount 四个关联子查询）

**数据口径**：⚠️ sl_sum 是数量合计字段不是金额——销售单**没有金额字段**，销售指标一律用销售数量 SUM(sl_sum)；报价金额=SUM(order_bjd_query.saleprice)；合格率=AVG(zpl) 兼容 0-1/0-100；工艺单关联=jfk.huohao=buj/gxp/gxpr.hhname=raw_material_warehouse.huohao。

**前端组件**：`src/components/DocumentStatsDashboard.tsx`（6 卡片 + 手绘 SVG 双折线趋势图 + 环形图 + 两个最近列表）。供应链主页已移除原 6 个业务 Tab（智能报价/产品报价/原料入库/原料采购/生产计划/辅料采购），仅保留「AI 对话」与「单据概览」。

#### 商品库（Goods Library）
文件夹式商品管理，Java 后端（`GoodsLibraryController` + `GoodsLibraryServiceImpl`：JdbcTemplate + FileStorageService）+ Next.js 通配代理（`/api/goods-library/[[...path]]` → `/goods-library/*`）：

- `GET /api/goods-library` - 获取商品文件夹列表（含主图签名 URL 作封面，keyword 模糊搜索）
- `POST /api/goods-library` - 创建商品文件夹（发起人/打样员/品名/货号/客户/订单号，均可空；文件夹名=货号+品名）
- `GET /api/goods-library/{id}` - 获取商品详情（含四类图片签名 URL）
- `PUT /api/goods-library/{id}` - 更新信息/备注（货号或品名变更时文件夹自动重命名）
- `DELETE /api/goods-library/{id}` - 删除商品（同步删除 OSS 图片）
- `POST /api/goods-library/{id}/images` - 上传图片（multipart：slot=main/side/detail/product + file，替换时自动删旧图）
- `DELETE /api/goods-library/{id}/images?slot={slot}` - 删除指定槽位图片

**数据表** `goods_library`（迁移脚本 V53）：第一层信息字段 + main/side/detail/product 四个 OSS key + 单个备注字段 `remark`（自由文本，可填写卖点/竞品/功能/对应人群/使用场景等）

**⚠️ 事务陷阱（重要）**：`application.yml` 中 HikariCP `auto-commit: false`，无 Spring 事务时 JdbcTemplate 写操作会被连接池回滚（INSERT 看似成功实际未落库）。本模块所有写库操作使用 `TransactionTemplate` 编程式事务（OSS 网络调用留在事务外）；`AiCallLogService.record` 使用 `@Transactional(REQUIRES_NEW)` 独立事务。

**OSS 键规范**：`goods-library/{文件夹名安全形式}/{slot}.{ext}`（FileStorageService 扩展方法 `uploadFileForKey` 支持指定完整 key；中文按 S3 字符规范安全替换）

**前端页面**：`/goods-library`（文件夹网格，封面=主图）、`/goods-library/{id}`（图片管理+信息+备注）

#### AI 识别
- `POST /api/ai/recognize` - AI 识别图片
  - 支持关键词匹配和视觉识别（qwen3.6:35b 多模态）

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
