package com.imagemanager.milvus.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.imagemanager.milvus.HybridCollectionGuard;
import com.imagemanager.milvus.HybridCompareEval;
import com.imagemanager.milvus.MilvusDenseSchema;
import com.imagemanager.milvus.MilvusHybridSchema;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 在临时集合里放假数据，走正式回填，再跑对比评测。
 * 只创建和删除 hybrid_smoke_ 前缀的集合，不会碰 salesperson_docs。
 */
public final class HybridPipelineDemo {

    public static final String SOURCE = "hybrid_smoke_src";
    public static final String TARGET = "hybrid_smoke_hybrid";
    private static final int DIM = 32;

    private HybridPipelineDemo() {
    }

    public record Result(Path report, long sourceRows, long shadowRows,
                         HybridCompareEval.Summary dense, HybridCompareEval.Summary hybrid,
                         HybridCompareEval.Summary denseCode, HybridCompareEval.Summary hybridCode,
                         HybridCompareEval.Summary denseChinese, HybridCompareEval.Summary hybridChinese) {
    }

    public static Result run(String host, int port, Path report) throws Exception {
        HybridCollectionGuard.assertSmokeName(SOURCE);
        HybridCollectionGuard.assertSmokeName(TARGET);
        HybridCollectionGuard.assertShadowTarget(SOURCE, TARGET);
        MilvusClientV2 client = MilvusToolClient.connect(host, port);
        try {
            dropSmoke(client, SOURCE);
            dropSmoke(client, TARGET);
            MilvusDenseSchema.create(client, SOURCE, DIM);
            List<JsonObject> rows = seedRows();
            MilvusDenseSchema.insert(client, SOURCE, rows);
            MilvusHybridSchema.flush(client, SOURCE);
            long sourceBefore = awaitCount(client, SOURCE, rows.size());

            MilvusHybridSchema.RebuildStats first = copyOnce(client);
            MilvusHybridSchema.RebuildStats second = copyOnce(client);
            long sourceAfter = awaitCount(client, SOURCE, rows.size());
            long shadowSecond = awaitCount(client, TARGET, rows.size());
            if (sourceBefore != sourceAfter) {
                throw new IllegalStateException("演练改动了原集合行数 " + sourceBefore + " -> " + sourceAfter);
            }
            if (first.inserted != rows.size() || second.inserted != rows.size() || first.scanned != rows.size()) {
                throw new IllegalStateException("回填计数不一致 first=" + first.inserted
                        + " second=" + second.inserted + " scanned=" + first.scanned
                        + " err=" + first.lastError);
            }

            List<HybridCompareEval.Chunk> chunks = new ArrayList<>();
            for (JsonObject row : rows) {
                chunks.add(new HybridCompareEval.Chunk(
                        row.get("doc_id").getAsString(),
                        row.get("chunk_index").getAsInt(),
                        row.get("content").getAsString()));
            }
            List<HybridCompareEval.Question> questions = HybridCompareEval.sample(chunks, 10, 5, 25);
            List<HybridCompareEval.Trial> denseTrials = new ArrayList<>();
            List<HybridCompareEval.Trial> hybridTrials = new ArrayList<>();
            client.loadCollection(io.milvus.v2.service.collection.request.LoadCollectionReq.builder()
                    .collectionName(SOURCE).build());
            client.loadCollection(io.milvus.v2.service.collection.request.LoadCollectionReq.builder()
                    .collectionName(TARGET).build());
            for (HybridCompareEval.Question question : questions) {
                float[] vector = queryVector(question);
                long denseStart = System.nanoTime();
                List<MilvusHybridSchema.HybridHit> denseHits = MilvusDenseSchema.search(
                        client, SOURCE, vector, 10, ConsistencyLevel.STRONG);
                long denseNanos = System.nanoTime() - denseStart;
                long hybridStart = System.nanoTime();
                List<MilvusHybridSchema.HybridHit> hybridHits = MilvusHybridSchema.search(
                        client, TARGET, vector, question.text(), 10, "rrf", 60, 0.5f, 0.5f, ConsistencyLevel.STRONG);
                long hybridNanos = System.nanoTime() - hybridStart;
                denseTrials.add(new HybridCompareEval.Trial(question, toHits(denseHits), denseNanos));
                hybridTrials.add(new HybridCompareEval.Trial(question, toHits(hybridHits), hybridNanos));
            }
            expectRecall("混合货号", HybridCompareEval.summarize("混合", "product", hybridTrials));
            expectRecall("混合面料编号", HybridCompareEval.summarize("混合", "fabric", hybridTrials));
            expectRecall("混合型号", HybridCompareEval.summarize("混合", "model", hybridTrials));
            expectRecall("混合中文", HybridCompareEval.summarize("混合", "chinese", hybridTrials));
            expectRecall("稠密中文", HybridCompareEval.summarize("稠密", "chinese", denseTrials));
            expectMiss("稠密货号", HybridCompareEval.summarize("稠密", "product", denseTrials));
            expectMiss("稠密面料编号", HybridCompareEval.summarize("稠密", "fabric", denseTrials));
            expectMiss("稠密型号", HybridCompareEval.summarize("稠密", "model", denseTrials));
            String markdown = HybridCompareEval.markdown(
                    SOURCE,
                    TARGET,
                    "假数据自带向量（编号题故意用无关向量，中文题用该切片自己的向量）",
                    "这是 Milvus 2.6 上的假数据演练，用来确认回填和评测能跑通。不是线上 7604 行的结果。",
                    denseTrials,
                    hybridTrials);
            Path parent = report.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(report, markdown);
            return new Result(
                    report,
                    sourceAfter,
                    shadowSecond,
                    HybridCompareEval.summarize("稠密", "all", denseTrials),
                    HybridCompareEval.summarize("混合", "all", hybridTrials),
                    HybridCompareEval.summarize("稠密", "product", denseTrials),
                    HybridCompareEval.summarize("混合", "product", hybridTrials),
                    HybridCompareEval.summarize("稠密", "chinese", denseTrials),
                    HybridCompareEval.summarize("混合", "chinese", hybridTrials));
        } finally {
            try {
                dropSmoke(client, SOURCE);
                dropSmoke(client, TARGET);
            } catch (RuntimeException ignored) {
                // 清理失败不影响已经写出的报告
            }
            client.close();
        }
    }

    private static MilvusHybridSchema.RebuildStats copyOnce(MilvusClientV2 client) throws InterruptedException {
        MilvusHybridSchema.dropShadow(client, SOURCE, TARGET);
        MilvusHybridSchema.createCollection(client, TARGET, DIM);
        MilvusHybridSchema.RebuildStats stats = MilvusHybridSchema.backfill(client, SOURCE, TARGET, 2, null);
        if (stats.failed > 0 || stats.lastError != null) {
            throw new IllegalStateException("回填失败: " + stats.lastError);
        }
        MilvusHybridSchema.flush(client, TARGET);
        return stats;
    }

    private static long awaitCount(MilvusClientV2 client, String name, long expected) throws InterruptedException {
        long count = -1;
        for (int i = 0; i < 20; i++) {
            count = MilvusHybridSchema.rowCount(client, name);
            if (count == expected) {
                return count;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException(name + " 行数 " + count + "，期望 " + expected);
    }

    private static void dropSmoke(MilvusClientV2 client, String name) {
        HybridCollectionGuard.assertSmokeName(name);
        MilvusHybridSchema.dropIfExists(client, name);
    }

    private static List<HybridCompareEval.Hit> toHits(List<MilvusHybridSchema.HybridHit> hits) {
        List<HybridCompareEval.Hit> out = new ArrayList<>();
        for (MilvusHybridSchema.HybridHit hit : hits) {
            out.add(new HybridCompareEval.Hit(hit.docId(), hit.chunkIndex(), hit.content()));
        }
        return out;
    }

    private static void expectRecall(String label, HybridCompareEval.Summary summary) {
        if (summary.questions() < 1 || summary.recallAt5() < 1.0) {
            throw new IllegalStateException(label + " Recall@5=" + summary.recallAt5()
                    + " 题数=" + summary.questions());
        }
    }

    private static void expectMiss(String label, HybridCompareEval.Summary summary) {
        if (summary.questions() < 1 || summary.recallAt10() > 0) {
            throw new IllegalStateException(label + " Recall@10=" + summary.recallAt10()
                    + " 题数=" + summary.questions() + "，预期稠密通道找不到编号");
        }
    }

    private static float[] queryVector(HybridCompareEval.Question question) {
        if ("chinese".equals(question.kind())) {
            return vectorFor(question.goldDocId());
        }
        return axis(0);
    }

    private static float[] vectorFor(String docId) {
        if ("doc-code".equals(docId)) {
            return axis(1);
        }
        if ("doc-fabric".equals(docId)) {
            return axis(2);
        }
        if ("doc-model".equals(docId)) {
            return axis(3);
        }
        if ("doc-zh".equals(docId)) {
            return axis(4);
        }
        if ("doc-zh2".equals(docId)) {
            return axis(5);
        }
        if (docId != null && docId.startsWith("doc-noise-")) {
            return near(Integer.parseInt(docId.substring("doc-noise-".length())));
        }
        return axis(0);
    }

    private static List<JsonObject> seedRows() {
        List<JsonObject> rows = new ArrayList<>();
        rows.add(denseRow("doc-code", 0, "工艺单 货号25YK00022 采用棉氨面料", axis(1)));
        rows.add(denseRow("doc-fabric", 0, "面料编号C100-40S 用于夏季针织", axis(2)));
        rows.add(denseRow("doc-model", 0, "型号ZX9K2 机台参数说明", axis(3)));
        rows.add(denseRow("doc-zh", 0, "棉质面料需要低温洗涤避免缩水", axis(4)));
        rows.add(denseRow("doc-zh2", 0, "包装纸箱需要防潮并标明批次", axis(5)));
        for (int i = 0; i < 15; i++) {
            rows.add(denseRow("doc-noise-" + i, 0, "note", near(i)));
        }
        return rows;
    }

    private static JsonObject denseRow(String docId, int chunkIndex, String content, float[] embedding) {
        JsonObject row = new JsonObject();
        row.addProperty("doc_id", docId);
        row.addProperty("file_name", docId + ".txt");
        row.addProperty("doc_type", "txt");
        row.addProperty("chunk_index", chunkIndex);
        row.addProperty("content", content);
        JsonArray vector = new JsonArray();
        for (float value : embedding) {
            vector.add(value);
        }
        row.add("embedding", vector);
        return row;
    }

    private static float[] axis(int hot) {
        float[] vector = new float[DIM];
        vector[hot] = 1f;
        return vector;
    }

    /** 靠近第 0 轴，余弦高于正交的货号向量，用来占满稠密检索的前 10 名。 */
    private static float[] near(int salt) {
        float[] vector = new float[DIM];
        vector[0] = 1f;
        vector[6 + (salt % 20)] = 0.08f;
        double sum = 0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
        return vector;
    }
}
