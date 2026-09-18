# 宝娜斯集团 · 产品智能中台

宝娜斯（Bonasoma）内部系统（`com.imagemanager`）。登录后三个门户：**设计师**、**工厂 / 供应链**、**市场营销**。不是豆包 / 扣子 / 火山引擎产品；`COZE_PROJECT_ENV` 只是启动脚本里的环境变量名。

主路径是 **本地 LLM 问答**（Ollama，默认 `qwen3.6:35b` 对话 + `bge-m3` 向量，SSE）。难点不在「接了 RAG」，而在：**向量对货号召回差、改写查询会弄丢编码、低分切片进上下文会瞎编**。下面按代码说明怎么防，而不是列功能清单。

生产 FRP 常见：`http://ai.bonasoma.com`。

---

## 1. RAG 怎么设计、幻觉从哪来、代码怎么挡

编排在 `SmartChatServiceImpl.smartChat`（`GET /api/chat/smart`）。先意图，再检索，再拼上下文，最后才把 **带约束的 system + 检索块 + 用户问题** 交给 Ollama。模型看不到全库，只能看到这一轮注入的材料。

### 1.1 为什么不能「一句问题直接向量检索」

货号（如 `M1TT403`）在 embedding 空间里经常低于阈值，看起来像「库里没有」。`QueryEnhancer` 用 Ollama 把问题改写成 3 个变体时，**提示词只要求「保持原意、换说法」**，**没有**「必须保留货号」——变体完全可能把 `M1TT403` 改成「该型号产品」。若先走增强再检索，精确匹配就断了。

所以检索是 **先保编码、再语义、最后才让 LLM 说话**，不是「Pipeline 一条龙」。

### 1.2 知识库：先 SQL 货号，再 RagPipeline，再直查向量

`searchKnowledgeBase`（`SmartChatServiceImpl`）：

1. **`KeywordExtractor.extractProductCode` 抽出货号**（字母+数字、排除 `ERP`/`BOM`/`HTTP` 等），然后 `searchKnowledgeEmbeddingsDirect` 对 `knowledge_embeddings` 做关键词 SQL。不经过 Ollama、不改写、无向量超时。有命中就 **直接返回**，后面的 Pipeline 不再跑。
2. **没有货号或 SQL 空**：才进 `RagPipeline.enhancedSearch`。
3. Pipeline 仍空或异常：`KnowledgeBaseService.search(query, 0.30, 15)` 直查。阈值和 Pipeline 粗筛对齐；注释写明 **再低会召回弱相关切片引发幻觉**。

`KnowledgeBaseServiceImpl.search` 自己还有一层：Milvus 启用则先向量搜（同样用传入的 `minScore`）；落到 pgvector 时再次用 `KeywordExtractor` 分词，**货号关键词优先 ILIKE**，没有再走 tsvector + 余弦混合。行业复合词用正向最大匹配（「棉质面料」不拆成「棉质」「面料」），停用词去掉「帮我/最近/什么」，最多 8 个词，避免 SQL 膨胀。

### 1.3 RagPipeline：增强只做召回补面，不替代原句

`RagPipeline.enhancedSearch`：

| 步骤 | 做什么 | 为什么 |
|------|--------|--------|
| 变体 | `QueryEnhancer.enhance`：原句始终在列表里；Ollama `/api/generate` 再要 3 个变体；失败则只用原句 | 语义问法（「洗涤注意」）靠变体扩召回；**货号问法不应依赖变体** |
| 并行召回 | 每路 `knowledgeBaseService.search(q, 0.30, 10)`，**单路 1s 超时熔断**，整体最多 **1.5s** | 增强本身可能 >1s；超时返回空，不堵死其他路 |
| 全空再降 | 原句、`minScore=0.25`、limit 15 | 略降阈值，仍有下限，避免垃圾切片 |
| 去重 | chunk 前 200 字指纹，留高分 | 多路重复 |
| 重排 | `Reranker` → bge-reranker-v2-m3（`:8001`，可关；失败回向量序） | 向量近 ≠ 能回答这个问题 |
| 硬过滤 | **rerank 分 < 0.40 丢弃** | 注释原话：低分切片会幻觉，例如问「切换模式」却召到货号数据 |

最终 `enhancedSearchAsMap` 只留 **top 5** 且过 0.40 的片段。

**货号不被改写毁掉的办法是绕开增强，不是增强器自己保码。** 原句仍作为第一路，是语义检索的保底，不是 SKU 的主路径。

### 1.4 工厂模式：表数据权威，文档切片让路

工厂（`mode=factory`）在知识库之外还有一层，顺序在 `smartChat` 里写死：

1. **报价实体检索**（问题里是否真有库中的单号/客户/货号，不靠「报价」二字）：`QuotationCalcService` / `order_bjd_query`，可向前端推 `quotation_list`。宽泛供应链意图才走 `searchSupplyChain`。
2. **`DecisionDataService` 结构化 SQL**（工艺、BOM、工序工价、销售订单、产能、商品库等）。货号级 ERP **已经命中时跳过 Milvus 业务员文档**——文档切片可能过期或近似，不能盖过表数字。
3. **`searchSupplyChain` 宽检索**：同样先货号直查 embeddings → RagPipeline → LangChain4j Text-to-SQL（`SupplyChainAssistant` / `SupplyChainTools`）→ 业务表关键词。Text-to-SQL 排在向量后面，当语义问法（「哪些供应商做染色」）而不是精确编码。
4. 知识库 RAG（与设计师共用）。
5. Milvus 业务员库：bge-m3 + HNSW COSINE，**score < 0.35 丢掉**；同文档多切片按分聚合再按 `chunkIndex` 拼回阅读顺序；全局大约 **8000 字预算**，防止撑爆本地上下文。

闲聊：设计师可跳过检索（避免无关切片）。工厂默认都当业务问题。**模式切换口令**（「切换商品企划模式」等）**整段检索都不跑**——否则指令句会召到货号块，正好触发上面 0.40 要防的那类幻觉。

### 1.5 上下文怎么拼、prompt 怎么把模型按死在证据上

注入顺序（工厂 system 里的 L1–L5）：内部规则/岗位经验 → **报价与 ERP 表、业务员 Milvus** → 知识库原文 → 网络（仅企划）→ 用户口头数字。冲突必须写「数据差异说明」，禁止静默覆盖。

用户消息不是裸问题。有检索块时拼成「上下文 + `用户问题:`」，再追加约束，例如：

- 有供应链数据：优先用上方精确数字；报价意图还要走 SOP 四步，不得只回一个数。
- 有岗位卡片：用卡片经验，不要用知识库泛文替代。
- 其余：**必须严格基于以上知识回答，禁止用通用知识补编；不够的部分写清「暂无数据」。**
- 检索全空：只发原问题，靠 system 的拒答句，而不是塞一堆低分垃圾。

**工厂 system（节选设计，不是口号）**

- 身份锁死：忽略历史里别的角色自称。
- 数字只认检索里的报价/供应链；单号/货号/客户必须**精确包含所问实体**，模糊相似不得引用。
- 三路都没有：必须说 **「当前数据库中暂无此数据」**，严禁通用知识编造。
- 文末标注来源（供应链 / 业务员资料 / 知识库 / 图 / 网）。
- `buildUniversalLogicRules`：关键数字要来源、日期、缺失、置信度；没有依据的结论不准输出；缺口标【假设项】。

**设计师 system**

- 身份是设计师助手，不是工厂助手；报价/原料等问题 **引导去供应链门户**，本模式不注入 ERP/Milvus 业务员库。
- 岗位问题优先【岗位知识卡片】。
- 知识库和卡片都没有：必须 **「当前知识库中暂无此内容」**，不要用通识猜。

**联网（仅 factory + `planning`）**：MiniMax 只收 **用户原句**（或企划改写句），**禁止**把知识上下文拼进外网请求。回来的摘要标 L4，和内部 L2/L3 冲突时以内部为准。设计师模式代码路径不跑 web_search。

### 1.6 向量落在哪（服务检索，不是功能点）

- pgvector `knowledge_embeddings`：`KNOWLEDGE_BASE` / `POSITION_CARD` / `SMART_CHAT`（非闲聊回合异步写入 Q+A）。
- Milvus：业务员资料导入直写（`MilvusService`，`milvus.collection`，代码默认 `salesperson_docs`）。知识库上传可双写。

对话用 Ollama `/api/chat`。没有仍在写入的 Memory 库服务。

---

## 2. 三个门户（检索工具不同）

### 设计师 `/` · `/chat` · `/knowledge` · `/ai-image`

图库、岗位卡片、文档中心、商品库、AI 生图（文生图/图生图 → 异步轮询 → 可入库「二创」）。RAG 只用岗位卡片 + 知识库 + 历史 Q&A + 图库检索。生图 API 不做 RAG。

### 工厂 / 供应链 `/supply-chain`（一等能力）

Tab：**AI 对话**（`mode=factory`，子模式通用 / 商品企划 `planning` / 决策 `decision`）和 **单据概览**。另有历史订单、管理员 `/erp-sync`（七模块增量 **只插入不覆盖**；报价表 `order_bjd_query` 不在这七个接口里）。不是 MES，无车间报工。

前端已去掉原料/计划等 CRUD Tab；那些 REST 还在后端。问答主要打报价表、ERP 同步表、知识库、Milvus。

### 营销 `/marketing`

Ollama 流式，**无检索、无向量**。

**影刀**：业务代码无 RPA/飞书机器人；上传接口可供外部自动化调用，闭环不在本仓库。

---

## 3. 商品库打样与钉钉（配套）

文件夹 = 货号+品名；四槽图上 OSS。打样员变更后异步 ActionCard → `/sampler/{id}`。钉钉不能改已发卡片，补全货号/品名会再发「打样信息已更新」。

`/org` 同步通讯录（不同步批量开户）→ `/register` 姓名开户，初始密码 **123456** 强制改密。钉钉内 H5 免登 + ticket；未注册通讯录成员只有 12 小时 `sampler` 会话。未配 AgentId 则保存成功但不发通知。

---

## 4. 运行与配置

Next.js 16 + 自定义 `src/server.ts`（**无 HMR**）+ Spring Boot 3.2 / Java 17。PostgreSQL、**Redis 会话**、OSS（失败降本地 `uploads`）。BFF：`/api/*` → `:8080/api`。后端挂了返回 503，无 Mock。

```bash
cd backend && ./mvnw spring-boot:run     # :8080/api
pnpm install && pnpm run build
NEXT_HOSTNAME=0.0.0.0 PORT=5000 pnpm start
```

`pnpm dev` 与 `start` 都是 `NODE_ENV=production COZE_PROJECT_ENV=PROD PORT=5000 npx tsx src/server.ts`。

角色：`user` / `admin` / `superadmin` / `sampler`。种子账号密码只来自 `SEED_*`。

常用变量：`BACKEND_API_URL`、`OLLAMA_BASE_URL`、`MILVUS_*`、`RERANKER_*`、`S3_*`、`DINGTALK_*`、`ERP_*`、`FRONTEND_URL`、`CORS_ALLOWED_ORIGINS`。HTTP 内网不要开 `COOKIE_SECURE`。不要提交 `application-local.yml`。
