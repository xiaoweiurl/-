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

## 型号题为什么没召回

在 Milvus v2.6.20 上用临时集合复现过：单独做 BM25，`K294`、`T15`、`M1YK010M`、`C100-40S`、`25YK00022` 都能排到第 1。混合检索却把短编号挤出前 10。

原因是查询词袋里带着「型号」这类常见词。60 条正文含「型号」、稠密向量又和问句同向的无关切片，会同时进稠密和稀疏的前 48。RRF（k=60）给它们大约 `1/61 + 1/62 ≈ 0.032`。只在稀疏通道排第 1、稠密向量进不了前 48 的编号切片只有 `1/61 ≈ 0.016`，进不了最终前 10。面料编号和货号匹配到的常见词少，所以同样的 0.016 还能留在前面。评测路径本身没有 0.35 余弦阈值。正文截断也不是这次的原因：返回内容里能看到完整的 `K294`。

查询侧已改成：问句里有编号时，BM25 只送这些编号的小写全文；带 `-` `/` `_` `.` 时再加一份去掉分隔符的形式，不再把分段（例如 `40s`）和「型号 / 货号 / 面料」送进去。没有编号的中文问句仍用完整词袋。文档侧索引没变，已经回填的 `salesperson_docs_hybrid` 里已经有 `k294` 这类 token。

因此这次不要重建影子集合，也不要重跑回填。`salesperson_docs` 保持不动。

## 抽样收紧

评测不再把这些 token 当成题目：

- 数量和单位：`98g`、`405W`、`7865.00KGS`（后缀 `kgs/kg/g/mg/gsm/cm/mm/m/yd/w/kw/v/a/pcs/pc/oz/lb/ml/l/tex/dtex/rpm/nm`）
- 旦尼尔根数：`3D/12F`
- Excel 单元格：1 到 3 个字母再接行号，例如 `I413`、`K294`、`T15`、`J623`、`AA10`

仍然出题的是：货号（至少两位数字 + 字母 + 至少两位数字，如 `25YK00022`）、带 `-` `/` `_` 且至少 2 个字母和 2 个数字的面料编号（如 `C100-40S`）、长度至少 5 且不是单元格的型号（如 `M1YK010M`、`M2SW2516`、`ZX9K2`）。

`K294`、`T15` 不再进入评测题，避免和单元格坐标混在一起。用户直接问「型号 K294」时，稀疏查询仍然只送 `k294`。

## 在 Windows 上重跑评测

在仓库根目录打开 PowerShell。不要用 `mvnw.cmd ... exec:java "-Dexec.args=..."`：PowerShell 直接跑和 `cmd /c` 都会把参数拆碎，Maven 只打印 usage。`mvnw.cmd` 为路径含空格做过的修改解决不了这件事。脚本先执行 `dependency:build-classpath`，再 `java -cp "target\classes;<依赖>"`，参数不经过 Maven。

不需要启动 Spring Boot，不连 Postgres，没有要手工执行的 SQL。不读、不写、不删 `salesperson_docs`。影子集合只读。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\hybrid-eval.ps1
```

默认参数就是上次那一批的设置：`localhost:19530`、原集合 `salesperson_docs`、影子集合 `salesperson_docs_hybrid`、Ollama `http://localhost:11434` 的 `bge-m3`、1024 维、编号题 40、中文题 15、`seed=25`、`top-k=10`。报告写到 `backend\hybrid-eval-report.md`。查询向量现算，不写回 Milvus。

只要确认两边行数、不写任何集合，可以先看一眼：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\hybrid-backfill.ps1 -DryRun
```

`-DryRun` 只打印行数。不要去掉 `-DryRun`，否则会删掉并重建 `salesperson_docs_hybrid`。这次的修复在查询侧，已回填的影子集合可以继续用。

打开 `backend\hybrid-eval-report.md`。里面有每种方式的 Recall@5、Recall@10、MRR 和延迟（均值 / P50 / P95），并按货号、面料编号、型号、中文拆开。

货号类问题：返回正文里包含该编号就算命中。中文题：必须命中抽样的那一条 `doc_id + chunk_index`。影子集合的 `chunk_id` 是新的自增主键，不能拿来对原集合。

数字可以接受之后，再打开开关并在 IntelliJ IDEA 里重启后端：

```powershell
$env:MILVUS_HYBRID_ENABLED = "true"
```

或在本地 yml 把 `milvus.hybrid.enabled` 设为 `true`。未打开时，应用仍读 `salesperson_docs`，行为和现在一样。

IntelliJ 也可以直接运行这两个 main class，工作目录选 `backend`。评测的程序参数是 `--host=localhost --port=19530 --source=salesperson_docs --target=salesperson_docs_hybrid --embed-url=http://localhost:11434 --embed-model=bge-m3 --dim=1024 --sample-codes=40 --sample-chinese=15 --seed=25 --top-k=10 --output=hybrid-eval-report.md`。不要用 Spring Boot 的主类来跑它们，也不要经过 `exec:java` 的 `-Dexec.args`。

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

Windows 上用同一支脚本的 `-Demo`，只读写 `hybrid_smoke_` 临时集合，不读 `salesperson_docs`：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\hybrid-eval.ps1 -Demo
```

Linux / macOS 的 bash 引号能保住 `exec.args`，可以用：

```bash
cd backend
./mvnw -DskipTests compile exec:java -Dexec.mainClass=com.imagemanager.milvus.tools.HybridCompareEvalMain -Dexec.args="--demo --output=target/hybrid-smoke-report.md"
```
