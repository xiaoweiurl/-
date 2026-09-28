# Milvus 混合检索

默认关闭。关闭时对话检索、货号 ILIKE、ERP 工具路径和现在一样。打开后，知识库和业务员资料的 Milvus 检索改读影子集合。

稠密向量是本地 Ollama `bge-m3`（1024 维）。重排序是本地 `bge-reranker-v2-m3`（`localhost:8001`），这条链路没有改。Java SDK 是 `io.milvus:milvus-sdk-java:2.5.6`。

线上 Milvus 是 **v2.6.20 standalone**（`localhost:19530`，另有 Attu）。这个版本自带 BM25 Function 和 hybridSearch，不需要降级，也不需要为了混合检索升级。

## 代码实际读哪个集合

Java 读的是 `milvus.collection`，没配环境变量时默认 **`salesperson_docs`**。这就是线上正在用的集合（约 7604 行）：`chunk_id`、`doc_id`、`file_name`、`doc_type`、`chunk_index`、`content`、`embedding` FloatVector 1024 COSINE。没有 Function，`content` 没有开分词器。

`application.yml` 里的 `milvus.collection-name`（默认 `salesperson_chunks`）没有任何 Java 字段绑定。`salesperson_chunks` 是空集合，带 `salesperson_id` / `customer_id`，检索不会读它。不要把默认集合改成这个名字。

| 来源 | 存储 |
| --- | --- |
| 业务员资料 | Milvus `salesperson_docs` |
| 知识库文档 | pgvector `knowledge_embeddings`，上传时顺带写入同一个 Milvus 集合 |
| 历史问答 | 只在 pgvector，`source_type=SMART_CHAT` |
| 商品库 | PostgreSQL `goods_library`，没有向量 |
| ERP / 工艺单 / BOM / 报价 | PostgreSQL 业务表 |

## 影子集合

混合检索写到旁边的新集合，默认 **`salesperson_docs_hybrid`**，可用 `MILVUS_HYBRID_COLLECTION` 改名。回填和重建只会删除这个影子集合。`salesperson_docs` 和 `salesperson_chunks` 被代码拒绝作为目标，不会被删、不会被改 schema。

影子集合在原字段之外增加：

- `lexical_text`：由 `content` 在客户端预先分词后写入，Milvus 用 whitespace + lowercase 分词器。货号、型号、面料编号整段保留，所以 `25YK00022`、`C100-40S` 不会被切开。
- `sparse`：BM25 Function 从 `lexical_text` 自动生成，不手写稀疏向量。
- `embedding`：从原集合原样拷贝，不调 Ollama。

原始 `content` 保持不加分词器，引用和正文展示仍是原文。没有把 jieba 直接开在 `content` 上，避免服务器把字母数字串切碎。

融合用 RRF，k=60。余弦分和 BM25 分不是一个尺度，WeightedRanker 要手调权重。没有线上评测集时 RRF 更稳，稀疏通道排第一的货号不会被 0.35 的余弦阈值丢掉。

## 在 Windows 上按这个顺序执行

在仓库的 `backend` 目录打开 PowerShell。不需要启动 Spring Boot Web，也不会连 Postgres。Flyway 不用跑，没有要手工执行的 SQL。

先看一眼，不写任何集合：

```powershell
cd backend
.\mvnw.cmd -DskipTests compile exec:java "-Dexec.mainClass=com.imagemanager.milvus.tools.HybridBackfillMain" "-Dexec.args=--dry-run --host=localhost --port=19530 --source=salesperson_docs --target=salesperson_docs_hybrid"
```

确认打印的原集合行数仍是 7604 左右，然后再回填。这一步只读 `salesperson_docs`，把已有向量和正文分批抄到 `salesperson_docs_hybrid`，每批 200 行。可以重复执行：每次只删掉影子集合再完整重抄，原集合行数应保持不变。

```powershell
.\mvnw.cmd -DskipTests compile exec:java "-Dexec.mainClass=com.imagemanager.milvus.tools.HybridBackfillMain" "-Dexec.args=--host=localhost --port=19530 --source=salesperson_docs --target=salesperson_docs_hybrid --dim=1024 --batch=200"
```

回填完成后做对比。同一批从原集合抽样的问题，分别打稠密检索和混合检索。问题里会有货号、面料编号、型号；如果库里有 `25YK00022`，这道题会排在前面。不含编号的切片用来出中文题。查询向量向本机 Ollama `bge-m3` 现算，不写回 Milvus。整个过程只读两个集合，不连接 Postgres。

```powershell
.\mvnw.cmd -DskipTests compile exec:java "-Dexec.mainClass=com.imagemanager.milvus.tools.HybridCompareEvalMain" "-Dexec.args=--host=localhost --port=19530 --source=salesperson_docs --target=salesperson_docs_hybrid --embed-url=http://localhost:11434 --embed-model=bge-m3 --dim=1024 --sample-codes=40 --sample-chinese=15 --seed=25 --top-k=10 --output=hybrid-eval-report.md"
```

打开 `backend\hybrid-eval-report.md`。里面有每种方式的 Recall@5、Recall@10、MRR 和延迟（均值 / P50 / P95），并按货号、面料编号、型号、中文拆开。

货号类问题：返回正文里包含该编号就算命中。中文题：必须命中抽样的那一条 `doc_id + chunk_index`。影子集合的 `chunk_id` 是新的自增主键，不能拿来对原集合。

数字可以接受之后，再打开开关并在 IntelliJ IDEA 里重启后端：

```powershell
$env:MILVUS_HYBRID_ENABLED = "true"
```

或在本地 yml 把 `milvus.hybrid.enabled` 设为 `true`。未打开时，应用仍读 `salesperson_docs`，行为和现在一样。

IntelliJ 也可以直接运行这两个 main class，程序参数用上面 `--host=...` 那一串，工作目录选 `backend`。不要用 Spring Boot 的主类来跑它们。

## 回滚

把 `MILVUS_HYBRID_ENABLED` 设为 `false` 并重启。稠密集合不动。影子集合可以留着，也可以在 Attu 里只删 `salesperson_docs_hybrid`。不要删 `salesperson_docs`。

## 假数据演练

在 Milvus v2.6.20 上用临时集合 `hybrid_smoke_src` / `hybrid_smoke_hybrid` 跑过回填和评测（跑完会删掉这两个临时集合）。编号题故意用无关稠密向量，中文题用该切片自己的向量。结果：

| 方式 | 题型 | Recall@5 | Recall@10 | MRR |
| --- | --- | ---: | ---: | ---: |
| 稠密 | 全部 5 题 | 0.400 | 0.400 | 0.400 |
| 混合 | 全部 5 题 | 1.000 | 1.000 | 1.000 |
| 稠密 | 货号 25YK00022、面料编号 C100-40S、型号 ZX9K2 | 0 | 0 | 0 |
| 混合 | 同上三题 | 1 | 1 | 1 |
| 稠密 / 混合 | 中文 2 题 | 1 | 1 | 1 |

这不是线上 7604 行的结果。线上数字以你本机生成的 `hybrid-eval-report.md` 为准。

同一条命令也可以在开发机上复现：

```bash
cd backend
./mvnw -DskipTests compile exec:java -Dexec.mainClass=com.imagemanager.milvus.tools.HybridCompareEvalMain -Dexec.args="--demo --output=target/hybrid-smoke-report.md"
```

`--demo` 不会读取 `salesperson_docs`。
