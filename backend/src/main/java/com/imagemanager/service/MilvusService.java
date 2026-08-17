package com.imagemanager.service;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.FieldType;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;

/**
 * Milvus 向量数据库服务
 * 用于存储和检索文档切片向量（替代 pgvector，支持千万级向量）
 */
@Slf4j
@Service
public class MilvusService {

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.collection:knowledge_chunks}")
    private String collectionName;

    @Value("${milvus.dimension:1024}")
    private int dimension;

    private MilvusClientV2 client;

    @PostConstruct
    public void init() {
        try {
            String uri = "http://" + host + ":" + port;
            log.info("连接 Milvus: {}", uri);
            client = new MilvusClientV2(ConnectConfig.builder()
                    .uri(uri)
                    .build());

            if (!client.hasCollection(HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build())) {
                createCollection();
            }
            log.info("Milvus 连接成功，集合 {} 已就绪", collectionName);
        } catch (Exception e) {
            log.error("Milvus 初始化失败: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * 创建集合（schema: id, chunk_id, doc_id, text, vector）
     */
    private void createCollection() {
        log.info("创建 Milvus 集合: {}", collectionName);

        // 定义字段
        List<FieldType> fields = new ArrayList<>();
        fields.add(FieldType.builder()
                .name("id")
                .dataType(DataType.Int64)
                .primaryKey(true)
                .autoID(false)
                .build());
        fields.add(FieldType.builder()
                .name("chunk_id")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());
        fields.add(FieldType.builder()
                .name("doc_id")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());
        fields.add(FieldType.builder()
                .name("text")
                .dataType(DataType.VarChar)
                .maxLength(65535)
                .build());
        fields.add(FieldType.builder()
                .name("vector")
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build());

        // 索引参数（HNSW）
        IndexParam indexParam = IndexParam.builder()
                .fieldName("vector")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of("M", 16, "efConstruction", 200))
                .build();

        CreateCollectionReq req = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .fieldTypes(fields)
                .indexParams(List.of(indexParam))
                .build();

        client.createCollection(req);
        log.info("Milvus 集合 {} 创建成功", collectionName);
    }

    /**
     * 插入向量
     * @param rows 每行包含 id, chunk_id, doc_id, text, vector
     */
    public void insert(List<Map<String, Object>> rows) {
        if (client == null || rows == null || rows.isEmpty()) return;
        try {
            client.insert(InsertReq.builder()
                    .collectionName(collectionName)
                    .data(rows)
                    .build());
            log.info("Milvus 插入 {} 条向量", rows.size());
        } catch (Exception e) {
            log.error("Milvus 插入失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 向量检索
     * @param queryVector 查询向量
     * @param topK 返回数量
     * @param filter 过滤条件（如 doc_id == 'xxx'）
     * @return 检索结果（chunk_id, text, score）
     */
    public List<Map<String, Object>> search(List<Float> queryVector, int topK, String filter) {
        if (client == null) return Collections.emptyList();
        try {
            SearchReq.SearchReqBuilder builder = SearchReq.builder()
                    .collectionName(collectionName)
                    .data(Collections.singletonList(queryVector))
                    .topK(topK)
                    .outputFields(Arrays.asList("chunk_id", "doc_id", "text"));

            if (filter != null && !filter.isEmpty()) {
                builder.filter(filter);
            }

            SearchResp resp = client.search(builder.build());
            List<Map<String, Object>> results = new ArrayList<>();
            for (SearchResp.SearchResult result : resp.getSearchResults().get(0)) {
                Map<String, Object> row = new HashMap<>();
                row.put("chunk_id", result.getId());
                row.put("score", result.getScore());
                row.putAll(result.getEntity());
                results.add(row);
            }
            return results;
        } catch (Exception e) {
            log.error("Milvus 检索失败: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 删除指定文档的所有向量
     * @param docId 文档ID
     */
    public void deleteByDocId(String docId) {
        if (client == null) return;
        try {
            client.delete(DeleteReq.builder()
                    .collectionName(collectionName)
                    .filter("doc_id == '" + docId + "'")
                    .build());
            log.info("Milvus 删除文档 {} 的向量", docId);
        } catch (Exception e) {
            log.error("Milvus 删除失败: {}", e.getMessage(), e);
        }
    }

    public boolean isReady() {
        return client != null;
    }
}
