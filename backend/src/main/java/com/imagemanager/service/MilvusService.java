package com.imagemanager.service;

import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.collection.request.FlushReq;
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

/**
 * Milvus 向量数据库服务（200G 业务员资料，约 2000 万切片）
 *
 * Collection: salesperson_docs
 * - chunk_id:   Int64 主键（自增）
 * - doc_id:     VarChar(64)  文档ID（内容 SHA-256 前缀，重导幂等）
 * - file_name:  VarChar(1024) 文件名（zip 内完整路径）
 * - doc_type:   VarChar(16)  pdf/xlsx/image/word/txt
 * - chunk_index:Int32        切片序号
 * - content:    VarChar(8192) 切片原文（检索后直接返回）
 * - embedding:  FloatVector(1024) bge-m3 向量
 *
 * 索引：
 * - embedding: HNSW(M=16, efConstruction=200) + COSINE
 * - file_name / doc_type: TRIE 标量索引
 */
@Slf4j
@Service
public class MilvusService {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.collection:salesperson_docs}")
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

            // 构建 Schema
            CreateCollectionReq.CollectionSchema schema = client.createSchema();

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

            // 索引：HNSW 向量索引 + TRIE 标量索引
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

            log.info("Milvus collection 创建成功: {} (dimension={}, HNSW)", collectionName, dimension);
        } catch (Exception e) {
            log.error("创建 Milvus collection 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构造一条 chunk 的 JsonObject（插入数据行）
     */
    public JsonObject buildRow(String docId, String fileName, String docType,
                               int chunkIndex, String content, float[] embedding) {
        JsonObject row = new JsonObject();
        row.addProperty("doc_id", docId);
        row.addProperty("file_name", fileName != null ? fileName : "");
        row.addProperty("doc_type", docType != null ? docType : "other");
        row.addProperty("chunk_index", chunkIndex);
        row.addProperty("content", content != null ? content : "");
        com.google.gson.JsonArray vec = new com.google.gson.JsonArray();
        for (float v : embedding) {
            vec.add(v);
        }
        row.add("embedding", vec);
        return row;
    }

    /**
     * 批量插入（批量导入核心入口，每批建议 500-1000 条）
     */
    public void batchInsert(List<JsonObject> rows) {
        if (!enabled || client == null || rows == null || rows.isEmpty()) {
            return;
        }
        try {
            client.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(rows)
                    .build());
        } catch (Exception e) {
            log.error("Milvus 批量插入失败({}条): {}", rows.size(), e.getMessage(), e);
            throw new RuntimeException("Milvus 批量插入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 插入单条切片（知识库文档向量化时使用）
     */
    public void insertChunk(String docId, String fileName, String docType,
                            int chunkIndex, String content, float[] embedding) {
        if (!enabled || client == null) {
            return;
        }
        try {
            batchInsert(List.of(buildRow(docId, fileName, docType, chunkIndex, content, embedding)));
        } catch (Exception e) {
            log.warn("Milvus 单条插入失败: docId={}, {}", docId, e.getMessage());
        }
    }

    /**
     * 强制落盘（批量导入完成后调用，确保数据持久化）
     */
    public void flush() {
        if (!enabled || client == null) {
            return;
        }
        try {
            client.flush(FlushReq.builder()
                    .collectionNames(List.of(collectionName))
                    .build());
            log.info("Milvus flush 完成: {}", collectionName);
        } catch (Exception e) {
            log.warn("Milvus flush 失败: {}", e.getMessage());
        }
    }

    /**
     * Milvus 检索结果
     */
    public static class MilvusSearchResult {
        public String docId;
        public String fileName;
        public String docType;
        public String content;
        public float score;
    }

    /**
     * 向量检索（TopK 相似）
     */
    public List<MilvusSearchResult> search(float[] queryEmbedding, int topK) {
        if (!enabled || client == null) {
            return Collections.emptyList();
        }
        try {
            List<Float> vector = new ArrayList<>(queryEmbedding.length);
            for (float f : queryEmbedding) {
                vector.add(f);
            }
            List<BaseVector> vectors = List.of(new FloatVec(vector));

            SearchResp resp = client.search(SearchReq.builder()
                    .collectionName(collectionName)
                    .annsField("embedding")
                    .data(vectors)
                    .topK(topK)
                    .outputFields(List.of("doc_id", "file_name", "doc_type", "content"))
                    .searchParams(Map.of("ef", 128))
                    .build());

            List<List<SearchResp.SearchResult>> results = resp.getSearchResults();
            if (results == null || results.isEmpty()) {
                return Collections.emptyList();
            }

            List<MilvusSearchResult> out = new ArrayList<>();
            for (SearchResp.SearchResult r : results.get(0)) {
                MilvusSearchResult row = new MilvusSearchResult();
                row.score = r.getScore();
                Map<String, Object> entity = r.getEntity();
                if (entity != null) {
                    Object docId = entity.get("doc_id");
                    row.docId = docId != null ? docId.toString() : null;
                    Object fileName = entity.get("file_name");
                    row.fileName = fileName != null ? fileName.toString() : null;
                    Object docType = entity.get("doc_type");
                    row.docType = docType != null ? docType.toString() : null;
                    Object content = entity.get("content");
                    row.content = content != null ? content.toString() : null;
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
     * 按文档ID删除（删除文档/重导前清理旧向量）
     */
    public void deleteByDocId(String docId) {
        if (!enabled || client == null || docId == null) {
            return;
        }
        try {
            client.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter("doc_id == \"" + docId + "\"")
                    .build());
        } catch (Exception e) {
            log.error("Milvus 删除失败 docId={}: {}", docId, e.getMessage(), e);
        }
    }

    public boolean isEnabled() {
        return enabled && client != null;
    }
}
