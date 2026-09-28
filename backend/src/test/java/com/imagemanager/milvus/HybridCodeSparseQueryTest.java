package com.imagemanager.milvus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 问句里的「型号」会命中很多无关切片。编号本身必须仍能进混合检索前几名。
 * 默认 mvn test 不跑，设置 MILVUS_IT=true 且本机有 Milvus 2.5+ 才跑。
 */
@EnabledIfEnvironmentVariable(named = "MILVUS_IT", matches = "true")
class HybridCodeSparseQueryTest {

    @Test
    void codeTokenIsNotCrowdedOutByTheWordModel() {
        String host = System.getenv().getOrDefault("MILVUS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("MILVUS_PORT", "19530"));
        String collection = "hybrid_diag_codes";
        MilvusClientV2 client = new MilvusClientV2(ConnectConfig.builder()
                .uri("http://" + host + ":" + port)
                .connectTimeoutMs(8000)
                .build());
        try {
            MilvusHybridSchema.dropIfExists(client, collection);
            MilvusHybridSchema.createCollection(client, collection, 8);
            String longPrefix = "工艺说明".repeat(400);
            MilvusHybridSchema.insert(client, collection, List.of(
                    row("doc-k294", longPrefix + "机台型号K294参数表"),
                    row("doc-m1", "型号M1YK010M用于夏季"),
                    row("doc-t15", longPrefix + "参见T15"),
                    row("doc-fabric", "面料编号C100-40S用于针织"),
                    row("doc-huohao", "货号25YK00022棉氨")));
            for (int i = 0; i < 60; i++) {
                MilvusHybridSchema.insert(client, collection, List.of(
                        row("noise-" + i, "型号说明会议纪要条目" + i)));
            }
            MilvusHybridSchema.flush(client, collection);
            assertInTop(client, collection, "型号 K294", "doc-k294");
            assertInTop(client, collection, "型号 M1YK010M", "doc-m1");
            assertInTop(client, collection, "型号 T15", "doc-t15");
            assertInTop(client, collection, "面料编号 C100-40S", "doc-fabric");
            assertInTop(client, collection, "货号 25YK00022", "doc-huohao");
        } finally {
            try {
                MilvusHybridSchema.dropIfExists(client, collection);
            } catch (RuntimeException ignored) {
                // 清理失败不影响断言
            }
            client.close();
        }
    }

    private static void assertInTop(MilvusClientV2 client, String collection, String query, String docId) {
        List<MilvusHybridSchema.HybridHit> hits = MilvusHybridSchema.search(
                client, collection, hot(0), query, 10, "rrf", 60, 0.5f, 0.5f, ConsistencyLevel.STRONG);
        assertTrue(hits.stream().limit(5).anyMatch(hit -> docId.equals(hit.docId())),
                query + " 前 5 名没有 " + docId + "：" + hits.stream().map(MilvusHybridSchema.HybridHit::docId).toList());
    }

    private static JsonObject row(String docId, String content) {
        JsonObject row = new JsonObject();
        row.addProperty("doc_id", docId);
        row.addProperty("file_name", docId + ".txt");
        row.addProperty("doc_type", "txt");
        row.addProperty("chunk_index", 0);
        row.addProperty("content", content);
        JsonArray vector = new JsonArray();
        float[] embedding = docId.startsWith("noise-") || "doc-noise".equals(docId) ? hot(0) : hot(1);
        for (float value : embedding) {
            vector.add(value);
        }
        row.add("embedding", vector);
        return MilvusHybridSchema.hybridRowFromDense(row);
    }

    private static float[] hot(int index) {
        float[] vector = new float[8];
        vector[index] = 1f;
        return vector;
    }
}
