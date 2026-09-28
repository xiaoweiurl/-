package com.imagemanager.milvus;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.orm.iterator.QueryIterator;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.GetCollectionStatsReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryIteratorReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.BaseRanker;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.SearchResp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 稠密集合旁边的混合集合：同一份正文和 bge-m3 向量，另加 whitespace BM25。
 * 分词在写入前完成，服务器不再把货号切开。
 */
public final class MilvusHybridSchema {

    public static final String LEXICAL_FIELD = "lexical_text";
    public static final String SPARSE_FIELD = "sparse";
    public static final String DENSE_FIELD = "embedding";
    public static final String REQUIREMENT =
            "混合检索需要 Milvus Server 2.5.0 及以上（线上 v2.6.20 standalone 已支持 BM25 Function 和 hybridSearch，"
                    + "Java SDK 是 io.milvus:milvus-sdk-java:2.5.6）。"
                    + "低于 2.5 时不要打开 milvus.hybrid.enabled，检索仍走原来的稠密 HNSW。";

    private static final List<String> OUTPUT_FIELDS = List.of(
            "doc_id", "file_name", "doc_type", "chunk_index", "content");

    private MilvusHybridSchema() {
    }

    public record HybridHit(String docId, String fileName, String docType, String content,
                            int chunkIndex, float score, String scoreMetric) {
    }

    public static void createCollection(MilvusClientV2 client, String collectionName, int dimension) {
        try {
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
            Map<String, Object> analyzer = new LinkedHashMap<>();
            analyzer.put("tokenizer", "whitespace");
            analyzer.put("filter", List.of("lowercase"));
            schema.addField(AddFieldReq.builder()
                    .fieldName(LEXICAL_FIELD)
                    .dataType(DataType.VarChar)
                    .maxLength(65535)
                    .enableAnalyzer(true)
                    .analyzerParams(analyzer)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(DENSE_FIELD)
                    .dataType(DataType.FloatVector)
                    .dimension(dimension)
                    .build());
            schema.addField(AddFieldReq.builder()
                    .fieldName(SPARSE_FIELD)
                    .dataType(DataType.SparseFloatVector)
                    .build());
            schema.addFunction(CreateCollectionReq.Function.builder()
                    .name("lexical_bm25")
                    .functionType(FunctionType.BM25)
                    .inputFieldNames(List.of(LEXICAL_FIELD))
                    .outputFieldNames(List.of(SPARSE_FIELD))
                    .build());

            List<IndexParam> indexes = List.of(
                    IndexParam.builder()
                            .fieldName(DENSE_FIELD)
                            .indexType(IndexParam.IndexType.HNSW)
                            .metricType(IndexParam.MetricType.COSINE)
                            .extraParams(Map.of("M", 16, "efConstruction", 200))
                            .build(),
                    IndexParam.builder()
                            .fieldName(SPARSE_FIELD)
                            .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                            .metricType(IndexParam.MetricType.BM25)
                            .extraParams(Map.of("inverted_index_algo", "DAAT_MAXSCORE"))
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
        } catch (RuntimeException e) {
            throw new IllegalStateException(REQUIREMENT + " 创建集合失败: " + e.getMessage(), e);
        }
    }

    public static boolean exists(MilvusClientV2 client, String collectionName) {
        return client.hasCollection(HasCollectionReq.builder()
                .collectionName(collectionName)
                .build());
    }

    public static void dropIfExists(MilvusClientV2 client, String collectionName) {
        if (exists(client, collectionName)) {
            client.dropCollection(DropCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
        }
    }

    /**
     * 只删除影子集合。原集合（以及 salesperson_docs / salesperson_chunks）一律拒绝。
     */
    public static void dropShadow(MilvusClientV2 client, String sourceCollection, String shadowCollection) {
        HybridCollectionGuard.assertShadowTarget(sourceCollection, shadowCollection);
        dropIfExists(client, shadowCollection);
    }

    public static long rowCount(MilvusClientV2 client, String collectionName) {
        Long count = client.getCollectionStats(GetCollectionStatsReq.builder()
                .collectionName(collectionName)
                .build()).getNumOfEntities();
        return count == null ? 0L : count;
    }

    public static void flush(MilvusClientV2 client, String collectionName) {
        client.flush(FlushReq.builder()
                .collectionNames(List.of(collectionName))
                .waitFlushedTimeoutMs(120_000L)
                .build());
    }

    public static JsonObject hybridRowFromDense(JsonObject denseRow) {
        JsonObject row = denseRow.deepCopy();
        row.remove(SPARSE_FIELD);
        row.remove("chunk_id");
        String content = "";
        if (row.has("content") && !row.get("content").isJsonNull()) {
            content = row.get("content").getAsString();
        }
        row.addProperty(LEXICAL_FIELD, HybridLexicalTokenizer.lexicalText(content));
        return row;
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

    /**
     * 从稠密集合把正文和向量抄到混合集合。可重复执行：调用方先删掉混合集合再调用本方法。
     */
    public static RebuildStats backfill(MilvusClientV2 client,
                                        String sourceCollection,
                                        String hybridCollection,
                                        int batchSize,
                                        RebuildProgress progress) {
        int size = batchSize > 0 ? batchSize : 200;
        RebuildStats stats = new RebuildStats();
        // chunk_id 必须出现在输出字段里，否则 Java SDK 的 QueryIterator 无法推进主键游标，会反复返回同一页。
        QueryIterator iterator = client.queryIterator(QueryIteratorReq.builder()
                .collectionName(sourceCollection)
                .expr("chunk_id >= 0")
                .outputFields(List.of("chunk_id", "doc_id", "file_name", "doc_type", "chunk_index", "content", DENSE_FIELD))
                .batchSize(size)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .build());
        try {
            List<JsonObject> pending = new ArrayList<>();
            long cursor = Long.MIN_VALUE;
            while (true) {
                List<QueryResultsWrapper.RowRecord> page = iterator.next();
                if (page == null || page.isEmpty()) {
                    break;
                }
                boolean advanced = false;
                long pageMax = cursor;
                for (QueryResultsWrapper.RowRecord record : page) {
                    long pk = primaryKey(record.get("chunk_id"));
                    if (pk > cursor) {
                        advanced = true;
                    }
                    if (pk > pageMax) {
                        pageMax = pk;
                    }
                }
                if (!advanced) {
                    stats.lastError = "查询迭代没有前进，已停止以避免重复写入";
                    break;
                }
                cursor = pageMax;
                for (QueryResultsWrapper.RowRecord record : page) {
                    stats.scanned++;
                    if (progress != null) {
                        progress.scanned = stats.scanned;
                    }
                    JsonObject row = rowFromRecord(record);
                    if (row == null) {
                        stats.skipped++;
                        continue;
                    }
                    pending.add(row);
                    if (pending.size() >= size) {
                        flushBatch(client, hybridCollection, pending, stats, progress);
                    }
                }
            }
            flushBatch(client, hybridCollection, pending, stats, progress);
        } finally {
            iterator.close();
        }
        return stats;
    }

    public static List<HybridHit> search(MilvusClientV2 client,
                                         String collectionName,
                                         float[] embedding,
                                         String queryText,
                                         int topK,
                                         String rankerName,
                                         int rrfK,
                                         float denseWeight,
                                         float sparseWeight,
                                         ConsistencyLevel consistency) {
        int limit = topK > 0 ? topK : 10;
        boolean denseOk = embedding != null && embedding.length > 0;
        String lexical = HybridLexicalTokenizer.lexicalText(queryText);
        boolean sparseOk = queryText != null && !queryText.isBlank() && !"_blank".equals(lexical);
        if (!denseOk && !sparseOk) {
            return List.of();
        }
        if (denseOk && sparseOk) {
            int annK = Math.max(limit, 48);
            List<Float> vector = new ArrayList<>(embedding.length);
            for (float value : embedding) {
                vector.add(value);
            }
            AnnSearchReq denseReq = AnnSearchReq.builder()
                    .vectorFieldName(DENSE_FIELD)
                    .vectors(List.<BaseVector>of(new FloatVec(vector)))
                    .topK(annK)
                    .metricType(IndexParam.MetricType.COSINE)
                    .params("{\"ef\":128}")
                    .build();
            AnnSearchReq sparseReq = AnnSearchReq.builder()
                    .vectorFieldName(SPARSE_FIELD)
                    .vectors(List.<BaseVector>of(new EmbeddedText(lexical)))
                    .topK(annK)
                    .metricType(IndexParam.MetricType.BM25)
                    .params("{\"drop_ratio_search\":0.0}")
                    .build();
            boolean weighted = "weighted".equalsIgnoreCase(rankerName);
            BaseRanker ranker = weighted
                    ? new WeightedRanker(List.of(denseWeight, sparseWeight))
                    : new RRFRanker(rrfK > 0 ? rrfK : RrfFusion.DEFAULT_K);
            HybridSearchReq.HybridSearchReqBuilder<?, ?> builder = HybridSearchReq.builder()
                    .collectionName(collectionName)
                    .searchRequests(List.of(denseReq, sparseReq))
                    .ranker(ranker)
                    .topK(limit)
                    .outFields(OUTPUT_FIELDS);
            if (consistency != null) {
                builder.consistencyLevel(consistency);
            }
            SearchResp resp = client.hybridSearch(builder.build());
            return mapHits(resp, weighted ? "weighted" : "rrf");
        }
        if (sparseOk) {
            SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField(SPARSE_FIELD)
                    .metricType(IndexParam.MetricType.BM25)
                    .data(List.of(new EmbeddedText(lexical)))
                    .topK(limit)
                    .outputFields(OUTPUT_FIELDS)
                    .searchParams(Map.of("drop_ratio_search", 0.0));
            if (consistency != null) {
                builder.consistencyLevel(consistency);
            }
            return mapHits(client.search(builder.build()), "bm25");
        }
        List<Float> vector = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            vector.add(value);
        }
        SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                .collectionName(collectionName)
                .annsField(DENSE_FIELD)
                .metricType(IndexParam.MetricType.COSINE)
                .data(List.of(new FloatVec(vector)))
                .topK(limit)
                .outputFields(OUTPUT_FIELDS)
                .searchParams(Map.of("ef", 128));
        if (consistency != null) {
            builder.consistencyLevel(consistency);
        }
        return mapHits(client.search(builder.build()), "cosine");
    }

    private static void flushBatch(MilvusClientV2 client,
                                   String hybridCollection,
                                   List<JsonObject> pending,
                                   RebuildStats stats,
                                   RebuildProgress progress) {
        if (pending.isEmpty()) {
            return;
        }
        try {
            insert(client, hybridCollection, pending);
            stats.inserted += pending.size();
        } catch (RuntimeException batchError) {
            for (JsonObject row : pending) {
                try {
                    insert(client, hybridCollection, List.of(row));
                    stats.inserted++;
                } catch (RuntimeException one) {
                    stats.failed++;
                    stats.lastError = one.getMessage();
                }
            }
        }
        if (progress != null) {
            progress.inserted = stats.inserted;
            progress.failed = stats.failed;
            progress.message = "已扫描 " + stats.scanned + "，写入 " + stats.inserted;
        }
        pending.clear();
    }

    private static long primaryKey(Object raw) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw == null) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(raw.toString());
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    private static JsonObject rowFromRecord(QueryResultsWrapper.RowRecord record) {
        if (record == null) {
            return null;
        }
        JsonArray vector = toVector(record.get(DENSE_FIELD));
        if (vector.size() == 0) {
            return null;
        }
        JsonObject row = new JsonObject();
        row.addProperty("doc_id", text(record.get("doc_id")));
        row.addProperty("file_name", text(record.get("file_name")));
        String docType = text(record.get("doc_type"));
        row.addProperty("doc_type", docType.isBlank() ? "other" : docType);
        Object chunkIndex = record.get("chunk_index");
        row.addProperty("chunk_index", chunkIndex instanceof Number ? ((Number) chunkIndex).intValue() : 0);
        String content = text(record.get("content"));
        row.addProperty("content", content);
        row.add(DENSE_FIELD, vector);
        row.addProperty(LEXICAL_FIELD, HybridLexicalTokenizer.lexicalText(content));
        return row;
    }

    private static JsonArray toVector(Object raw) {
        JsonArray array = new JsonArray();
        if (raw instanceof List<?> list) {
            for (Object value : list) {
                if (value instanceof Number number) {
                    array.add(number.floatValue());
                }
            }
        } else if (raw instanceof float[] floats) {
            for (float value : floats) {
                array.add(value);
            }
        } else if (raw instanceof JsonArray json) {
            return json;
        }
        return array;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static List<HybridHit> mapHits(SearchResp resp, String scoreMetric) {
        if (resp == null || resp.getSearchResults() == null || resp.getSearchResults().isEmpty()) {
            return List.of();
        }
        List<HybridHit> out = new ArrayList<>();
        for (SearchResp.SearchResult result : resp.getSearchResults().get(0)) {
            Map<String, Object> entity = result.getEntity();
            String docId = entity == null ? null : textOrNull(entity.get("doc_id"));
            String fileName = entity == null ? null : textOrNull(entity.get("file_name"));
            String docType = entity == null ? null : textOrNull(entity.get("doc_type"));
            String content = entity == null ? null : textOrNull(entity.get("content"));
            int chunkIndex = -1;
            if (entity != null && entity.get("chunk_index") instanceof Number number) {
                chunkIndex = number.intValue();
            }
            out.add(new HybridHit(docId, fileName, docType, content, chunkIndex, result.getScore(), scoreMetric));
        }
        return out;
    }

    private static String textOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    public static final class RebuildStats {
        public int scanned;
        public int inserted;
        public int failed;
        public int skipped;
        public String lastError;
    }

    public static final class RebuildProgress {
        public volatile int scanned;
        public volatile int inserted;
        public volatile int failed;
        public volatile String message = "";
    }
}
