# RAG 评测

评测走对话检索的真实链路：`KeywordExtractor`、`QueryEnhancer`、`RagPipeline`（内部还会再调一次查询增强和重排序）、`Reranker`。指标是召回命中率、引用正确率、拒答正确率、平均延迟和 P95 延迟。报告写成 Markdown 和 JSON。

仓库里只放了标明 `EXAMPLE` 的示例题，不是业务数据。真实问题由管理员按下面的格式另加。

## 题目格式

文件放在 `backend/src/test/resources/rag-eval/`，扩展名 `.jsonl`，一行一道题：

```json
{"question":"货号 AB1234 的下机克重是多少？","expectedFacts":["下机克重"],"keywords":["下机克重"],"expectedSourceIds":["工艺单主键或知识文档id"],"huohao":"AB1234","shouldRefuse":false,"company":"宝娜斯集团"}
{"question":"并不存在的客户去年采购额？","keywords":["并不存在的客户"],"shouldRefuse":true,"company":"宝娜斯集团"}
```

| 字段 | 说明 |
| --- | --- |
| `question` | 原问题 |
| `expectedFacts` | 希望命中的原文片段，可空 |
| `keywords` | 关键词，可空 |
| `expectedSourceIds` | 知识切片/文档 id、ERP 主键或历史问答 id。给了这个字段时，引用正确要求这些 id 全部被召回 |
| `huohao` | 货号。没有来源 id 时，用货号或关键词判断召回和首条引用 |
| `shouldRefuse` | `true` 表示知识库和 ERP 里不该有这条信息，检索为空才算拒答正确 |
| `company` | 租户，检索按这个公司过滤 |
| `example` | 示例题写 `true`。真实业务题不要标 `example`，题干里也不要写 EXAMPLE |

答错的问题可以在知识库「待补充」里点「导出评测题」，得到同一格式的 jsonl（`expectedFacts` 为空，`expectedSourceIds` 来自当时的引用来源）。把文件放进上面的目录后再跑评测。

## 怎么跑

在仓库根目录：

```bash
cd backend
mvn test -Dgroups=rag-eval -Dsurefire.excludedGroups=
```

默认 `mvn test` 排除 `@Tag("rag-eval")`，避免构建去连数据库、向量库和重排序服务。

跑完后看：

- `backend/target/rag-eval/report.md`
- `backend/target/rag-eval/report.json`

管理员也可以在已登录的情况下调用 `POST /api/chat/rag-eval`（仅 admin / superadmin）。进程工作目录下如果有 `src/test/resources/rag-eval` 或 `backend/src/test/resources/rag-eval`，会读取其中全部 jsonl；否则读 classpath 里的 `rag-eval/example.jsonl`。打样作用域会话不能调用。

## 指标

- **召回命中率**：非拒答题里，检索结果命中任一期望来源 id、货号、事实或关键词的比例。没有这些期望的题不进分母。
- **引用正确率**：给了 `expectedSourceIds` 时，这些 id 要全部出现。只给了货号或关键词时，看排名第一的结果是否包含它们。
- **拒答正确率**：`shouldRefuse=true` 的题，检索结果为空才算对。没有拒答题时报告写「无拒答样本」。
- **延迟**：每题检索耗时的平均值和 P95。

## 混合检索和纯稠密对比

默认 `mvn test` 里的 `RagHybridDenseCompareTest` 用同一套 `RagEvalScorer`，对「货号只在稀疏通道排第一、稠密通道把它滤掉」的情形出一份对照表：纯稠密召回不到货号，RRF 混合检索能召回。融合公式和线上 `RRFRanker(k=60)` 相同。

线上要拿真实题库对比：先保持 `MILVUS_HYBRID_ENABLED=false` 跑

```bash
cd backend
mvn test -Dgroups=rag-eval -Dsurefire.excludedGroups=
```

把 `backend/target/rag-eval/` 拷走。再按 `docs/milvus-hybrid.md` 回填并打开开关，重跑同一条命令。两份 `report.md` 的召回命中率、引用正确率、拒答正确率可以直接比。开关只改变 Milvus 召回，评测入口、拒答规则和引用编号不变。
