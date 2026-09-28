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

    public List<ImageSearchModels.RawHit> searchRaw(float[] embedding, String scope, String company, int topK) {
        int fetch = Math.min(100, Math.max(topK, topK * 3));
        List<ImageSearchModels.RawHit> raw = vectorIndex.search(embedding, scope, company, fetch);
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
}
