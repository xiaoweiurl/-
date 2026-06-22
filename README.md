# 企业数智中台系统

企业级知识管理与智能中台系统，支持多品牌（宝娜斯/盈云）、知识库管理、岗位知识卡片、AI 智能对话、供应链管理、智能报价等核心功能。

## 技术栈

| 层级 | 技术 |
|------|------|
| **前端框架** | Next.js 16 (App Router) + React 19 |
| **语言** | TypeScript 5 |
| **UI 组件** | shadcn/ui (Radix UI) |
| **样式** | Tailwind CSS 4 |
| **图标** | Lucide React |
| **后端框架** | Spring Boot 3 + Java 17 |
| **数据库** | PostgreSQL + pgvector (向量扩展) |
| **认证** | 基于 Session 的用户认证 + RBAC 权限 |
| **对象存储** | S3 兼容存储 |
| **AI 模型** | 豆包 Vision (图像识别) + MiniMax Embedding (向量化) |
| **包管理** | pnpm (前端) + Maven (后端) |

## 快速开始

### 前端开发

```bash
pnpm install          # 安装依赖
pnpm run dev          # 启动开发服务器 (端口 5000)
```

### 后端开发

```bash
cd backend
./mvnw spring-boot:run   # 启动 Spring Boot (端口 8080)
```

### 环境变量

| 变量名 | 说明 | 示例 |
|--------|------|------|
| `NEXT_PUBLIC_BACKEND_API_URL` | 后端 API 地址 | `http://localhost:8080` |
| `DEPLOY_RUN_PORT` | 服务监听端口 | `5000` |
| `COZE_PROJECT_DOMAIN_DEFAULT` | 对外访问域名 | `https://xxx.coze.site` |

### 降级模式

当后端服务不可用时，前端自动切换到降级模式：
- 登录：任意用户名密码可登录
- 数据：使用前端内置 Mock 数据展示

## 系统架构

```
┌─────────────────────────────────────────────────────┐
│                     前端 (Next.js)                    │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐      │
│  │知识库│ │岗位  │ │AI对话│ │供应链│ │营销  │      │
│  │管理  │ │卡片  │ │      │ │管理  │ │AI    │      │
│  └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘      │
│     └────────┴────────┴────────┴────────┘           │
│              Next.js API Routes (代理层)              │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP
┌──────────────────────┴──────────────────────────────┐
│                  后端 (Spring Boot)                    │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐      │
│  │Auth  │ │知识库│ │岗位  │ │供应链│ │AI对话│      │
│  │认证  │ │服务  │ │卡片  │ │服务  │ │服务  │      │
│  └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘      │
│     └────────┴────────┴────────┴────────┘           │
│              JPA Repository + Service                 │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────┴──────────────────────────────┐
│              PostgreSQL + pgvector                     │
│  用户/知识/文档/岗位卡片/向量嵌入/供应链/对话历史       │
└─────────────────────────────────────────────────────┘
```

## 核心功能

### 1. 多品牌体系

系统支持双品牌独立运营，登录时绑定公司后不可更改：

| 品牌 | 主色调 | 侧边栏色 | 图标 |
|------|--------|----------|------|
| 盈云 | 紫色 (violet) | 紫色系 | Cloud |
| 宝娜斯 | 玫红 (rose) | 玫红系 | Scissors |

- 品牌配置：`src/lib/brand.ts`
- 公司绑定后不可更改，后端校验 `WHERE company IS NULL OR company = ''`
- 团队/部门按公司区分展示（如"产品开发(盈云)"、"针织技术(宝娜斯)"）

### 2. 知识库管理

- **文档上传**：支持 PDF、Word、Excel、TXT、Markdown 文件上传
- **自动分类**：上传时根据文件扩展名自动归类
- **向量化检索**：文本自动切片 (800字符/片) + MiniMax Embedding 向量化
- **公司隔离**：数据按公司隔离，同公司用户共享数据
- **分类管理**：支持多级分类创建和管理

### 3. 岗位知识卡片

按标准化模板录入岗位信息，8 大模块全部必填：

| 模块 | 字段 |
|------|------|
| 岗位基本信息 | 岗位名称、人员姓名、工号、所属部门、所属团队、岗位性质 |
| 岗位职责 | 核心职责、日常工作频率 |
| 关键产出物 | 关键产出物、交付标准 |
| 能力要求 | 硬技能、软技能 |
| 协作关系 | 上游输入方、下游输出方 |
| 当前状态 | 已完成工作、进行中工作、瓶颈与困难、需要的支持 |
| 改进计划 | 改进方向、流程优化建议、工具/资源需求 |
| 补充说明 | 补充信息 |

- **自动向量化**：创建/更新卡片时自动向量化，状态标签实时显示（已向量化/向量化失败/处理中/待向量化）
- **AI 对话检索**：岗位意图自动识别，优先检索岗位卡片数据

### 4. AI 智能对话

多源智能检索，SSE 流式输出，Markdown 渲染：

**检索优先级**：
1. 供应链精确数据（产品报价、原料采购等）
2. 岗位知识卡片（向量语义检索）
3. 记忆库（向量语义检索）
4. 知识库 PDF 文档（向量语义检索）

**意图识别**：
- **供应链意图**：检测报价/成本/采购等关键词 → 精确查询业务表，跳过向量检索
- **岗位意图**：检测岗位/职责/入职等关键词 → 优先岗位卡片，有结果时跳过 PDF 检索
- **通用意图**：全源检索，综合回答

### 5. 供应链管理

| 模块 | 功能 |
|------|------|
| 产品报价 | 产品成本、建议报价管理 |
| 原料入库 | 原料库存、入库记录 |
| 原料采购 | 采购订单、供应商管理 |
| 生产计划 | 生产排期、进度跟踪 |
| 辅料采购 | 辅料采购管理 |
| 供应商对比 | 按原料编码汇总供应商报价，展示最低价/最高价/节省比例 |

**智能报价**：基于原料用量 × 采购最低价自动计算总成本和建议报价。

### 6. 营销 AI

独立的营销 AI 对话模块，支持 SSE 流式输出和 Markdown 渲染，对话历史按公司隔离持久化到数据库。

### 7. 用户认证与权限

| 角色 | 权限 |
|------|------|
| 管理员 (admin) | 全部权限，含用户管理、系统配置 |
| 普通用户 (user) | 基础权限，管理自己的知识和分类 |

**预置账号**：

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | Admin@123 | ADMIN |
| user | User@123 | user |

### 8. 文档中心

支持 PDF、Word、Excel、PPT、压缩包等文档的上传、分类管理和预览，上传时自动根据扩展名分类。

## 项目结构

### 前端

```
src/
├── app/                          # Next.js App Router
│   ├── layout.tsx               # 根布局
│   ├── page.tsx                 # 主页面（含权限检查）
│   ├── login/page.tsx           # 登录页（分屏布局 + 品牌展示）
│   ├── knowledge/page.tsx       # 知识库（文档 + 岗位卡片 Tab）
│   ├── chat/page.tsx            # AI 对话页
│   ├── marketing/page.tsx       # 营销 AI 页
│   ├── supply-chain/page.tsx    # 供应链管理页
│   ├── memory/page.tsx          # 记忆库页（已隐藏入口）
│   ├── settings/                # 系统设置
│   ├── user-settings/           # 用户设置
│   ├── users/                   # 用户管理（管理员）
│   ├── api/                     # API 路由（代理转发到后端）
│   │   ├── auth/                # 认证 API
│   │   ├── knowledge/           # 知识库 + 岗位卡片 API
│   │   ├── chat/                # AI 对话 API
│   │   ├── marketing/           # 营销 AI API
│   │   ├── supply-chain/        # 供应链 API
│   │   ├── images/              # 图片管理 API
│   │   ├── documents/           # 文档管理 API
│   │   ├── albums/              # 相册/分类 API
│   │   └── users/               # 用户管理 API
│   └── globals.css              # 全局样式
├── components/                   # React 组件
│   ├── Sidebar.tsx              # 侧边导航栏（白色主题 + 品牌动态切换）
│   ├── Header.tsx               # 顶部栏（面包屑 + 用户菜单）
│   ├── MarkdownRenderer.tsx     # Markdown 渲染（AI 输出）
│   ├── KnowledgeCardForm.tsx    # 岗位知识卡片表单（8 模块）
│   ├── KnowledgeCardList.tsx    # 岗位知识卡片列表（含向量化状态标签）
│   ├── ImageCard.tsx            # 知识卡片
│   ├── ImageGrid.tsx            # 知识网格
│   ├── ImagePreview.tsx         # 知识预览
│   ├── FilterPanel.tsx          # 筛选面板
│   ├── DocumentManager.tsx      # 文档管理
│   ├── SwaggerDocs.tsx          # Swagger UI 组件
│   └── ui/                      # shadcn/ui 组件库
└── lib/                          # 工具库
    ├── auth.ts                  # 认证逻辑 + 权限配置
    ├── brand.ts                 # 双品牌配置（宝娜斯/盈云）
    ├── api-middleware.ts        # API 中间件（认证、限流）
    ├── api-schemas.ts           # Zod 验证 Schema
    ├── api-utils.ts             # 统一错误处理 + 速率限制
    ├── backend-proxy.ts         # 后端 API 代理
    ├── logger.ts                # 结构化日志
    ├── swagger.ts               # Swagger/OpenAPI 配置
    └── utils.ts                 # 工具函数
```

### 后端

```
backend/src/main/java/com/imagemanager/
├── ImageManagerApplication.java         # 启动类
├── config/                              # 配置
│   ├── SecurityConfig.java             # Spring Security + BCrypt + CORS
│   └── AuthInterceptor.java            # 认证拦截器
├── controller/                          # REST 控制器
│   ├── AuthController.java             # 认证（登录/登出/绑定公司）
│   ├── KnowledgeBaseController.java    # 知识库（按公司隔离）
│   ├── PositionKnowledgeCardController.java  # 岗位知识卡片
│   ├── ChatController.java             # AI 对话（SSE 流式）
│   ├── MarketingChatController.java    # 营销 AI 对话
│   ├── SupplyChainController.java      # 供应链管理
│   ├── DocumentController.java         # 文档管理
│   ├── AlbumController.java            # 相册/分类
│   ├── UserController.java             # 用户管理
│   ├── AIController.java               # AI 识别
│   └── ProductController.java          # 商品管理
├── entity/                              # JPA 实体
│   ├── User.java                       # 用户
│   ├── PositionKnowledgeCard.java      # 岗位知识卡片
│   ├── KnowledgeBaseDoc.java           # 知识库文档
│   ├── KnowledgeEmbedding.java         # 向量嵌入
│   ├── ProductQuotation.java           # 产品报价
│   ├── RawMaterialPurchase.java        # 原料采购
│   ├── RawMaterialWarehouse.java       # 原料入库
│   ├── ProductionPlan.java             # 生产计划
│   ├── AccessoryPurchase.java          # 辅料采购
│   └── ...
├── service/                             # 业务服务
│   ├── impl/
│   │   ├── SmartChatServiceImpl.java   # AI 对话核心（多源检索 + 意图识别）
│   │   ├── KnowledgeBaseServiceImpl.java  # 知识库（向量化 + 公司隔离）
│   │   ├── PositionKnowledgeCardServiceImpl.java  # 岗位卡片（自动向量化）
│   │   └── ...
│   └── ...
├── repository/                          # JPA Repository
└── util/                                # 工具类
    ├── RateLimiter.java                # 速率限制
    └── PasswordValidator.java          # 密码强度验证

backend/src/main/resources/
├── application.yml                      # 应用配置
└── db/migration/                        # Flyway 数据库迁移
    ├── V21__create_user_sessions.sql   # 用户会话表
    ├── V22__add_company_field.sql      # 公司字段
    ├── V28__create_position_knowledge_cards.sql  # 岗位知识卡片表
    └── V29__add_position_card_embedding_status.sql  # 向量化状态字段
```

## 数据库设计

### 核心数据表

| 表名 | 说明 | 隔离方式 |
|------|------|----------|
| `users` | 用户表 | company |
| `position_knowledge_cards` | 岗位知识卡片 | company |
| `knowledge_base_docs` | 知识库文档 | company |
| `knowledge_base_categories` | 知识库分类 | company |
| `knowledge_embeddings` | 向量嵌入 (pgvector) | source_type + company |
| `knowledge_domains` | 记忆库知识域 | company |
| `knowledge_cards` | 记忆库知识卡片 | company |
| `product_quotation` | 产品报价 | company |
| `raw_material_purchase` | 原料采购 | company |
| `raw_material_warehouse` | 原料入库 | company |
| `production_plan` | 生产计划 | company |
| `accessory_purchase` | 辅料采购 | company |
| `smart_chat_history` | AI 对话历史 | company |
| `marketing_chat_history` | 营销对话历史 | company |
| `albums` | 相册/分类 | company + user_id |
| `images` | 图片 | company + user_id |
| `documents` | 文档 | company + user_id |

### 向量化说明

`knowledge_embeddings` 表通过 `source_type` 区分来源：

| source_type | 说明 |
|-------------|------|
| MEMORY | 记忆库文档切片 |
| KNOWLEDGE_BASE | 知识库文档切片 |
| POSITION_CARD | 岗位知识卡片切片 |

切片规则：800 字符/片，100 字符重叠，使用 MiniMax Embedding API 向量化。

## 安全特性

- **BCrypt 密码加密**
- **Session 管理**：每用户最多 5 个并发 Session，支持自动续期
- **CSRF 保护**：Origin/Referer 验证
- **速率限制**：登录 5次/5分钟，上传 20次/分钟，改密 3次/小时
- **输入验证**：Zod Schema 验证 + ID 格式校验
- **安全头**：X-Frame-Options、X-Content-Type-Options、X-XSS-Protection
- **敏感信息过滤**：日志自动过滤 password、token、secret 等字段
- **图片域名白名单**：生产环境限制图片加载域名

## API 文档

启动服务后访问 `/api-docs` 查看 Swagger UI，支持在线调试所有 API 接口。

## 构建与部署

```bash
# 前端
pnpm install              # 安装依赖
pnpm run build            # 构建生产版本
pnpm run start            # 启动生产服务器

# 后端
cd backend
./mvnw package            # 构建 JAR
java -jar target/*.jar    # 启动生产服务

# 类型检查
npx tsc --noEmit          # TypeScript 类型检查
```
