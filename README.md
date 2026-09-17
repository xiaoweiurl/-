# 盈云产品智能中台

面向服装/纺织行业的数据智能平台。解决的核心问题：企业内部知识库文档（PDF/Word/Excel）和供应链业务数据（报价/采购/库存/生产计划）分散在多个系统，需要一个统一的 AI 对话入口来检索和查询。

## 技术栈

**前端**：Next.js 16 (App Router) + React 19 + TypeScript + shadcn/ui + Tailwind CSS 4
**后端**：Java Spring Boot（独立服务，前端通过 API 代理调用）
**数据库**：PostgreSQL + pgvector（向量存储）+ pg_trgm（模糊搜索加速）
**AI 模型**（全部本地部署）：
- 主对话 + 图片识别：qwen3.6:35b（多模态，SSE 流式，同时承担对话和图片理解）
- 查询增强：Ollama (qwen3.6:35b)
- 文档重排序：bge-reranker-v2-m3（独立部署，HTTP 接口）
- 文本向量化：bge-m3（本地部署）

**存储**：S3 兼容对象存储（coze-coding-dev-sdk）
**认证**：Session-based + RBAC（自研，非 Spring Security）

前端和后端是独立仓库，前端通过 `NEXT_PUBLIC_BACKEND_API_URL` 环境变量配置后端地址，后端不可用时自动降级到 Mock 数据。

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

## 核心实现与难点

### 1. RAG 多策略检索管线

用户提问后，系统需要从两个数据源检索：知识库文档（上传的 PDF/Word 切片后的向量）和供应链业务表（结构化的报价/采购/库存数据）。

**问题**：纯向量检索对精确货号（如 M1TT403）召回率很低。embedding 相似度经常低于阈值，导致明明数据库有数据但检索返回空。QueryEnhancer 用 Ollama 生成查询变体时会把货号改写为"该型号产品"，丢失精确匹配能力。

**方案**：4 层降级策略，按优先级依次尝试：

1. **直接 SQL 关键词匹配**：用 KeywordExtractor 提取货号，`ILIKE '%M1TT403%'` 精确命中。零超时风险，绕过所有向量计算。
2. **RagPipeline 向量检索**：Ollama 生成 3 个查询变体 → 4 路并行向量召回（单路 1s 超时）→ 去重 → bge-reranker 重排序取 top-5。适合模糊语义问题。
3. **LangChain4j Text-to-SQL**：让 LLM 根据用户问题生成 SQL 查询业务表。适合"哪些供应商做染色加工"这类结构化查询。
4. **业务表关键词兜底**：对产品报价、原料采购、原料入库、生产计划、辅料采购 5 张表做关键词搜索。

上下文构建器按优先级拼接结果（供应链数据 > 岗位卡片 > 知识库片段 > 历史 QA > 图片搜索），根据意图标签自动追加不同指令后缀，通过 SSE 流式返回。

**取舍**：RagPipeline 的超时设置（单路 1s，整体 1.5s）是实测后的折中值。Ollama 查询增强本身可能超过 1s，所以第一层直接 SQL 搜索是必要的快速通道。

### 2. 向量搜索 SQL 性能优化

知识库文档量增长后，主搜索 SQL 出现性能问题。

**问题**：
- 向量距离 `1 - (e.embedding <=> ?)` 在 SELECT、WHERE、ORDER BY 中重复计算 3 次
- `ILIKE '%keyword%'` 在 chunk_text、title、file_name、file_content 4 个字段上做全表扫描
- LEFT JOIN knowledge_base_docs 和 knowledge_base_categories 在所有行上执行，而不是只在候选集上

**方案**：

改用 CTE 分两步执行：
```sql
WITH candidates AS (
    -- 第一步：关键词过滤 + 向量距离计算（只算 1 次）
    SELECT e.id, e.chunk_text, 1-(e.embedding <=> ?) AS score, ...
    FROM knowledge_embeddings e
    WHERE source_type = 'KNOWLEDGE_BASE'
    AND (search_vector @@ plainto_tsquery(?) OR chunk_text ILIKE ?)
    AND 1-(e.embedding <=> ?) >= ?
    ORDER BY e.embedding <=> ? LIMIT ?
)
-- 第二步：只对候选集做 JOIN
SELECT c.*, d.title, d.file_name, c2.name AS category_name
FROM candidates c
LEFT JOIN knowledge_base_docs d ON ...
LEFT JOIN knowledge_base_categories c2 ON ...
```

新增 V39 迁移脚本：
- `pg_trgm` GIN 索引：加速 ILIKE 查询
- `search_vector` tsvector 预计算列 + GIN 索引：全文搜索替代多字段 ILIKE
- `source_type + company` 复合索引：最常用的 WHERE 条件
- 触发器自动维护 search_vector（INSERT/UPDATE 时更新）

关键词参数从每个词 4 个精简为 2 个（tsquery + ILIKE fallback）。诊断查询从 `COUNT(*)` 改为 `EXISTS`。

### 3. 行业关键词提取器（KeywordExtractor）

**问题**：原有分词逻辑按空格/标点简单拆分。"棉质面料"被拆成"棉质"和"面料"，"FAST/28G"被拆散，整句"棉质面料的洗涤注意事项"塞进 ILIKE 匹配不到任何记录。

**方案**：
- 内置 150+ 纺织/服装/供应链行业复合词典（棉质面料、原料采购、成本核算、生产计划等）
- 正向最大匹配（FMM）分词，保持复合词完整
- 分层优先级：产品编码 > 行业复合词 > 行业单词 > 普通词
- 停用词 100+（含时间词"最近/目前"、操作词"帮我/请"、程度词"非常/比较"）
- 限制最多 8 个关键词，避免 SQL 过于复杂

统一替换了 SmartChatServiceImpl 和 KnowledgeBaseServiceImpl 中 6 处分散的关键词提取逻辑。

### 4. 数据格式不匹配排查

**现象**：日志显示 SQL 查询成功找到 M1TT403 的数据，但 AI 仍回答"暂无数据"。

**排查过程**：追踪数据从 SQL 结果 → 上下文构建器 → LLM prompt 的完整链路。发现 `searchKnowledgeEmbeddingsDirect` 返回的字段是 `content/source/score`，但供应链上下文构建器读取的是 `type/summary/data`（Map 类型）。字段名不匹配导致构建出的上下文是空的 `### []`，LLM 看到的就是空数据。

**修复**：让直接搜索方法同时输出两种格式的字段，兼容供应链上下文构建器（type/summary/data）和知识库上下文构建器（content/score/source）。加了调试日志打印发送给 LLM 的上下文摘要，方便后续排查。

### 5. 其他工程细节

- **双模式运行**：前端自动检测后端可用性，后端不可用时降级到 Mock 数据，开发时不依赖后端服务
- **SSE 流式对话**：前端通过 EventSource 接收流式响应，支持打字机效果渲染
- **Q&A 异步向量化**：对话完成后异步将 Q+A 文本向量化存入 knowledge_embeddings，后续对话可检索历史问答
- **图片 AI 分类**：上传时通过 qwen3.6:35b 多模态能力自动识别图片内容并分类到对应相册
- **速率限制**：登录 5 分钟 5 次、改密 1 小时 3 次、上传 1 分钟 20 次

## 项目局限 & 待优化

1. **Reranker 服务单点**：bge-reranker-v2-m3 部署在 localhost:8001，没有做高可用和负载均衡。服务挂掉时降级到原始排序，但重排序质量下降明显。
2. **QueryEnhancer 丢失关键词**：Ollama 生成查询变体时可能丢失货号等精确关键词。目前靠第一层直接 SQL 搜索兜底，但根本解决方案是在 QueryEnhancer 中强制保留提取到的产品编码。
3. **Text-to-SQL 不稳定**：LangChain4j 的 Text-to-SQL 依赖 LLM 生成 SQL，复杂查询容易出错。目前作为第三层降级使用，没有做 SQL 校验和沙箱执行。
4. **向量检索阈值硬编码**：minScore 阈值（0.10/0.08/0.12）是实测后的经验值，没有做动态调整。不同文档类型的最佳阈值可能不同。
5. **前端状态管理**：没有用 Redux/Zustand，全靠 React useState + props 传递，组件层级深时 props drilling 明显。
6. **缺少自动化测试**：后端没有单元测试，前端没有 E2E 测试。RAG 管线的 4 层降级策略全靠手动测试验证。
7. **知识库文档切片策略**：固定 800 字符/片、100 字符重叠，没有根据文档类型（PDF vs Word vs Excel）做差异化切片。表格类文档切片后语义断裂严重。

## 快速启动

```bash
# 前端（无 HMR / 无 tsx watch：改代码后需重新构建并手动重启进程）
pnpm install
pnpm run build
pnpm run dev  # 或 pnpm start；http://localhost:5000
# 改代码后：Ctrl+C 停掉进程 → pnpm run build → pnpm run dev

# 后端（独立仓库）
cd backend && ./mvnw spring-boot:run

# 环境变量
NEXT_PUBLIC_BACKEND_API_URL=http://localhost:8080
```

本地与公司 FRP **都不启用** Next Fast Refresh / webpack-hmr / `tsx watch`。保存文件不会自动刷新页面或重启服务。

### 内网 FRP 与本地启动（无热更新）

**原因**：自定义服务若以 Next **development** 模式启动，浏览器会连 Fast Refresh / `webpack-hmr` WebSocket。经 FRP 时该连接失败，客户端重连循环会每隔几秒 **整页 reload**。`tsx watch` 还会在保存文件时重启 Node 进程。

**做法**：先 `next build`，再用自定义服务以 **production Next**（`dev: false`）启动。`pnpm dev` 与 `pnpm start` 均如此（无 watch、无 HMR）。

```bash
pnpm install
pnpm run build

# Linux / macOS
NEXT_HOSTNAME=0.0.0.0 PORT=5000 pnpm start
# 本地同样：pnpm run dev

# Windows (cmd / PowerShell) — pnpm start / pnpm dev 均可，无需 :win
pnpm start
# FRP 绑 0.0.0.0：PowerShell 用 $env:NEXT_HOSTNAME="0.0.0.0"; pnpm start
# cmd 仍可用：set NEXT_HOSTNAME=0.0.0.0&& pnpm start
# start:win / dev:win 仍可用，等价于 start / dev
```

改前端代码后：

1. 停止当前 Node 进程（Ctrl+C）
2. `pnpm run build`
3. 再执行 `pnpm start` 或 `pnpm run dev`

启动日志应出现 `as production` 和 `HMR / Fast Refresh / tsx watch: OFF`。

| 变量 | 推荐 | 说明 |
|------|------|------|
| `COZE_PROJECT_ENV` | `PROD` | `pnpm start` / `pnpm dev` 已设置 |
| `NODE_ENV` | `production` | 同上。自定义服务始终 `next({ dev: false })` |
| `NEXT_HOSTNAME` / `HOST` | `0.0.0.0` | 监听主机；勿把 Linux 机器名 `HOSTNAME` 当公网域名 |
| `PORT` | `5000` | 与 FRP 本地端口一致 |
| `COOKIE_SECURE` | HTTP 内网不设或 `false` | 仅 HTTPS 时 cookie 才加 `Secure` |

`react-dev-inspector` 仅在非 production 构建且 `COZE_PROJECT_ENV=DEV` 时启用；当前启动脚本不会加载它。

后端不可用时前端自动降级到 Mock 数据，可独立开发前端。

## 预置账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | Admin@123 | 管理员 |
| user | User@123 | 普通用户 |

钉钉姓名注册产生的办公账号初始密码固定为 **123456**，`must_change_password=true`，走现有登录后强制改密流程。

## 钉钉组织同步与姓名注册（Phase 1，办公/管理端）

车间考勤/报工不走钉钉。本能力仅用于办公室与管理员：从钉钉企业内部应用同步部门树和通讯录，员工用**姓名**注册本地账号。

### 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `DINGTALK_APP_KEY` | 同步时必填 | 企业内部应用 AppKey |
| `DINGTALK_APP_SECRET` | 同步时必填 | 企业内部应用 AppSecret |
| `DINGTALK_AGENT_ID` | 工作通知必填 | 企业内部应用 AgentId。未配置时应用仍可启动，打样员保存成功但**不发送**工作通知（记日志） |
| `DINGTALK_CORP_ID` | 否 | 企业 corpId |
| `DINGTALK_API_BASE_URL` | 否 | 默认 `https://api.dingtalk.com` |
| `DINGTALK_OAPI_BASE_URL` | 否 | 默认 `https://oapi.dingtalk.com` |
| `DINGTALK_COMPANY` | 否 | 写入的公司名，默认 `宝娜斯集团` |

未配置 AppKey/Secret 时**应用仍可启动**。调用「同步钉钉组织」会返回明确错误，不做 Mock 兜底。CI 可不配置这些变量。

未配置 `DINGTALK_AGENT_ID` 时同步/注册不受影响；商品库打样员工作通知功能关闭（见下方 Phase 2）。

### 应用权限（钉钉开放平台 → 企业内部应用）

- 通讯录部门信息读权限（`topapi/v2/department/listsub`、`department/get`）
- 成员信息读权限（`topapi/v2/user/list`）
- 可选：通讯录手机号信息（有则写入 `org_users.mobile`）
- 工作通知（Phase 2）：企业内工作通知发送权限（`topapi/message/corpconversation/asyncsend_v2`）

### 选用的 OpenAPI

| 用途 | 方法 | 地址 |
|------|------|------|
| accessToken | POST | `https://api.dingtalk.com/v1.0/oauth2/accessToken` |
| 部门详情 | POST | `https://oapi.dingtalk.com/topapi/v2/department/get` |
| 子部门（仅下一级，需递归） | POST | `https://oapi.dingtalk.com/topapi/v2/department/listsub` |
| 部门成员（cursor 分页） | POST | `https://oapi.dingtalk.com/topapi/v2/user/list` |
| 工作通知（Phase 2） | POST | `https://oapi.dingtalk.com/topapi/message/corpconversation/asyncsend_v2` |

### 同步与注册流程

1. 管理员登录后打开 **钉钉组织**（`/org`），点击「同步钉钉组织」。
2. 后端拉取部门树写入 `org_departments`，通讯录写入 `org_users`（不自动创建登录账号）。
3. 员工打开注册页，只填**姓名**（公司默认宝娜斯集团）。
4. 系统在已同步通讯录中精确匹配姓名（忽略中间空格）：
   - 0 人：404，提示联系管理员同步
   - 1 人：创建本地账号，复制部门/职位，绑定 `dingtalk_userid`
   - 多人：409 + 候选人，用户点选后再提交 `dingtalkUserid`
5. 新账号密码固定 `123456`，`must_change_password=true`。登录后沿用现有 `/settings?tab=security&forceChange=1` 改密。邮箱不要求；若钉钉无邮箱则写入 `dt-{userid}@dingtalk.invalid` 占位。

管理员 API（需 admin/superadmin）：`POST /api/org/sync`、`GET /api/org/status`、`GET /api/org/departments`、`GET /api/org/contacts?name=`。

## 钉钉工作通知（Phase 2，商品库打样员）

商品库创建或更新时，若 **打样员（sampler）新填写或变更为另一人**，向该打样员推送钉钉企业内部应用**工作通知**。sampler 未变的普通保存不重复通知。

公开未登录表单链接（工位填报等）本阶段**明确不做**，由产品后续另开需求。

### 行为

1. 事务提交成功后再异步发送，钉钉失败**不影响**商品保存。
2. 按姓名解析钉钉 userid（去空格精确匹配，与 Phase 1 `OrgNameMatcher` 一致）：
   - 优先 `org_users`（已同步通讯录）
   - 其次 `users.dingtalk_userid`（已注册办公账号的 username / nickname）
   - 0 人、同名多人、userid 为空：记 warn 日志并跳过，不硬失败
3. 消息为中文 action_card（标题「您被指定为打样员」），单按钮与 markdown `[填写打样表单](http…)` 均跳转**已登录**打样表单 `{FRONTEND_URL}/sampler/{id}`（`src/app/sampler/[id]`）。`single_url` **默认就是该 HTTP(S) 直链**（官方 action_card 示例即为 `https://open.dingtalk.com`，无需 `dingtalk://` 包装）；无前端地址时退化为 text。
4. 未配置 AgentId / AppKey / Secret：功能关闭，INFO 日志说明原因，应用不崩溃。

公开未登录表单本阶段不做。打样员须先登录（401 会跳转 `/login?returnUrl=/sampler/{id}`），与全站会话一致。

### 钉钉内打开与「后续页面非钉钉提供」

避免把表单包进 `dingtalk://dingtalkclient/action/openapp` 或 `page/link`（域名未进白名单、或 HTTP FRP 被错误包装时，钉钉会报「后续页面非钉钉提供」）。默认直链即可。

若仍出现该提示，在 **钉钉开放平台 → 该企业内部应用 → 开发管理 → 安全设置 / H5 可信域名** 加入 `FRONTEND_URL` 主机（生产如 `ai.bonasoma.com`），并把应用首页 / PC 首页写成同一站点。HTTP 公网若仍被拒，需上 HTTPS。

仅当同时配置了 `DINGTALK_CORP_ID` **且** `DINGTALK_WORK_NOTICE_PROTOCOL_LINKS=true` 时，按钮 URL 才会包一层 `dingtalk://`；那时安全域名必须包含 FRONTEND_URL 主机。

### 手机端（钉钉内打开）

工作通知打开的是打样员专用页 `src/app/sampler/[id]/page.tsx`（非商品库列表）。约 **375px** 宽下应满足：

- 顶栏不横向溢出
- 商品信息单列、输入框 ≥44px、字号 16px（避免 iOS 聚焦放大）
- 保存按钮全宽、图片操作按钮常显（不依赖 hover）

验证：浏览器开发者工具 iPhone SE / 375×667，打开 `/sampler/{id}`；桌面编辑仍走 `/goods-library/{id}`。

### 额外环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `DINGTALK_AGENT_ID` | 发送时必填 | 与 AppKey/Secret 同一企业内部应用的 AgentId |
| `FRONTEND_URL` | 否 | 工作通知跳转基址，默认 `http://localhost:5000`（已有配置 `app.frontend.url`） |
| `DINGTALK_WORK_NOTICE_PROTOCOL_LINKS` | 否 | 默认 `false`。`true` 且已配 corpId 时才用 `dingtalk://` 包装 `single_url` |

### 范围

Phase 1 组织同步 / 姓名注册行为不变。车间考勤、ERP、钉钉 SSO 登录均不在本能力范围内。

## 项目结构

### 前端

```
src/
├── app/                          # Next.js App Router
│   ├── layout.tsx               # 根布局
│   ├── page.tsx                 # 主页面（含权限检查）
│   ├── login/page.tsx           # 登录页（分屏布局 + 品牌展示）
│   ├── register/page.tsx        # 钉钉姓名注册（初始密码 123456，强制改密）
│   ├── org/page.tsx             # 钉钉组织同步（管理员）
│   ├── sampler/[id]/page.tsx    # 打样员专用表单（钉钉工作通知落地页）
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
├── enhance/                             # RAG 增强模块
│   ├── RagPipeline.java                # RAG 管线（查询增强 + 多路召回 + Reranker）
│   ├── QueryEnhancer.java              # 查询增强器（Ollama 生成变体）
│   └── Reranker.java                   # 文档重排序（bge-reranker-v2-m3）
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
    ├── KeywordExtractor.java           # 行业关键词提取器（FMM + 词典）
    ├── RateLimiter.java                # 速率限制
    └── PasswordValidator.java          # 密码强度验证

backend/src/main/resources/
├── application.yml                      # 应用配置
└── db/migration/                        # Flyway 数据库迁移
    ├── V21__create_user_sessions.sql   # 用户会话表
    ├── V22__add_company_field.sql      # 公司字段
    ├── V28__create_position_knowledge_cards.sql  # 岗位知识卡片表
    ├── V29__add_position_card_embedding_status.sql  # 向量化状态字段
    └── V60__dingtalk_org.sql            # 钉钉组织部门树/通讯录/用户绑定
```

## 数据库设计

### 核心数据表

| 表名 | 说明 | 隔离方式 |
|------|------|----------|
| `users` | 用户表 | company |
| `org_departments` | 钉钉部门树 | company |
| `org_users` | 钉钉通讯录缓存 | company |
| `org_user_departments` | 人员-部门多对多 | org_user_id |
| `org_sync_state` | 钉钉组织同步状态 | company |
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
| SMART_CHAT | 历史 Q&A 向量化 |

切片规则：800 字符/片，100 字符重叠，使用 bge-m3 本地模型向量化。

### 搜索性能索引（V39）

| 索引 | 类型 | 作用 |
|------|------|------|
| `idx_ke_search_vector` | GIN (tsvector) | 全文搜索替代多字段 ILIKE |
| `idx_ke_chunk_text_trgm` | GIN (pg_trgm) | 加速 ILIKE 模糊查询 |
| `idx_ke_source_type_company` | B-tree | 最常用的 WHERE 条件组合 |
| `idx_ke_source_doc_id` | B-tree | 加速 LEFT JOIN |
| `idx_ke_created_at_desc` | B-tree | 加速 ORDER BY created_at DESC |

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
