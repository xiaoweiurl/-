package com.imagemanager.milvus;

import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 和线上 salesperson_docs 相同的稠密 schema：没有 analyzer，没有 BM25 Function。
 * 只给假数据演练建临时集合，应用启动不会用它去创建 salesperson_docs。
 */
public final class MilvusDenseSchema {

    private static final List<String> OUTPUT_FIELDS = List.of(
            "doc_id", "file_name", "doc_type", "chunk_index", "content");

    private MilvusDenseSchema() {
    }

    public static void create(MilvusClientV2 client, String collectionName, int dimension) {
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_id")
                .dataType(DataType.Int64)
                .isPrimaryKey(true)
                .autoID(true)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("doc_id")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("file_name")
                .dataType(DataType.VarChar)
                .maxLength(1024)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("doc_type")
                .dataType(DataType.VarChar)
                .maxLength(16)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_index")
                .dataType(DataType.Int32)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("content")
                .dataType(DataType.VarChar)
                .maxLength(8192)
                .build());
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding")
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build());
        List<IndexParam> indexes = List.of(
                IndexParam.builder()
                        .fieldName("embedding")
                        .indexType(IndexParam.IndexType.HNSW)
                        .metricType(IndexParam.MetricType.COSINE)
                        .extraParams(Map.of("M", 16, "efConstruction", 200))
                        .build(),
                IndexParam.builder()
                        .fieldName("file_name")
                        .indexType(IndexParam.IndexType.TRIE)
                        .build(),
                IndexParam.builder()
                        .fieldName("doc_type")
                        .indexType(IndexParam.IndexType.TRIE)
                        .build()
        );
        client.createCollection(CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .indexParams(indexes)
                .enableDynamicField(false)
                .build());
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName)
                .build());
    }

    public static void insert(MilvusClientV2 client, String collectionName, List<JsonObject> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        client.insert(InsertReq.builder()
                .collectionName(collectionName)
                .data(rows)
                .build());
    }

    public static List<MilvusHybridSchema.HybridHit> search(MilvusClientV2 client,
                                                            String collectionName,
                                                            float[] embedding,
                                                            int topK,
                                                            ConsistencyLevel consistency) {
        int limit = topK > 0 ? topK : 10;
        List<Float> vector = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            vector.add(value);
        }
        SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField("embedding")
                .metricType(IndexParam.MetricType.COSINE)
                .data(List.of(new FloatVec(vector)))
                .topK(limit)
                .outputFields(OUTPUT_FIELDS)
                .searchParams(Map.of("ef", 128));
        if (consistency != null) {
            builder.consistencyLevel(consistency);
        }
        SearchResp resp = client.search(builder.build());
        if (resp == null || resp.getSearchResults() == null || resp.getSearchResults().isEmpty()) {
            return List.of();
        }
        List<MilvusHybridSchema.HybridHit> out = new ArrayList<>();
        for (SearchResp.SearchResult result : resp.getSearchResults().get(0)) {
            Map<String, Object> entity = result.getEntity();
            String docId = entity == null || entity.get("doc_id") == null ? null : entity.get("doc_id").toString();
            String fileName = entity == null || entity.get("file_name") == null ? null : entity.get("file_name").toString();
            String docType = entity == null || entity.get("doc_type") == null ? null : entity.get("doc_type").toString();
            String content = entity == null || entity.get("content") == null ? null : entity.get("content").toString();
            int chunkIndex = -1;
            if (entity != null && entity.get("chunk_index") instanceof Number number) {
                chunkIndex = number.intValue();
            }
            out.add(new MilvusHybridSchema.HybridHit(
                    docId, fileName, docType, content, chunkIndex, result.getScore(), "cosine"));
        }
        return out;
    }
}
