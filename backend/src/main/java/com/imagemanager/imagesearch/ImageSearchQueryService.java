package com.imagemanager.imagesearch;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 以图或中文文本检索。结果会再按公司和来源滤一遍。
 */
@Service
public class ImageSearchQueryService {

    private final ImageSearchProperties properties;
    private final ImageEmbedder embedder;
    private final ImageVectorIndex vectorIndex;
    private final ImageSearchEnricher enricher;

    public ImageSearchQueryService(ImageSearchProperties properties,
                                   ImageEmbedder embedder,
                                   ImageVectorIndex vectorIndex,
                                   ImageSearchEnricher enricher) {
        this.properties = properties;
        this.embedder = embedder;
        this.vectorIndex = vectorIndex;
        this.enricher = enricher;
    }

    public ImageSearchModels.ImageSearchResponse search(byte[] image, String filename, String text,
                                                        String scope, Integer topK, String company) {
        properties.requireEnabled();
        if (!vectorIndex.isReady() && vectorIndex instanceof MilvusImageVectorIndex milvus) {
            milvus.initIfEnabled();
        }
        if (!vectorIndex.isReady()) {
            throw new IllegalStateException("图片向量库未就绪");
        }
        boolean hasImage = image != null && image.length > 0;
        boolean hasText = text != null && !text.isBlank();
        if (!hasImage && !hasText) {
            throw new IllegalArgumentException("请上传图片或输入中文描述");
        }
        if (hasImage && image.length > Math.min(properties.getMaxImageBytes(), 10 * 1024 * 1024)) {
            throw new IllegalArgumentException("图片不能超过 10MB");
        }
        int k = topK == null || topK <= 0 ? properties.getTopK() : topK;
        k = Math.max(1, Math.min(k, 50));
        String normalizedScope = ImageSearchFilters.normalizeScope(scope);
        String normalizedCompany = ImageSearchFilters.normalizeCompany(company, properties.getDefaultCompany());
        long started = System.nanoTime();
        float[] embedding = hasImage
                ? embedder.embedImage(image, filename == null ? "query.jpg" : filename)
                : embedder.embedText(text.trim());
        List<ImageSearchModels.RawHit> raw = searchRaw(embedding, normalizedScope, normalizedCompany, k);
        List<ImageSearchModels.ImageSearchHitView> views = enricher.enrich(raw, normalizedCompany);
        if (views.size() > k) {
            views = new ArrayList<>(views.subList(0, k));
        }
        ImageSearchModels.ImageSearchResponse response = new ImageSearchModels.ImageSearchResponse();
        response.setEnabled(true);
        response.setMode(hasImage ? "image" : "text");
        response.setTookMs((int) ((System.nanoTime() - started) / 1_000_000L));
        response.setResults(views);
        return response;
    }

    public boolean cropCollectionReady() {
        return properties.isCropEnabled() && vectorIndex.supports(EmbeddingVariant.CROP);
    }

    /**
     * 按指定集合取出补全后的命中，不做场景分组。条数是内部分组用的，可以大于对用户展示的条数。
     */
    public List<ImageSearchModels.ImageSearchHitView> collect(byte[] image, String filename, String text,
                                                              String scope, int fetch, String company,
                                                              EmbeddingVariant variant) {
        properties.requireEnabled();
        ensureReady();
        boolean hasImage = image != null && image.length > 0;
        boolean hasText = text != null && !text.isBlank();
        if (!hasImage && !hasText) {
            throw new IllegalArgumentException("请上传图片或输入中文描述");
        }
        if (hasImage && image.length > Math.min(properties.getMaxImageBytes(), 10 * 1024 * 1024)) {
            throw new IllegalArgumentException("图片不能超过 10MB");
        }
        EmbeddingVariant actual = resolveVariant(variant);
        String normalizedScope = ImageSearchFilters.normalizeScope(scope);
        String normalizedCompany = ImageSearchFilters.normalizeCompany(company, properties.getDefaultCompany());
        float[] embedding = hasImage
                ? embedder.embedImage(image, filename == null ? "query.jpg" : filename, actual == EmbeddingVariant.CROP)
                : embedder.embedText(text.trim());
        return collectEmbedded(embedding, normalizedScope, fetch, normalizedCompany, actual);
    }

    public List<ImageSearchModels.ImageSearchHitView> collectEmbedded(float[] embedding, String scope, int fetch,
                                                                      String company, EmbeddingVariant variant) {
        properties.requireEnabled();
        ensureReady();
        EmbeddingVariant actual = resolveVariant(variant);
        String normalizedCompany = ImageSearchFilters.normalizeCompany(company, properties.getDefaultCompany());
        List<ImageSearchModels.RawHit> raw = searchRaw(embedding, scope, normalizedCompany, Math.max(fetch, 1), actual);
        return enricher.enrich(raw, normalizedCompany);
    }

    public List<ImageSearchModels.RawHit> searchRaw(float[] embedding, String scope, String company, int topK) {
        return searchRaw(embedding, scope, company, topK, EmbeddingVariant.FULL);
    }

    public List<ImageSearchModels.RawHit> searchRaw(float[] embedding, String scope, String company, int topK,
                                                    EmbeddingVariant variant) {
        EmbeddingVariant actual = resolveVariant(variant);
        int fetch = VisualSearchLimits.capInternal(Math.max(topK, topK * 3));
        List<ImageSearchModels.RawHit> raw = vectorIndex.search(actual, embedding, scope, company, fetch);
        List<ImageSearchModels.RawHit> kept = new ArrayList<>();
        for (ImageSearchModels.RawHit hit : raw) {
            if (ImageSearchFilters.matches(hit.record(), scope, company)) {
                kept.add(hit);
            }
            if (kept.size() >= fetch) {
                break;
            }
        }
        return kept;
    }

    private EmbeddingVariant resolveVariant(EmbeddingVariant variant) {
        if (variant == EmbeddingVariant.CROP && !cropCollectionReady()) {
            return EmbeddingVariant.FULL;
        }
        return variant == null ? EmbeddingVariant.FULL : variant;
    }

    private void ensureReady() {
        if (!vectorIndex.isReady() && vectorIndex instanceof MilvusImageVectorIndex milvus) {
            milvus.initIfEnabled();
        }
        if (!vectorIndex.isReady()) {
            throw new IllegalStateException("图片向量库未就绪");
        }
    }
}
