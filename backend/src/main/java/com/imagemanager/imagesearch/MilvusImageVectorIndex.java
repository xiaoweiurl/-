package com.imagemanager.imagesearch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 独立的图片向量集合。不读取、不修改 salesperson_docs / salesperson_docs_hybrid / salesperson_chunks。
 * 功能关闭时不建立连接。
 */
@Slf4j
@Component
public class MilvusImageVectorIndex implements ImageVectorIndex {

    private final ImageSearchProperties properties;
    private final Object lock = new Object();
    private MilvusClientV2 client;
    private volatile boolean ready;
    private volatile boolean cropReady;

    public MilvusImageVectorIndex(ImageSearchProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void onStart() {
        if (!properties.isEnabled()) {
            log.info("以图搜图未开启 (image-search.enabled=false)");
            return;
        }
        initIfEnabled();
    }

    public void initIfEnabled() {
        if (!properties.isEnabled()) {
            return;
        }
        synchronized (lock) {
            if (ready && client != null) {
                return;
            }
            ImageSearchFilters.assertCollectionName(properties.getCollection());
            try {
                String uri = "http://" + properties.getMilvusHost() + ":" + properties.getMilvusPort();
                log.info("连接图片向量库 {} 集合 {}", uri, properties.getCollection());
                client = new MilvusClientV2(ConnectConfig.builder()
                        .uri(uri)
                        .connectTimeoutMs(10_000)
                        .build());
                ensureCollection(properties.getCollection());
                ready = true;
                log.info("图片向量集合就绪: {}", properties.getCollection());
                cropReady = false;
                if (properties.isCropEnabled()) {
                    openCropCollection();
                }
            } catch (Exception e) {
                ready = false;
                cropReady = false;
                log.error("图片向量库连接失败（以图搜图不可用，不影响其他功能）: {}", e.getMessage());
            }
        }
    }

    @PreDestroy
    public void close() {
        synchronized (lock) {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception e) {
                    log.warn("关闭图片向量库连接异常: {}", e.getMessage());
                }
                client = null;
            }
            ready = false;
            cropReady = false;
        }
    }

    @Override
    public boolean isReady() {
        return ready && client != null;
    }

    @Override
    public boolean supports(EmbeddingVariant variant) {
        if (variant == EmbeddingVariant.CROP) {
            return cropReady && client != null;
        }
        return isReady();
    }

    @Override
    public void upsert(ImageVectorRecord record, float[] embedding) {
        upsert(EmbeddingVariant.FULL, record, embedding);
    }

    @Override
    public void upsert(EmbeddingVariant variant, ImageVectorRecord record, float[] embedding) {
        ensureReady();
        if (embedding == null || embedding.length != properties.getDimension()) {
            throw new IllegalStateException("向量维度与 image-search.dimension 不一致");
        }
        String collection = collectionName(variant);
        deleteQuiet(collection, record.vectorId());
        JsonObject row = toRow(record, embedding);
        client.insert(InsertReq.builder()
                .collectionName(collection)
                .data(List.of(row))
                .build());
    }

    @Override
    public void deleteById(String vectorId) {
        deleteById(EmbeddingVariant.FULL, vectorId);
    }

    @Override
    public void deleteById(EmbeddingVariant variant, String vectorId) {
        if (!supports(variant) || vectorId == null || vectorId.isBlank()) {
            return;
        }
        deleteQuiet(collectionName(variant), vectorId);
    }

    @Override
    public List<ImageSearchModels.RawHit> search(float[] embedding, String scope, String company, int topK) {
        return search(EmbeddingVariant.FULL, embedding, scope, company, topK);
    }

    @Override
    public List<ImageSearchModels.RawHit> search(EmbeddingVariant variant, float[] embedding,
                                                String scope, String company, int topK) {
        if (!supports(variant)) {
            throw new IllegalStateException(variant == EmbeddingVariant.CROP
                    ? "裁剪向量集合未就绪"
                    : "图片向量库未就绪");
        }
        if (embedding == null || embedding.length != properties.getDimension()) {
            throw new IllegalStateException("查询向量维度与 image-search.dimension 不一致");
        }
        int k = Math.max(1, Math.min(topK, VisualSearchLimits.MAX_INTERNAL));
        String collection = collectionName(variant);
        List<Float> vector = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            vector.add(value);
        }
        SearchResp response = client.search(SearchReq.builder()
                .collectionName(collection)
                .annsField("embedding")
                .data(List.of(new FloatVec(vector)))
                .topK(k)
                .filter(ImageSearchFilters.toMilvus(scope, company))
                .outputFields(List.of(
                        "vector_id", "source", "source_id", "slot", "company",
                        "oss_key", "goods_no", "title", "product_id"))
                .searchParams(Map.of("ef", 128))
                .build());
        List<List<SearchResp.SearchResult>> groups = response.getSearchResults();
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        List<ImageSearchModels.RawHit> hits = new ArrayList<>();
        for (SearchResp.SearchResult item : groups.get(0)) {
            Map<String, Object> entity = item.getEntity();
            if (entity == null) {
                continue;
            }
            ImageVectorRecord record = new ImageVectorRecord(
                    text(entity.get("vector_id")),
                    text(entity.get("source")),
                    text(entity.get("source_id")),
                    text(entity.get("slot")),
                    text(entity.get("company")),
                    text(entity.get("oss_key")),
                    text(entity.get("goods_no")),
                    text(entity.get("title")),
                    text(entity.get("product_id")));
            hits.add(new ImageSearchModels.RawHit(record, item.getScore()));
        }
        return hits;
    }

    private void openCropCollection() {
        String name = properties.resolvedCropCollection();
        if (name.equals(properties.getCollection())) {
            log.error("裁剪集合不能和整图集合同名: {}", name);
            cropReady = false;
            return;
        }
        try {
            ImageSearchFilters.assertCollectionName(name);
            ensureCollection(name);
            cropReady = true;
            log.info("裁剪向量集合就绪: {}", name);
        } catch (Exception e) {
            cropReady = false;
            log.error("裁剪向量集合未就绪，相似素材会退回整图集合: {}", e.getMessage());
        }
    }

    private String collectionName(EmbeddingVariant variant) {
        if (variant == EmbeddingVariant.CROP) {
            return properties.resolvedCropCollection();
        }
        return properties.getCollection();
    }

    private void ensureCollection(String name) {
        boolean exists = client.hasCollection(HasCollectionReq.builder().collectionName(name).build());
        if (exists) {
            client.loadCollection(LoadCollectionReq.builder().collectionName(name).build());
            log.info("图片向量集合已存在，不修改结构: {}", name);
            return;
        }
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("vector_id").dataType(DataType.VarChar).maxLength(160)
                .isPrimaryKey(true).autoID(false).build());
        schema.addField(varchar("source", 16));
        schema.addField(varchar("source_id", 64));
        schema.addField(varchar("slot", 16));
        schema.addField(varchar("company", 64));
        // max_length 是字节。中文路径和标题按 3 字节计，留出余量避免插入失败。
        schema.addField(varchar("oss_key", 4096));
        schema.addField(varchar("goods_no", 512));
        schema.addField(varchar("title", 2048));
        schema.addField(varchar("product_id", 1024));
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding").dataType(DataType.FloatVector)
                .dimension(properties.getDimension()).build());

        List<IndexParam> indexes = List.of(
                IndexParam.builder()
                        .fieldName("embedding")
                        .indexType(IndexParam.IndexType.HNSW)
                        .metricType(IndexParam.MetricType.COSINE)
                        .extraParams(Map.of("M", 16, "efConstruction", 200))
                        .build(),
                IndexParam.builder().fieldName("source").indexType(IndexParam.IndexType.TRIE).build(),
                IndexParam.builder().fieldName("company").indexType(IndexParam.IndexType.TRIE).build());

        client.createCollection(CreateCollectionReq.builder()
                .collectionName(name)
                .collectionSchema(schema)
                .indexParams(indexes)
                .enableDynamicField(false)
                .build());
        client.loadCollection(LoadCollectionReq.builder().collectionName(name).build());
        log.info("已创建图片向量集合 {} dimension={}", name, properties.getDimension());
    }

    private static AddFieldReq varchar(String name, int maxLength) {
        return AddFieldReq.builder()
                .fieldName(name)
                .dataType(DataType.VarChar)
                .maxLength(maxLength)
                .build();
    }

    private JsonObject toRow(ImageVectorRecord record, float[] embedding) {
        JsonObject row = new JsonObject();
        row.addProperty("vector_id", ImageSearchFilters.cutUtf8(record.vectorId(), 160));
        row.addProperty("source", ImageSearchFilters.cutUtf8(record.source(), 16));
        row.addProperty("source_id", ImageSearchFilters.cutUtf8(record.sourceId(), 64));
        row.addProperty("slot", ImageSearchFilters.cutUtf8(record.slot(), 16));
        row.addProperty("company", ImageSearchFilters.cutUtf8(record.company(), 64));
        row.addProperty("oss_key", ImageSearchFilters.cutUtf8(record.ossKey(), 4096));
        row.addProperty("goods_no", ImageSearchFilters.cutUtf8(record.goodsNo(), 512));
        row.addProperty("title", ImageSearchFilters.cutUtf8(record.title(), 2048));
        row.addProperty("product_id", ImageSearchFilters.cutUtf8(record.productId(), 1024));
        JsonArray vector = new JsonArray();
        for (float value : embedding) {
            vector.add(value);
        }
        row.add("embedding", vector);
        return row;
    }

    private void deleteQuiet(String collection, String vectorId) {
        try {
            client.delete(DeleteReq.builder()
                    .collectionName(collection)
                    .filter("vector_id == \"" + ImageSearchFilters.milvusQuote(vectorId) + "\"")
                    .build());
        } catch (Exception e) {
            log.debug("删除旧图片向量跳过: {} {}", vectorId, e.getMessage());
        }
    }

    private void ensureReady() {
        if (!isReady()) {
            throw new IllegalStateException("图片向量库未就绪");
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
