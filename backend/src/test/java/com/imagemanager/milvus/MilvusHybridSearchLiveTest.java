package com.imagemanager.milvus;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连真实 Milvus 做一次混合检索。默认 mvn test 不跑。
 * 设置 MILVUS_IT=true，并保证 MILVUS_HOST / MILVUS_PORT 上是 2.5+ standalone。
 */
@EnabledIfEnvironmentVariable(named = "MILVUS_IT", matches = "true")
class MilvusHybridSearchLiveTest {

    @Test
    void sparseChannelRecallsProductCodeAheadOfDenseNeighbor() {
        String host = System.getenv().getOrDefault("MILVUS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("MILVUS_PORT", "19530"));
        String collection = "hybrid_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder()
                .uri("http://" + host + ":" + port)
                .connectTimeoutMs(8000)
                .build());
        try {
            int dim = 8;
            MilvusHybridSchema.createCollection(client, collection, dim);
            float[] codeVector = vector(dim, 0);
            float[] otherVector = vector(dim, 1);
            MilvusHybridSchema.insert(client, collection, List.of(
                    row("doc-code", "工艺单 货号25YK00022 面料编号C100-40S", codeVector),
                    row("doc-other", "棉质面料洗涤注意事项", otherVector)));

            List<MilvusHybridSchema.HybridHit> hits = MilvusHybridSchema.search(
                    client,
                    collection,
                    otherVector,
                    "25YK00022",
                    2,
                    "rrf",
                    60,
                    0.5f,
                    0.5f,
                    ConsistencyLevel.STRONG);
            assertFalse(hits.isEmpty(), "混合检索没有返回结果");
            assertEquals("doc-code", hits.get(0).docId());
            assertTrue(hits.get(0).content().contains("25YK00022"));
            assertEquals("rrf", hits.get(0).scoreMetric());
        } finally {
            try {
                MilvusHybridSchema.dropIfExists(client, collection);
            } catch (RuntimeException ignored) {
                // 清理失败不影响断言
            }
            client.close();
        }
    }

    private static com.google.gson.JsonObject row(String docId, String content, float[] embedding) {
        com.google.gson.JsonObject row = new com.google.gson.JsonObject();
        row.addProperty("doc_id", docId);
        row.addProperty("file_name", docId + ".txt");
        row.addProperty("doc_type", "txt");
        row.addProperty("chunk_index", 0);
        row.addProperty("content", content);
        com.google.gson.JsonArray vector = new com.google.gson.JsonArray();
        for (float value : embedding) {
            vector.add(value);
        }
        row.add("embedding", vector);
        return MilvusHybridSchema.hybridRowFromDense(row);
    }

    private static float[] vector(int dim, int hot) {
        float[] vector = new float[dim];
        vector[hot] = 1f;
        return vector;
    }
}
