# Milvus 混合检索

默认关闭。关闭时对话检索、货号 ILIKE、ERP 工具路径和现在一样。

稠密向量是本地 Ollama `bge-m3`（1024 维），不是云端嵌入。Java SDK 是 `io.milvus:milvus-sdk-java:2.5.6`。

## 各库存哪

| 来源 | 存储 |
| --- | --- |
| 业务员资料 | Milvus 稠密集合。代码读 `milvus.collection`，默认 `salesperson_docs`。`application.yml` 的 `milvus.collection-name` 没有绑到这个字段 |
| 知识库文档 | pgvector `knowledge_embeddings`（`source_type=KNOWLEDGE_BASE`），上传时顺带写入同一个 Milvus 集合 |
| 历史问答 | 只在 pgvector，`source_type=SMART_CHAT` |
| 商品库 | PostgreSQL `goods_library`，没有向量 |
| ERP / 工艺单 / BOM / 报价 | PostgreSQL 业务表，结构化 SQL |

混合集合是旁边的新集合，默认名 `{稠密集合}_hybrid`（默认即 `salesperson_docs_hybrid`）。稠密集合的字段和索引不动。

## 服务器版本

混合检索需要 **Milvus Server 2.5.0 及以上**，建议 **2.5.6**，和 Java SDK 对齐。要有 BM25 Function、VARCHAR analyzer 和 hybridSearch。

低于 2.5：不要设置 `MILVUS_HYBRID_ENABLED=true`。创建混合集合会失败，检索自动留在稠密 HNSW，货号仍走 `KeywordExtractor` 的 ILIKE，ERP 工具路径不动。升级 standalone 或集群到 2.5.6 之后，再执行下面的回填。

## 打开

1. 确认 Milvus 已启动，版本不低于 2.5。
2. 用管理员账号调用 `POST /api/admin/milvus/hybrid/rebuild`。这个接口可以重复执行：每次删掉混合集合，再从稠密集合把正文和向量抄过去，并重新生成 BM25。不需要跑 Flyway，也没有要手工执行的 SQL。
3. 用 `GET /api/admin/milvus/hybrid` 看 `rebuildState=done`，以及 `inserted`。
4. 设置 `MILVUS_HYBRID_ENABLED=true`（或在本地 yml 里把 `milvus.hybrid.enabled` 设为 true）。
5. 重启 Java 进程。

重建过程中检索会暂时回到稠密集合。混合集合还是空的时候，混合检索没有结果也会回退稠密检索。

新写入的切片在混合集合已经存在时会双写。如果在回填之后、打开开关之前又导入了大批文件，再调一次重建接口即可。

## 回滚

把 `MILVUS_HYBRID_ENABLED` 设为 `false` 并重启。稠密集合还在，货号 ILIKE 和 ERP 路径按原来的顺序工作。混合集合可以留着，也可以在 Milvus 里删掉 `{collection}_hybrid`。

## 排序

默认 RRF，k=60，和 Milvus `RRFRanker` 相同。余弦分和 BM25 分不是一个尺度，WeightedRanker 要手调权重；没有线上评测集时 RRF 更稳，货号在稀疏通道排第一时不会被 0.35 的余弦阈值丢掉。

要改成加权：`MILVUS_HYBRID_RANKER=weighted`，再用 `MILVUS_HYBRID_DENSE_WEIGHT` 和 `MILVUS_HYBRID_SPARSE_WEIGHT`。权重顺序是先稠密、后稀疏。

中文和货号不走 jieba。写入前把货号、型号、面料编号整段留下来，Milvus 只对空格做分词，所以 `25YK00022`、`C100-40S` 不会被切开。
