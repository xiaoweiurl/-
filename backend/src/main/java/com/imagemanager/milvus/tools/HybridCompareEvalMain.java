package com.imagemanager.milvus.tools;

import com.imagemanager.milvus.HybridCollectionGuard;
import com.imagemanager.milvus.HybridCompareEval;
import com.imagemanager.milvus.MilvusDenseSchema;
import com.imagemanager.milvus.MilvusHybridSchema;
import com.imagemanager.milvus.OllamaEmbedClient;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.QueryIteratorReq;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用同一批从原集合抽样的问题，比较稠密检索和影子集合上的混合检索。
 * 只读两个集合，不写 Postgres。问题向量向本机 Ollama 现算，不写回 Milvus。
 *
 * <p>Windows 上不要用 {@code mvnw.cmd exec:java -Dexec.args}。用仓库里的 {@code scripts/hybrid-eval.ps1}。
 */
public final class HybridCompareEvalMain {

    private HybridCompareEvalMain() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = ToolArgs.parse(args);
        if (ToolArgs.flag(options, "help")) {
            System.out.println("""
                    用法: HybridCompareEvalMain --source=salesperson_docs --target=salesperson_docs_hybrid --embed-url=http://localhost:11434 --embed-model=bge-m3 --output=hybrid-eval-report.md
                    --demo  只用 hybrid_smoke_ 临时集合跑假数据，不读 salesperson_docs
                    正式评测只读 source 和 target，不删除集合，不连接 Postgres。
                    """);
            return;
        }
        String host = ToolArgs.text(options, "host", "localhost");
        int port = ToolArgs.integer(options, "port", 19530);
        if (ToolArgs.flag(options, "demo")) {
            Path output = Path.of(ToolArgs.text(options, "output", "hybrid-smoke-report.md"));
            HybridPipelineDemo.Result result = HybridPipelineDemo.run(host, port, output);
            System.out.println("假数据演练报告: " + result.report().toAbsolutePath());
            System.out.println("稠密 Recall@5=" + result.dense().recallAt5() + " 混合 Recall@5=" + result.hybrid().recallAt5());
            return;
        }
        String source = ToolArgs.text(options, "source", "salesperson_docs");
        String target = ToolArgs.text(options, "target", source + "_hybrid");
        String embedUrl = ToolArgs.text(options, "embed-url", "http://localhost:11434");
        String embedModel = ToolArgs.text(options, "embed-model", "bge-m3");
        int dim = ToolArgs.integer(options, "dim", 1024);
        int codeLimit = ToolArgs.integer(options, "sample-codes", 40);
        int chineseLimit = ToolArgs.integer(options, "sample-chinese", 15);
        long seed = ToolArgs.integer(options, "seed", 25);
        int topK = ToolArgs.integer(options, "top-k", 10);
        Path output = Path.of(ToolArgs.text(options, "output", "hybrid-eval-report.md"));
        HybridCollectionGuard.assertShadowTarget(source, target);

        System.out.println("只读原集合 " + source + " 和影子集合 " + target + "。不连接 Postgres。");
        MilvusClientV2 client = MilvusToolClient.connect(host, port);
        try {
            if (!MilvusHybridSchema.exists(client, source)) {
                throw new IllegalStateException("原集合不存在: " + source);
            }
            if (!MilvusHybridSchema.exists(client, target)) {
                throw new IllegalStateException("影子集合不存在: " + target + "。先运行 HybridBackfillMain。");
            }
            client.loadCollection(LoadCollectionReq.builder().collectionName(source).build());
            client.loadCollection(LoadCollectionReq.builder().collectionName(target).build());
            long sourceRows = MilvusHybridSchema.rowCount(client, source);
            long targetRows = MilvusHybridSchema.rowCount(client, target);
            if (targetRows <= 0) {
                throw new IllegalStateException("影子集合是空的，先运行 HybridBackfillMain。");
            }
            List<HybridCompareEval.Chunk> chunks = readChunks(client, source);
            List<HybridCompareEval.Question> questions = HybridCompareEval.sample(chunks, codeLimit, chineseLimit, seed);
            if (questions.isEmpty()) {
                throw new IllegalStateException("没有抽到问题。确认原集合里有正文。");
            }
            System.out.println("原集合 " + sourceRows + " 行，影子集合 " + targetRows + " 行，抽到 " + questions.size() + " 题。");
            OllamaEmbedClient embedder = new OllamaEmbedClient();
            Map<String, float[]> vectors = new LinkedHashMap<>();
            for (HybridCompareEval.Question question : questions) {
                vectors.computeIfAbsent(question.text(), text -> {
                    float[] vector = embedder.embed(embedUrl, embedModel, text);
                    if (vector.length != dim) {
                        throw new IllegalStateException("嵌入维度是 " + vector.length + "，参数 dim=" + dim);
                    }
                    return vector;
                });
            }
            List<HybridCompareEval.Trial> denseTrials = new ArrayList<>();
            List<HybridCompareEval.Trial> hybridTrials = new ArrayList<>();
            for (HybridCompareEval.Question question : questions) {
                float[] vector = vectors.get(question.text());
                long denseStart = System.nanoTime();
                List<MilvusHybridSchema.HybridHit> denseHits = MilvusDenseSchema.search(
                        client, source, vector, topK, ConsistencyLevel.BOUNDED);
                long denseNanos = System.nanoTime() - denseStart;
                long hybridStart = System.nanoTime();
                List<MilvusHybridSchema.HybridHit> hybridHits = MilvusHybridSchema.search(
                        client, target, vector, question.text(), topK, "rrf", 60, 0.5f, 0.5f, ConsistencyLevel.BOUNDED);
                long hybridNanos = System.nanoTime() - hybridStart;
                denseTrials.add(new HybridCompareEval.Trial(question, toHits(denseHits), denseNanos));
                hybridTrials.add(new HybridCompareEval.Trial(question, toHits(hybridHits), hybridNanos));
            }
            String markdown = HybridCompareEval.markdown(
                    source,
                    target,
                    "Ollama " + embedModel + " @ " + embedUrl,
                    "问题从原集合正文抽样。含编号的切片用来出货号/面料编号/型号题，不含编号的切片用来出中文题。",
                    denseTrials,
                    hybridTrials);
            Files.writeString(output, markdown);
            HybridCompareEval.Summary dense = HybridCompareEval.summarize("稠密", "all", denseTrials);
            HybridCompareEval.Summary hybrid = HybridCompareEval.summarize("混合", "all", hybridTrials);
            System.out.println("报告: " + output.toAbsolutePath());
            System.out.println("稠密 Recall@5=" + dense.recallAt5() + " Recall@10=" + dense.recallAt10()
                    + " MRR=" + dense.mrr());
            System.out.println("混合 Recall@5=" + hybrid.recallAt5() + " Recall@10=" + hybrid.recallAt10()
                    + " MRR=" + hybrid.mrr());
        } finally {
            client.close();
        }
    }

    private static List<HybridCompareEval.Chunk> readChunks(MilvusClientV2 client, String source) {
        List<HybridCompareEval.Chunk> chunks = new ArrayList<>();
        // 输出里必须带 chunk_id，QueryIterator 才不会在同一页上打转。
        QueryIterator iterator = client.queryIterator(QueryIteratorReq.builder()
                .collectionName(source)
                .expr("chunk_id >= 0")
                .outputFields(List.of("chunk_id", "doc_id", "chunk_index", "content"))
                .batchSize(200)
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build());
        try {
            long cursor = Long.MIN_VALUE;
            while (true) {
                List<QueryResultsWrapper.RowRecord> page = iterator.next();
                if (page == null || page.isEmpty()) {
                    break;
                }
                boolean advanced = false;
                long pageMax = cursor;
                for (QueryResultsWrapper.RowRecord record : page) {
                    long pk = record.get("chunk_id") instanceof Number number
                            ? number.longValue() : Long.MIN_VALUE;
                    if (pk > cursor) {
                        advanced = true;
                    }
                    if (pk > pageMax) {
                        pageMax = pk;
                    }
                }
                if (!advanced) {
                    break;
                }
                cursor = pageMax;
                for (QueryResultsWrapper.RowRecord record : page) {
                    Object docId = record.get("doc_id");
                    Object content = record.get("content");
                    Object chunkIndex = record.get("chunk_index");
                    int index = chunkIndex instanceof Number number ? number.intValue() : 0;
                    chunks.add(new HybridCompareEval.Chunk(
                            docId == null ? "" : docId.toString(),
                            index,
                            content == null ? "" : content.toString()));
                }
            }
        } finally {
            iterator.close();
        }
        return chunks;
    }

    private static List<HybridCompareEval.Hit> toHits(List<MilvusHybridSchema.HybridHit> hits) {
        List<HybridCompareEval.Hit> out = new ArrayList<>();
        for (MilvusHybridSchema.HybridHit hit : hits) {
            out.add(new HybridCompareEval.Hit(hit.docId(), hit.chunkIndex(), hit.content()));
        }
        return out;
    }
}
