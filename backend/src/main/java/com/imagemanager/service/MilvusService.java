package com.imagemanager.service;

import com.google.gson.JsonObject;
import com.imagemanager.milvus.HybridCollectionGuard;
import com.imagemanager.milvus.MilvusHybridSchema;
import com.imagemanager.milvus.MilvusRetrievalPlan;
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
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

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
 * - embedding:  FloatVector(1024) bge-m3 向量（Ollama 本地模型，不是云端嵌入）
 *
 * 索引：
 * - embedding: HNSW(M=16, efConstruction=200) + COSINE
 * - file_name / doc_type: TRIE 标量索引
 *
 * 混合检索（默认关闭）写到旁边的集合 {collection}_hybrid：
 * lexical_text 由 {@link com.imagemanager.milvus.HybridLexicalTokenizer} 预先分词，
 * Milvus 2.5 BM25 Function 用 whitespace 分析器生成 sparse，再和稠密向量做 RRF。
 * 稠密集合的 schema 不变。Java 读取的集合名是 milvus.collection，默认 salesperson_docs；
 * application.yml 里的 milvus.collection-name 目前没有绑定到这个字段。
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

    /** 默认关闭。关闭时检索、写入都与原来的稠密集合一致。 */
    @Value("${milvus.hybrid.enabled:false}")
    private boolean hybridEnabled;

    @Value("${milvus.hybrid.collection:}")
    private String hybridCollectionOverride;

    @Value("${milvus.hybrid.ranker:rrf}")
    private String hybridRanker;

    @Value("${milvus.hybrid.rrf-k:60}")
    private int hybridRrfK;

    @Value("${milvus.hybrid.dense-weight:0.5}")
    private float hybridDenseWeight;

    @Value("${milvus.hybrid.sparse-weight:0.5}")
    private float hybridSparseWeight;

    private MilvusClientV2 client;
    private volatile boolean hybridReady;
    private volatile boolean rebuilding;
    private volatile String rebuildState = "idle";
    private volatile String hybridStatusMessage = "混合检索关闭";
    private final AtomicBoolean rebuildRunning = new AtomicBoolean(false);
    private final MilvusHybridSchema.RebuildProgress rebuildProgress = new MilvusHybridSchema.RebuildProgress();
    private final ExecutorService hybridRebuildExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "milvus-hybrid-rebuild");
        thread.setDaemon(true);
        return thread;
    });

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
            refreshHybridState();
        } catch (Exception e) {
            log.error("Milvus 连接失败: {}", e.getMessage(), e);
        }
    }

    @PreDestroy
    public void destroy() {
        hybridRebuildExecutor.shutdownNow();
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
            dualWriteHybrid(rows);
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
     * 注意：Milvus SDK v2.5.6 的 flush API 可能不可用，依赖自动 flush 机制
     */
    public void flush() {
        if (!enabled || client == null) {
            return;
        }
        // Milvus 会自动定期 flush，无需显式调用
        // 如果 SDK 支持 flush，可以在此处添加
        log.info("Milvus flush 跳过（依赖自动 flush）: {}", collectionName);
    }

    /**
     * Milvus 检索结果
     */
    public static class MilvusSearchResult {
        public String docId;
        public String fileName;
        public String docType;
        public String content;
        public int chunkIndex = -1;
        public float score;
        /** cosine：稠密 HNSW；rrf / weighted / bm25：混合或稀疏通道，不能再用 0.35 余弦阈值。 */
        public String scoreMetric = "cosine";
    }

    /**
     * 判断 doc_id 是否已存在（重复导入跳过用）
     */
    public boolean existsByDocId(String docId) {
        if (!enabled || client == null || docId == null || docId.isBlank()) {
            return false;
        }
        try {
            QueryResp resp = client.query(QueryReq.builder()
                    .collectionName(collectionName)
                    .filter("doc_id == \"" + escape(docId) + "\"")
                    .outputFields(List.of("doc_id"))
                    .limit(1L)
                    .build());
            return resp != null && resp.getQueryResults() != null && !resp.getQueryResults().isEmpty();
        } catch (Exception e) {
            log.warn("Milvus doc_id 存在性查询失败: {} -> {}", docId, e.getMessage());
            return false;
        }
    }

    /**
     * 按文件名查询已有 doc_id 列表（内容更新时清理旧向量用）
     */
    public List<String> findDocIdsByFileName(String fileName) {
        if (!enabled || client == null || fileName == null || fileName.isBlank()) {
            return Collections.emptyList();
        }
        try {
            QueryResp resp = client.query(QueryReq.builder()
                    .collectionName(collectionName)
                    .filter("file_name == \"" + escape(fileName) + "\"")
                    .outputFields(List.of("doc_id"))
                    .limit(100L)
                    .build());
            List<String> ids = new ArrayList<>();
            if (resp != null && resp.getQueryResults() != null) {
                for (QueryResp.QueryResult row : resp.getQueryResults()) {
                    Object id = row.getEntity() != null ? row.getEntity().get("doc_id") : null;
                    if (id != null) {
                        ids.add(id.toString());
                    }
                }
            }
            return ids;
        } catch (Exception e) {
            log.warn("Milvus 按文件名查询失败: {} -> {}", fileName, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * filter 表达式转义（反斜杠和双引号）
     */
    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
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
                    .outputFields(List.of("doc_id", "file_name", "doc_type", "chunk_index", "content"))
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
                    Object chunkIndex = entity.get("chunk_index");
                    if (chunkIndex instanceof Number) {
                        row.chunkIndex = ((Number) chunkIndex).intValue();
                    }
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
     * 稠密 + 稀疏融合检索。开关关闭、集合未就绪或重建进行中时，调用方应继续走 {@link #search}。
     * 查询失败时回退稠密检索，避免混合集合异常把原来的召回也打掉。
     */
    public List<MilvusSearchResult> hybridSearch(float[] queryEmbedding, String queryText, int topK) {
        if (!isHybridSearchActive()) {
            return queryEmbedding == null ? Collections.emptyList() : search(queryEmbedding, topK);
        }
        try {
            List<MilvusHybridSchema.HybridHit> hits = MilvusHybridSchema.search(
                    client,
                    hybridCollectionName(),
                    queryEmbedding,
                    queryText,
                    topK,
                    hybridRanker,
                    hybridRrfK,
                    hybridDenseWeight,
                    hybridSparseWeight,
                    null);
            if (hits.isEmpty() && queryEmbedding != null && queryEmbedding.length > 0) {
                log.info("混合检索无结果，回退稠密集合 {}", collectionName);
                return search(queryEmbedding, topK);
            }
            return toSearchResults(hits);
        } catch (Exception e) {
            log.error("混合检索失败，回退稠密检索: {}", e.getMessage(), e);
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                return Collections.emptyList();
            }
            return search(queryEmbedding, topK);
        }
    }

    public boolean isHybridSearchActive() {
        return MilvusRetrievalPlan.resolve(hybridEnabled, hybridReady, rebuilding) == MilvusRetrievalPlan.Mode.HYBRID;
    }

    public Map<String, Object> hybridStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("milvusEnabled", isEnabled());
        status.put("hybridEnabled", hybridEnabled);
        status.put("hybridReady", hybridReady);
        status.put("hybridSearchActive", isHybridSearchActive());
        status.put("denseCollection", collectionName);
        status.put("hybridCollection", hybridCollectionName());
        status.put("ranker", hybridRanker);
        status.put("rrfK", hybridRrfK);
        status.put("denseWeight", hybridDenseWeight);
        status.put("sparseWeight", hybridSparseWeight);
        status.put("rebuildState", rebuildState);
        status.put("rebuilding", rebuilding);
        status.put("scanned", rebuildProgress.scanned);
        status.put("inserted", rebuildProgress.inserted);
        status.put("failed", rebuildProgress.failed);
        status.put("message", hybridStatusMessage);
        status.put("requirement", MilvusHybridSchema.REQUIREMENT);
        status.put("note", "默认 hybrid.enabled=false。打开前先 POST /api/admin/milvus/hybrid/rebuild。"
                + "回滚把 MILVUS_HYBRID_ENABLED 设为 false 并重启，稠密集合不改。"
                + "没有 SQL 迁移。历史问答在 pgvector(SMART_CHAT)，商品库在 goods_library，都不在 Milvus。");
        return status;
    }

    /**
     * 重复执行的回填：删掉混合集合，按当前稠密集合重建 BM25 字段后逐批抄入。
     * 重建过程中检索自动回到稠密集合。
     */
    public Map<String, Object> startHybridRebuild() {
        if (!enabled || client == null) {
            hybridStatusMessage = "Milvus 未连接，无法重建";
            Map<String, Object> status = hybridStatus();
            status.put("success", false);
            return status;
        }
        if (!rebuildRunning.compareAndSet(false, true)) {
            hybridStatusMessage = "重建已在进行";
            Map<String, Object> status = hybridStatus();
            status.put("success", true);
            return status;
        }
        rebuilding = true;
        rebuildState = "running";
        hybridReady = false;
        rebuildProgress.scanned = 0;
        rebuildProgress.inserted = 0;
        rebuildProgress.failed = 0;
        rebuildProgress.message = "开始重建";
        hybridStatusMessage = "正在重建混合集合 " + hybridCollectionName();
        hybridRebuildExecutor.execute(() -> {
            try {
                runHybridRebuild();
                rebuildState = "done";
                hybridStatusMessage = "重建完成：扫描 " + rebuildProgress.scanned
                        + "，写入 " + rebuildProgress.inserted
                        + "，失败 " + rebuildProgress.failed
                        + "。确认后把 milvus.hybrid.enabled 设为 true 并重启。";
            } catch (Exception e) {
                rebuildState = "failed";
                hybridReady = false;
                hybridStatusMessage = e.getMessage();
                log.error("混合集合重建失败: {}", e.getMessage(), e);
            } finally {
                rebuilding = false;
                rebuildRunning.set(false);
            }
        });
        Map<String, Object> status = hybridStatus();
        status.put("success", true);
        return status;
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
            if (hybridReady && !rebuilding) {
                try {
                    client.delete(DeleteReq.builder()
                            .collectionName(hybridCollectionName())
                            .filter("doc_id == \"" + escape(docId) + "\"")
                            .build());
                } catch (Exception hybridEx) {
                    log.warn("混合集合删除失败 docId={}: {}", docId, hybridEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Milvus 删除失败 docId={}: {}", docId, e.getMessage(), e);
        }
    }

    public boolean isEnabled() {
        return enabled && client != null;
    }

    private void refreshHybridState() {
        if (!enabled || client == null) {
            hybridReady = false;
            hybridStatusMessage = "Milvus 未连接";
            return;
        }
        String hybridName = hybridCollectionName();
        try {
            HybridCollectionGuard.assertShadowTarget(collectionName, hybridName);
        } catch (IllegalArgumentException rejected) {
            hybridReady = false;
            hybridStatusMessage = rejected.getMessage();
            log.error(hybridStatusMessage);
            return;
        }
        try {
            boolean exists = MilvusHybridSchema.exists(client, hybridName);
            if (hybridEnabled && !exists) {
                log.info("混合检索已打开，创建空集合 {}。历史数据需要 POST /api/admin/milvus/hybrid/rebuild", hybridName);
                MilvusHybridSchema.createCollection(client, hybridName, dimension);
                exists = true;
                hybridStatusMessage = "混合集合已创建但还没有历史数据，请先重建回填。回填完成前无结果会回退稠密检索。";
            } else if (exists) {
                hybridStatusMessage = hybridEnabled
                        ? "混合检索已打开，集合 " + hybridName
                        : "混合集合已存在。检索开关仍是关闭，新写入会同步进去，方便之后打开开关。";
            } else {
                hybridStatusMessage = "混合检索关闭，使用稠密集合 " + collectionName;
            }
            hybridReady = exists;
            if (hybridEnabled && !hybridReady) {
                log.warn("混合检索开关已打开，但集合未就绪。{}", MilvusHybridSchema.REQUIREMENT);
            }
        } catch (Exception e) {
            hybridReady = false;
            hybridStatusMessage = e.getMessage();
            log.error("混合集合初始化失败，检索保持稠密模式: {}", e.getMessage(), e);
        }
    }

    private void runHybridRebuild() {
        String hybridName = hybridCollectionName();
        HybridCollectionGuard.assertShadowTarget(collectionName, hybridName);
        if (!MilvusHybridSchema.exists(client, collectionName)) {
            throw new IllegalStateException("稠密集合不存在，无法回填: " + collectionName);
        }
        log.info("开始重建混合集合 {} <- {}（只删影子集合）", hybridName, collectionName);
        MilvusHybridSchema.dropShadow(client, collectionName, hybridName);
        MilvusHybridSchema.createCollection(client, hybridName, dimension);
        MilvusHybridSchema.RebuildStats stats = MilvusHybridSchema.backfill(
                client, collectionName, hybridName, 200, rebuildProgress);
        hybridReady = true;
        if (stats.failed > 0) {
            hybridStatusMessage = "重建部分失败: " + stats.lastError;
            log.warn("混合集合重建部分失败 scanned={} inserted={} failed={} last={}",
                    stats.scanned, stats.inserted, stats.failed, stats.lastError);
        } else {
            log.info("混合集合重建完成 scanned={} inserted={}", stats.scanned, stats.inserted);
        }
    }

    private void dualWriteHybrid(List<JsonObject> rows) {
        if (!hybridReady || rebuilding || client == null || rows == null || rows.isEmpty()) {
            return;
        }
        try {
            List<JsonObject> hybridRows = new ArrayList<>(rows.size());
            for (JsonObject row : rows) {
                hybridRows.add(MilvusHybridSchema.hybridRowFromDense(row));
            }
            MilvusHybridSchema.insert(client, hybridCollectionName(), hybridRows);
        } catch (Exception e) {
            log.warn("混合集合双写失败({}条)，稠密集合已写入，可稍后重建: {}", rows.size(), e.getMessage());
        }
    }

    private List<MilvusSearchResult> toSearchResults(List<MilvusHybridSchema.HybridHit> hits) {
        List<MilvusSearchResult> out = new ArrayList<>();
        if (hits == null) {
            return out;
        }
        for (MilvusHybridSchema.HybridHit hit : hits) {
            MilvusSearchResult row = new MilvusSearchResult();
            row.docId = hit.docId();
            row.fileName = hit.fileName();
            row.docType = hit.docType();
            row.content = hit.content();
            row.chunkIndex = hit.chunkIndex();
            row.score = hit.score();
            row.scoreMetric = hit.scoreMetric() == null ? "cosine" : hit.scoreMetric();
            out.add(row);
        }
        return out;
    }

    private String hybridCollectionName() {
        if (hybridCollectionOverride != null && !hybridCollectionOverride.isBlank()) {
            return hybridCollectionOverride.trim();
        }
        return collectionName + "_hybrid";
    }
}
