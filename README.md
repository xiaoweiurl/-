# 宝娜斯集团 · 产品智能中台

宝娜斯（Bonasoma）内部系统，工程名 Image Manager（`com.imagemanager`）。登录后分三个门户：**设计师**、**工厂 / 供应链**、**市场营销**。

**核心能力是本地 LLM 问答**：用 Ollama 上的对话模型（默认 `qwen3.6:35b`）+ 向量模型（`bge-m3`），对知识库、岗位卡片、业务员资料和供应链结构化数据做 **RAG**，SSE 流式回答。向量侧是 **PostgreSQL pgvector + Milvus** 双存储，不是豆包 / 扣子 / 火山引擎产品。启动脚本里的 `COZE_PROJECT_ENV` 只是环境变量名。

生产 FRP 常见地址：`http://ai.bonasoma.com`。

---

## 1. 核心：本地 LLM + RAG + 向量检索

编排在 `SmartChatServiceImpl`，入口 `GET /api/chat/smart`（SSE）。`mode=designer`（默认，`/chat`）与 `mode=factory`（`/supply-chain`）走不同检索与系统提示。

### 模型与服务

| 用途 | 实现 |
|------|------|
| 主对话 / 查询改写 | Ollama `app.ollama.chat-model`（默认 qwen3.6:35b），`/api/chat` 流式 |
| 文本向量 | Ollama `bge-m3`，1024 维 |
| 导入 OCR | 同一套多模态模型 `app.ollama.vision-model` |
| 重排序 | 独立 HTTP 服务，默认 `localhost:8001`（bge-reranker-v2-m3） |
| 企划联网 | 仅工厂 `subMode=planning` 调 MiniMax web_search；设计师模式**不联网** |

### 向量存哪里

| 存储 | 内容 |
|------|------|
| **pgvector** `knowledge_embeddings` | 知识库切片 `KNOWLEDGE_BASE`；岗位卡片 `POSITION_CARD`；对话 Q&A `SMART_CHAT` |
| **Milvus** | 业务员资料批量导入（zip/目录流式解析 → 切片 → 向量 → 直写，不落 pgvector）。Java `MilvusService` 绑定 `milvus.collection`（代码默认 `salesperson_docs`）；`application.yml` 另有 `collection-name: salesperson_chunks`，以实际部署与该 `@Value` 为准 |

知识库**上传**会双写 pgvector + Milvus（若 Milvus 启用）。`KnowledgeBaseServiceImpl.search` 先试 Milvus，再做 pgvector 关键词 + 向量混合检索。

代码里没有仍在写入的 `MEMORY` 记忆库服务；旧注释里的「双库检索」已过时。

### 检索顺序（代码里的降级，不是宣传口径）

**知识库路径**（`searchKnowledgeBase`）

1. 关键词直查 SQL（货号等精确命中，绕开 Ollama 超时）
2. `RagPipeline`：Ollama 生成查询变体 → 多路向量召回 → 去重 → reranker
3. 再降级：直接 `KnowledgeBaseService.search`（Milvus → pgvector）

**工厂模式额外层**

1. 报价单实体检索（`order_bjd_query` / `QuotationCalcService`，可出 `quotation_list` 表）
2. 结构化 ERP SQL（`DecisionDataService`：工艺、BOM、工序工价、销售订单、产能、商品库等）
3. 供应链宽检索：RagPipeline → LangChain4j Text-to-SQL / `@Tool` → 业务表关键词 SQL
4. Milvus 业务员资料（已有货号结构化命中时可跳过）
5. 与设计师共用的知识库 RAG

无命中时提示词要求回答「当前数据库中暂无此数据」，禁止编造。对话结束后非闲聊回合会异步把 Q+A 写入 `SMART_CHAT` 向量。

大规模导入：`POST /api/knowledge/import/path` 或 `/upload`，进度 `/progress/{taskId}`。

---

## 2. 门户

登录页三入口（`portal_type`）。

### 设计师（`/`、`/chat`）

图库（相册、上传、预览、收藏、回收站、`/edit/[id]`）、知识库与**岗位卡片**、文档中心、商品库、**AI 生图**、设计师对话。

设计师 RAG 优先岗位卡片 + 知识库 + 历史 Q&A 向量 + 图库检索，**不注入** ERP / 报价单 / Milvus 业务员库。供应链类问题提示走工厂门户。

**AIGC（`/ai-image`）**：文生图 / 图生图（参考图 base64）→ 外部生成 API 异步任务 → 前端轮询 → 可选 `save-to-gallery` 写入「二创中心」（`source=creative`）。生图链路本身不做 RAG。上传图可走关键词分类；可选外部 Vision HTTP（`app.ai.*`，默认 URL 是历史残留，不是产品名）。

### 工厂 / 供应链（`/supply-chain`）

这是一等公民，不是附录。页内两个 Tab：

| Tab | 内容 |
|-----|------|
| **AI 对话** | `mode=factory`。子模式：通用助手 / **商品企划** `planning` / **决策辅助** `decision` |
| **单据概览** | 报价金额、销售**数量**（`sl_sum` 不是金额）、工艺单、合格率、趋势与最近单据 |

另有 **历史订单** `/supply-chain/history-orders`（只读，已审核销售单），管理员 **ERP 同步** `/erp-sync`。

ERP 同步：外部 HTTP 增量拉取，**只插入本地没有的行，不覆盖已有数据**。七个模块：销售订单、内衣/丝袜工艺单、工艺部件、工序、工价、原料 BOM。报价表 `order_bjd_query` **不在**这七个接口里。本仓库**不是**工厂 MES，没有车间报工/考勤。

后端仍保留 `/supply-chain` 下产品报价、原料、生产计划等 REST（含智能报价计算），**当前前端 Tab 已去掉这些 CRUD 页**；工厂问答主要打 `order_bjd_query`、ERP 同步表、知识库和 Milvus。

默认 `ERP_BASE_URL` 历史上是义乌网纺 Unitive 风格路径，可用环境变量改。

### 市场营销（`/marketing`）

独立 SSE 对话（`MarketingChatController`），Ollama 流式，**不做 RAG / 向量检索**。

### 其它页面

数据驾驶舱、AI 能力中心、数据资产、数据模型、运维中心（管理员）；商品库桌面编辑 `/goods-library/{id}`。

**影刀 RPA**：Java/TS 业务代码中**没有**影刀 SDK、定时采集或飞书机器人。仓库里的「影刀入库」只出现在面试题文档（`PDF-README.md`）。中台提供已登录的图片上传 / 批量 URL 入库接口，任何外部 RPA 都可以调，但闭环不在本仓库里实现。

---

## 3. 商品库打样与钉钉（配套）

商品库：文件夹 = 货号 + 品名；四槽图（主图/侧面/细节/产品图）上 OSS。

打样员变更且保存成功后，异步发钉钉 ActionCard 到 `/sampler/{id}`。钉钉**不能改已发出卡片正文**；表单补全原卡缺失的货号/品名时会再发一封「打样信息已更新」。

- 办公端：`/org` 同步通讯录（不同步批量开户）→ `/register` 按姓名开户，初始密码 **123456**，强制改密。指派打样员发通知前会按通讯录尝试确保本地账号存在。
- 钉钉内打开表单：H5 免登 + HMAC ticket；未注册通讯录成员只有 **sampler** 短会话（12 小时）。普通浏览器走登录页。
- 未配 AppKey/Secret 应用仍启动；未配 AgentId 则保存成功但不发通知。H5 可信域名（如 `ai.bonasoma.com`）改完必须**发布应用**。

---

## 4. 技术栈与目录

- **前端**：Next.js 16 + React 19 + TypeScript + shadcn/ui + Tailwind 4；自定义 `src/server.ts`，**始终 production Next**（无 HMR）
- **后端**：Spring Boot 3.2 / Java 17，`backend/`
- **库**：PostgreSQL（默认库 `image_management`）；增量 SQL 在 `db/migration/`（Flyway 文件名，**未**接 Flyway 插件）
- **会话**：Redis
- **对象存储**：阿里云 OSS（S3 兼容）；图片优先 S3，失败降级 `./uploads`；文档/知识库文件走本地盘

```
src/app/          门户页面；api/[[...path]] 统一 BFF → Java :8080/api
src/server.ts     前端入口 PORT=5000
backend/          SmartChat / RagPipeline / Milvus / ERP / 钉钉 / 商品库
```

浏览器打同源 `/api/<java路径>` 即可，一般不用再写 Next 代理。后端不可用返回 **503**，无 Mock 登录。

### 角色

| 角色 | 摘要 |
|------|------|
| `user` | 业务页；不能管用户、不能 ERP 同步 |
| `admin` | 管理 + ERP；不能重置其他 admin/superadmin 密码（本人除外） |
| `superadmin` | 最高权限 |
| `sampler` | 仅打样表单 |

种子账号 `superadmin` / `admin` / `user` 仅 local/dev，密码来自 `SEED_*`，文档不写明文。

---

## 5. 本地启动

需要：Node + pnpm ≥ 9、JDK 17、PostgreSQL、**Redis**（没 Redis 登不上）、可选 Ollama / reranker / Milvus。

```bash
# 后端
cd backend
# application-local.yml 或 DATABASE_*、REDIS_*、SEED_*（该 yml 已 gitignore）
./mvnw spring-boot:run          # http://localhost:8080/api

# 前端：无热更新。改代码 → 停进程 → build → 再 start
pnpm install && pnpm run build
NEXT_HOSTNAME=0.0.0.0 PORT=5000 pnpm start
# Windows：pnpm start 与 start:win 相同
```

`pnpm dev` 与 `pnpm start` 都是 `NODE_ENV=production COZE_PROJECT_ENV=PROD PORT=5000 npx tsx src/server.ts`。日志应有 `as production` 和 `HMR ... OFF`。

| 变量 | 说明 |
|------|------|
| `BACKEND_API_URL` / `NEXT_PUBLIC_BACKEND_API_URL` | 默认 `http://localhost:8080/api` |
| `OLLAMA_BASE_URL` | 默认 `http://localhost:11434` |
| `MILVUS_HOST` / `MILVUS_PORT` / `MILVUS_ENABLED` | 业务员库；关掉则工厂模式跳过该路 |
| `RERANKER_BASE_URL` / `RERANKER_ENABLED` | 关掉则跳过重排 |
| `S3_*` / `STORAGE_TYPE` | OSS；配不齐则本地盘 |
| `FRONTEND_URL` | 钉钉跳转，默认 `http://localhost:5000` |
| `CORS_ALLOWED_ORIGINS` | 生产加上公网 Origin |
| `DINGTALK_*` | 组织同步与打样通知 |
| `ERP_*` | 管理员同步 |
| `COOKIE_SECURE` | HTTP 内网不要 `true` |

Swagger：`http://localhost:8080/api/swagger-ui.html` 或前端 `/api-docs`。

---

## 开发约定

不要提交密钥、`.env.local`、`application-local.yml`。前端 `pnpm ts-check` / `pnpm lint`；后端 `./mvnw test`。基于 `main` 开短分支。不要把 session 放进 URL，也不要加 Mock 鉴权兜底。
