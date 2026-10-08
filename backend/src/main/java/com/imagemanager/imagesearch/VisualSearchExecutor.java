package com.imagemanager.imagesearch;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 弹层检索。范围页签决定场景：打样同款、素材参考、全部先同款再参考图。
 * 文本检索仍走同一向量空间，文本本身不裁剪。
 */
@Service
public class VisualSearchExecutor {

    private final ImageSearchProperties properties;
    private final ImageSearchQueryService queryService;
    private final MixedCatalogStrategy mixed = new MixedCatalogStrategy();

    public VisualSearchExecutor(ImageSearchProperties properties, ImageSearchQueryService queryService) {
        this.properties = properties;
        this.queryService = queryService;
    }

    public ImageSearchModels.ImageSearchResponse search(byte[] image, String filename, String text,
                                                        String scope, Integer topK, String company) {
        properties.requireEnabled();
        boolean hasImage = image != null && image.length > 0;
        boolean hasText = text != null && !text.isBlank();
        if (!hasImage && !hasText) {
            throw new IllegalArgumentException("请上传图片或输入中文描述");
        }
        if (hasImage && image.length > Math.min(properties.getMaxImageBytes(), 10 * 1024 * 1024)) {
            throw new IllegalArgumentException("图片不能超过 10MB");
        }
        int limit = topK == null || topK <= 0 ? properties.getTopK() : topK;
        limit = Math.max(1, Math.min(limit, 50));
        String normalizedScope = ImageSearchFilters.normalizeScope(scope);
        String normalizedCompany = ImageSearchFilters.normalizeCompany(company, properties.getDefaultCompany());
        VisualSearchScenario scenario = ScopeScenario.fromScope(normalizedScope);
        boolean cropReady = queryService.cropCollectionReady();
        long started = System.nanoTime();
        List<ImageSearchModels.ImageSearchHitView> results = scenario == VisualSearchScenario.MIXED
                ? searchMixed(image, filename, text, limit, normalizedCompany, cropReady)
                : searchOne(scenario, image, filename, text, normalizedScope, limit, normalizedCompany, cropReady);
        ImageSearchModels.ImageSearchResponse response = new ImageSearchModels.ImageSearchResponse();
        response.setEnabled(true);
        response.setMode(hasImage ? "image" : "text");
        response.setScenario(scenario.name());
        response.setTookMs((int) ((System.nanoTime() - started) / 1_000_000L));
        response.setResults(results);
        return response;
    }

    private List<ImageSearchModels.ImageSearchHitView> searchOne(VisualSearchScenario scenario, byte[] image,
                                                                 String filename, String text, String scope,
                                                                 int limit, String company, boolean cropReady) {
        VisualSearchStrategy strategy = VisualSearchStrategies.of(scenario);
        EmbeddingVariant variant = EmbeddingVariantSelector.select(scenario, properties, cropReady);
        List<ImageSearchModels.ImageSearchHitView> hits = queryService.collect(
                image, filename, text, scope, strategy.internalTopK(limit, properties), company, variant);
        return strategy.assemble(hits, 0d, limit, properties);
    }

    private List<ImageSearchModels.ImageSearchHitView> searchMixed(byte[] image, String filename, String text,
                                                                  int limit, String company, boolean cropReady) {
        EmbeddingVariant productVariant = EmbeddingVariantSelector.select(
                VisualSearchScenario.SAME_PRODUCT, properties, cropReady);
        EmbeddingVariant referenceVariant = EmbeddingVariantSelector.select(
                VisualSearchScenario.SIMILAR_REFERENCE, properties, cropReady);
        int fetch = mixed.internalTopK(limit, properties);
        if (productVariant == referenceVariant) {
            List<ImageSearchModels.ImageSearchHitView> hits = queryService.collect(
                    image, filename, text, "all", fetch, company, productVariant);
            return mixed.present(hits, hits, 0d, limit, properties);
        }
        List<ImageSearchModels.ImageSearchHitView> products = queryService.collect(
                image, filename, text, "all", fetch, company, productVariant);
        List<ImageSearchModels.ImageSearchHitView> references = queryService.collect(
                image, filename, text, "library", fetch, company, referenceVariant);
        return mixed.present(products, references, 0d, limit, properties);
    }
}
