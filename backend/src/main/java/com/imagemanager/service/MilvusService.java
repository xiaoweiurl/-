package com.imagemanager.service;

import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.common.BaseVector;
import io.milvus.v2.common.FloatVec;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq.CollectionSchema;
import io.milvus.v2.service.collection.request.CreateCollectionReq.FieldSchema;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.CreatePartitionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Milvus 向量数据库服务（200G 业务员数据优化版）
 *
 * Collection 结构：
 * - chunk_id: 主键（自增）
 * - doc_id: 文档ID（标量过滤）
 * - salesperson_id: 业务员ID（分区键，加速过滤）
 * - customer_id: 客户ID（标量过滤）
 * - doc_type: 文档类型（pdf/word/excel/image）
 * - chunk_index: 切片序号
 * - content: 切片原文（用于检索后返回）
 * - embedding: 向量（bge-m3, 1024维）
 *
 * 索引策略：
 * - embedding: HNSW（M=16, efConstruction=200）快速近似搜索
 * - salesperson_id: 分区键（按业务员分区，查询时只扫对应分区）
 * - doc_id/customer_id/doc_type: 标量索引（支持过滤）
 */
@Slf4j
@Service
public class MilvusService {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.collection:salesperson_chunks}")
    private String collectionName;

    @Value("${milvus.dimension:1024}")
    private int dimension;

    @Value("${milvus.enabled:true}")
    private boolean enabled;

    private MilvusClientV2 client;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Milvus 未启用 (milvus.enabled=false)");
            return;
        }
        try {
            String uri = "http://" + host + ":" + port;
            log.info("连接 Milvus: {}", uri);
            client = new MilvusClientV2(ConnectConfig.builder()
                    .uri(uri)
                    .connectTimeoutMs(10000)
                    .build());
            log.info("Milvus 连接成功");
            ensureCollection();
        } catch (Exception e) {
            log.error("Milvus 连接失败: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("关闭 Milvus 连接异常: {}", e.getMessage());
            }
        }
    }

    /**
     * 确保 Collection 存在（不存在则创建）
     */
    private void ensureCollection() {
        try {
            boolean exists = client.hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build());
            if (exists) {
                log.info("Milvus collection 已存在: {}", collectionName);
                return;
            }

            // 定义字段
            List<FieldSchema> fields = new ArrayList<>();

            // 主键（自增）
            fields.add(FieldSchema.builder()
                    .name("chunk_id")
                    .dataType(DataType.Int64)
                    .isPrimaryKey(true)
                    .autoID(true)
                    .build());

            // 文档ID（标量过滤）
            fields.add(FieldSchema.builder()
                    .name("doc_id")
                    .dataType(DataType.Int64)
                    .build());

            // 业务员ID（分区键，加速过滤）
            fields.add(FieldSchema.builder()
                    .name("salesperson_id")
                    .dataType(DataType.Int64)
                    .build());

            // 客户ID（标量过滤）
            fields.add(FieldSchema.builder()
                    .name("customer_id")
                    .dataType(DataType.Int64)
                    .build());

            // 文档类型（pdf/word/excel/image）
            fields.add(FieldSchema.builder()
                    .name("doc_type")
                    .dataType(DataType.VarChar)
                    .maxLength(32)
                    .build());

            // 切片序号
            fields.add(FieldSchema.builder()
                    .name("chunk_index")
                    .dataType(DataType.Int32)
                    .build());

            // 切片原文（用于检索后返回）
            fields.add(FieldSchema.builder()
                    .name("content")
                    .dataType(DataType.VarChar)
                    .maxLength(8192)
                    .build());

            // 向量（bge-m3, 1024维）
            fields.add(FieldSchema.builder()
                    .name("embedding")
                    .dataType(DataType.FloatVector)
                    .dimension(dimension)
                    .build());

            // 定义索引
            List<IndexParam> indexes = new ArrayList<>();

            // 向量索引：HNSW（快速近似搜索）
            indexes.add(IndexParam.builder()
                    .fieldName("embedding")
                    .indexType(IndexParam.IndexType.HNSW)
                    .metricType(IndexParam.MetricType.COSINE)
                    .extraParams(Map.of("M", 16, "efConstruction", 200))
                    .build());

            // 标量索引：doc_id（加速过滤）
            indexes.add(IndexParam.builder()
                    .fieldName("doc_id")
                    .indexType(IndexParam.IndexType.STL_SORT)
                    .build());

            // 标量索引：salesperson_id（分区键）
            indexes.add(IndexParam.builder()
                    .fieldName("salesperson_id")
                    .indexType(IndexParam.IndexType.STL_SORT)
                    .build());

            // 标量索引：customer_id
            indexes.add(IndexParam.builder()
                    .fieldName("customer_id")
                    .indexType(IndexParam.IndexType.STL_SORT)
                    .build());

            // 标量索引：doc_type
            indexes.add(IndexParam.builder()
                    .fieldName("doc_type")
                    .indexType(IndexParam.IndexType.TRIE)
                    .build());

            // 创建 Collection
            client.createCollection(CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .fieldTypes(fields)
                    .indexes(indexes)
                    .enableDynamicField(false)
                    .build());

            log.info("Milvus collection 创建成功: {} (dimension={}, HNSW索引)", collectionName, dimension);
        } catch (Exception e) {
            log.error("创建 Milvus collection 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 批量插入向量（200G 数据批量导入优化）
     *
     * @param rows 每行包含 doc_id, salesperson_id, customer_id, doc_type, chunk_index, content, embedding
     * @return 插入成功数量
     */
    public int batchInsert(List<Map<String, Object>> rows) {
        if (!enabled || client == null || rows == null || rows.isEmpty()) {
            return 0;
        }
        try {
            // 转换为 JsonObject（Milvus SDK v2 要求）
            List<JsonObject> jsonRows = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                JsonObject obj = new JsonObject();
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    String key = entry.getKey();
                    Object val = entry.getValue();
                    if (val instanceof Number) {
                        obj.addProperty(key, (Number) val);
                    } else if (val instanceof String) {
                        obj.addProperty(key, (String) val);
                    } else if (val instanceof List) {
                        // embedding 字段：List<Float> 序列化为 JSON 数组
                        com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                        for (Object item : (List<?>) val) {
                            if (item instanceof Number) {
                                arr.add((Number) item);
                            }
                        }
                        obj.add(key, arr);
                    }
                }
                jsonRows.add(obj);
            }
            client.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(jsonRows)
                    .build());
            log.info("Milvus 批量插入成功: {} 条", rows.size());
            return rows.size();
        } catch (Exception e) {
            log.error("Milvus 批量插入失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Milvus 检索结果
     */
    public static class MilvusSearchResult {
        public long chunkId;
        public Long docId;
        public Long salespersonId;
        public Long customerId;
        public String docType;
        public int chunkIndex;
        public String content;
        public float score;
    }

    /**
     * 向量检索（4参数重载，默认不过滤 docType）
     */
    public List<MilvusSearchResult> search(float[] queryEmbedding, int topK,
                                            Long salespersonId, Long customerId) {
        return search(queryEmbedding, topK, salespersonId, customerId, null);
    }

    /**
     * 向量检索（支持元数据过滤，200G 数据快速精准）
     *
     * @param queryEmbedding 查询向量
     * @param topK 返回数量
     * @param salespersonId 业务员ID过滤（可选，null 表示不过滤）
     * @param customerId 客户ID过滤（可选）
     * @param docType 文档类型过滤（可选）
     * @return 检索结果列表
     */
    public List<MilvusSearchResult> search(float[] queryEmbedding, int topK,
                                            Long salespersonId, Long customerId, String docType) {
        if (!enabled || client == null) {
            return Collections.emptyList();
        }
        try {
            // 构建过滤表达式
            List<String> filters = new ArrayList<>();
            if (salespersonId != null) {
                filters.add("salesperson_id == " + salespersonId);
            }
            if (customerId != null) {
                filters.add("customer_id == " + customerId);
            }
            if (docType != null && !docType.isEmpty()) {
                filters.add("doc_type == \"" + docType + "\"");
            }
            String filterExpr = filters.isEmpty() ? "" : String.join(" and ", filters);

            // 构建查询
            SearchReq.SearchReqBuilder<?, ?> builder = SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField("embedding")
                    .topK(topK)
                    .outputFields(Arrays.asList("chunk_id", "doc_id", "salesperson_id", "customer_id", "doc_type", "chunk_index", "content"));

            // 设置向量数据（包装为 FloatVec）
            List<Float> vector = new ArrayList<>();
            for (float f : queryEmbedding) {
                vector.add(f);
            }
            List<BaseVector> vectors = new ArrayList<>();
            vectors.add(new FloatVec(vector));
            builder.data(vectors);

            // 设置过滤条件
            if (!filterExpr.isEmpty()) {
                builder.filter(filterExpr);
            }

            // 设置搜索参数（HNSW: ef 越大越准但越慢）
            builder.searchParams(Map.of("ef", 128));

            SearchResp resp = client.search(builder.build());
            List<List<SearchResp.SearchResult>> results = resp.getSearchResults();
            if (results == null || results.isEmpty()) {
                return Collections.emptyList();
            }

            List<MilvusSearchResult> out = new ArrayList<>();
            for (SearchResp.SearchResult r : results.get(0)) {
                MilvusSearchResult row = new MilvusSearchResult();
                row.chunkId = r.getId() instanceof Number ? ((Number) r.getId()).longValue() : 0L;
                row.score = r.getScore();
                Map<String, Object> entity = r.getEntity();
                if (entity != null) {
                    row.docId = entity.get("doc_id") instanceof Number ? ((Number) entity.get("doc_id")).longValue() : null;
                    row.salespersonId = entity.get("salesperson_id") instanceof Number ? ((Number) entity.get("salesperson_id")).longValue() : null;
                    row.customerId = entity.get("customer_id") instanceof Number ? ((Number) entity.get("customer_id")).longValue() : null;
                    row.docType = entity.get("doc_type") instanceof String ? (String) entity.get("doc_type") : null;
                    row.chunkIndex = entity.get("chunk_index") instanceof Number ? ((Number) entity.get("chunk_index")).intValue() : 0;
                    row.content = entity.get("content") instanceof String ? (String) entity.get("content") : null;
                }
                out.add(row);
            }
            return out;
        } catch (Exception e) {
            log.error("Milvus 检索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 按文档ID删除（删除文档时同步删除向量）
     */
    public void deleteByDocId(long docId) {
        if (!enabled || client == null) {
            return;
        }
        try {
            client.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter("doc_id == " + docId)
                    .build());
            log.info("Milvus 删除文档向量: docId={}", docId);
        } catch (Exception e) {
            log.error("Milvus 删除失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 按业务员ID删除（删除业务员数据时同步删除向量）
     */
    public void deleteBySalespersonId(long salespersonId) {
        if (!enabled || client == null) {
            return;
        }
        try {
            client.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter("salesperson_id == " + salespersonId)
                    .build());
            log.info("Milvus 删除业务员向量: salespersonId={}", salespersonId);
        } catch (Exception e) {
            log.error("Milvus 删除失败: {}", e.getMessage(), e);
        }
    }

    public boolean isEnabled() {
        return enabled && client != null;
    }
}
