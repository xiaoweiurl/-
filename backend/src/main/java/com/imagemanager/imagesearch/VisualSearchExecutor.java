package com.imagemanager.imagesearch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹层检索。范围页签决定场景：打样同款、素材参考、全部先同款再参考图。
 * 图片旁边的文字按条件解释：范围和公司进向量过滤，时间、打样员、相册在补全后过滤。
 */
@Service
public class VisualSearchExecutor {

    private final ImageSearchProperties properties;
    private final ImageSearchQueryService queryService;
    private final ImageSearchConditionApplier conditions;
    private final ImageSearchRecordLinks recordLinks;
    private final MixedCatalogStrategy mixed = new MixedCatalogStrategy();

    public VisualSearchExecutor(ImageSearchProperties properties, ImageSearchQueryService queryService) {
        this(properties, queryService, null);
    }

    public VisualSearchExecutor(ImageSearchProperties properties,
                                ImageSearchQueryService queryService,
                                ImageSearchConditionApplier conditions) {
        this(properties, queryService, conditions, null);
    }

    /**
     * Spring 注入构造器。必须标 {@code @Autowired}：同类还有给测试用的短构造器，
     * 不标明时 Spring 会退回不存在的无参构造。
     */
    @Autowired
    public VisualSearchExecutor(ImageSearchProperties properties,
                                ImageSearchQueryService queryService,
                                ImageSearchConditionApplier conditions,
                                ImageSearchRecordLinks recordLinks) {
        this.properties = properties;
        this.queryService = queryService;
        this.conditions = conditions;
        this.recordLinks = recordLinks;
    }

    public ImageSearchModels.ImageSearchResponse search(byte[] image, String filename, String text,
                                                        String scope, Integer topK, String company) {
        return search(image, filename, text, scope, topK, company, null);
    }

    public ImageSearchModels.ImageSearchResponse search(byte[] image, String filename, String text,
                                                        String scope, Integer topK, String company,
                                                        String exclude) {
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
        String uiScope = ImageSearchFilters.normalizeScope(scope);
        String normalizedCompany = ImageSearchFilters.normalizeCompany(company, properties.getDefaultCompany());
        ImageSearchCondition.Parsed parsed = ImageSearchCondition.Parsed.empty();
        if (hasImage && hasText && conditions != null) {
            parsed = conditions.parse(text, exclude);
        }
        String queryText = hasImage ? null : text;
        String effectiveScope = parsed.scope() != null ? parsed.scope() : uiScope;
        VisualSearchScenario scenario = ScopeScenario.fromScope(uiScope);
        boolean cropReady = queryService.cropCollectionReady();
        long started = System.nanoTime();
        Gathered gathered = gather(scenario, image, filename, queryText, effectiveScope, limit,
                normalizedCompany, cropReady, parsed);
        ImageSearchConditionRank.Outcome outcome = apply(union(gathered), parsed, gathered.variant());
        if (outcome.hits().isEmpty() && parsed.scope() != null && !parsed.scope().equals(uiScope)) {
            Gathered wider = gather(scenario, image, filename, queryText, uiScope, limit,
                    normalizedCompany, cropReady, parsed.withoutScope());
            ImageSearchConditionRank.Outcome relaxed = apply(union(wider), parsed.withoutScope(), wider.variant());
            if (!relaxed.hits().isEmpty()) {
                outcome = ImageSearchConditionRank.relaxScope(relaxed, scopeChip(parsed));
                gathered = wider;
            }
        }
        List<ImageSearchModels.ImageSearchHitView> results = present(scenario, gathered, outcome, limit);
        attachLinks(results, normalizedCompany);
        ImageSearchModels.ImageSearchResponse response = new ImageSearchModels.ImageSearchResponse();
        response.setEnabled(true);
        response.setMode(hasImage ? "image" : "text");
        response.setScenario(scenario.name());
        response.setTookMs((int) ((System.nanoTime() - started) / 1_000_000L));
        response.setResults(results);
        response.setFilters(ImageSearchConditionRank.toViews(outcome.filters()));
        response.setFilterNotice(outcome.notice());
        response.setFilterRelaxed(outcome.relaxed());
        return response;
    }

    /**
     * 打样页拍照查同款。只查本公司商品图，按货号收成同款卡片。素材库进不来。
     */
    public ImageSearchModels.ImageSearchResponse searchSampler(byte[] image, String filename,
                                                               Integer topK, String company) {
        properties.requireEnabled();
        if (!properties.isSamplerEnabled()) {
            throw new ImageSearchDisabledException("拍照查同款未开启");
        }
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("请先拍照或从相册选择图片");
        }
        if (image.length > Math.min(properties.getMaxImageBytes(), 10 * 1024 * 1024)) {
            throw new IllegalArgumentException("图片不能超过 10MB");
        }
        int limit = topK == null || topK <= 0 ? properties.getChatTopK() : topK;
        limit = Math.max(1, Math.min(limit, 50));
        String normalizedCompany = ImageSearchFilters.normalizeCompany(company, properties.getDefaultCompany());
        boolean cropReady = queryService.cropCollectionReady();
        long started = System.nanoTime();
        Gathered gathered = gather(VisualSearchScenario.SAME_PRODUCT, image, filename, null,
                ImageSearchFilters.SOURCE_GOODS_COMPANY, limit, normalizedCompany, cropReady,
                ImageSearchCondition.Parsed.empty());
        List<ImageSearchModels.ImageSearchHitView> results = new ArrayList<>();
        for (ImageSearchModels.ImageSearchHitView hit : present(
                VisualSearchScenario.SAME_PRODUCT, gathered,
                ImageSearchConditionRank.Outcome.unchanged(gathered.hits()), limit)) {
            if (hit != null && ImageSearchFilters.SOURCE_GOODS.equals(hit.getSource())) {
                results.add(hit);
            }
        }
        attachLinks(results, normalizedCompany);
        ImageSearchModels.ImageSearchResponse response = new ImageSearchModels.ImageSearchResponse();
        response.setEnabled(true);
        response.setMode("image");
        response.setScenario(VisualSearchScenario.SAME_PRODUCT.name());
        response.setTookMs((int) ((System.nanoTime() - started) / 1_000_000L));
        response.setResults(results);
        return response;
    }

    private void attachLinks(List<ImageSearchModels.ImageSearchHitView> hits, String company) {
        if (recordLinks == null || hits == null || hits.isEmpty()) {
            return;
        }
        recordLinks.attach(hits, company);
    }

    private Gathered gather(VisualSearchScenario scenario, byte[] image, String filename, String text,
                            String scope, int limit, String company, boolean cropReady,
                            ImageSearchCondition.Parsed parsed) {
        if (scenario == VisualSearchScenario.MIXED && "all".equals(scope)) {
            EmbeddingVariant productVariant = EmbeddingVariantSelector.select(
                    VisualSearchScenario.SAME_PRODUCT, properties, cropReady);
            EmbeddingVariant referenceVariant = EmbeddingVariantSelector.select(
                    VisualSearchScenario.SIMILAR_REFERENCE, properties, cropReady);
            int fetch = widen(mixed.internalTopK(limit, properties), parsed);
            if (productVariant == referenceVariant) {
                List<ImageSearchModels.ImageSearchHitView> hits = queryService.collect(
                        image, filename, text, "all", fetch, company, productVariant);
                return new Gathered(hits, hits, productVariant, scope);
            }
            List<ImageSearchModels.ImageSearchHitView> products = queryService.collect(
                    image, filename, text, "all", fetch, company, productVariant);
            List<ImageSearchModels.ImageSearchHitView> references = queryService.collect(
                    image, filename, text, "library", fetch, company, referenceVariant);
            return new Gathered(products, references, productVariant, scope);
        }
        VisualSearchScenario variantScenario = scenario == VisualSearchScenario.MIXED
                ? VisualSearchScenario.SAME_PRODUCT : scenario;
        EmbeddingVariant variant = EmbeddingVariantSelector.select(variantScenario, properties, cropReady);
        VisualSearchStrategy strategy = scenario == VisualSearchScenario.MIXED
                ? mixed : VisualSearchStrategies.of(scenario);
        int fetch = widen(strategy.internalTopK(limit, properties), parsed);
        List<ImageSearchModels.ImageSearchHitView> hits = queryService.collect(
                image, filename, text, scope, fetch, company, variant);
        return new Gathered(hits, hits, variant, scope);
    }

    private List<ImageSearchModels.ImageSearchHitView> present(VisualSearchScenario scenario, Gathered gathered,
                                                               ImageSearchConditionRank.Outcome outcome, int limit) {
        boolean untouched = outcome.filters().isEmpty() && !outcome.relaxed();
        if (scenario == VisualSearchScenario.MIXED) {
            List<ImageSearchModels.ImageSearchHitView> products = untouched
                    ? gathered.hits() : matching(outcome.hits(), gathered.hits());
            List<ImageSearchModels.ImageSearchHitView> references = untouched
                    ? gathered.references() : matching(outcome.hits(), gathered.references());
            return mixed.present(products, references, 0d, limit, properties);
        }
        VisualSearchStrategy strategy = VisualSearchStrategies.of(scenario);
        List<ImageSearchModels.ImageSearchHitView> hits = untouched ? gathered.hits() : outcome.hits();
        return strategy.assemble(hits, 0d, limit, properties);
    }

    private ImageSearchConditionRank.Outcome apply(List<ImageSearchModels.ImageSearchHitView> hits,
                                                   ImageSearchCondition.Parsed parsed,
                                                   EmbeddingVariant variant) {
        if (conditions == null || parsed == null || !parsed.active()) {
            return ImageSearchConditionRank.Outcome.unchanged(hits);
        }
        return conditions.apply(hits, parsed, variant);
    }

    private int widen(int fetch, ImageSearchCondition.Parsed parsed) {
        if (conditions == null) {
            return fetch;
        }
        return conditions.widen(fetch, parsed);
    }

    private static List<ImageSearchModels.ImageSearchHitView> union(Gathered gathered) {
        if (gathered.references() == gathered.hits() || gathered.references() == null) {
            return gathered.hits();
        }
        List<ImageSearchModels.ImageSearchHitView> all = new ArrayList<>(gathered.hits());
        for (ImageSearchModels.ImageSearchHitView hit : gathered.references()) {
            if (!poolContains(all, hit)) {
                all.add(hit);
            }
        }
        return all;
    }

    private static List<ImageSearchModels.ImageSearchHitView> matching(List<ImageSearchModels.ImageSearchHitView> kept,
                                                                       List<ImageSearchModels.ImageSearchHitView> pool) {
        List<ImageSearchModels.ImageSearchHitView> matched = new ArrayList<>();
        if (kept == null || pool == null) {
            return matched;
        }
        for (ImageSearchModels.ImageSearchHitView hit : kept) {
            if (poolContains(pool, hit)) {
                matched.add(hit);
            }
        }
        return matched;
    }

    private static boolean poolContains(List<ImageSearchModels.ImageSearchHitView> pool,
                                        ImageSearchModels.ImageSearchHitView hit) {
        if (pool == null || hit == null) {
            return false;
        }
        String key = key(hit);
        for (ImageSearchModels.ImageSearchHitView item : pool) {
            if (key.equals(key(item))) {
                return true;
            }
        }
        return false;
    }

    private static String key(ImageSearchModels.ImageSearchHitView hit) {
        if (hit == null) {
            return "";
        }
        return text(hit.getSource()) + "|" + text(hit.getSourceId()) + "|" + text(hit.getSlot())
                + "|" + text(hit.getVectorId());
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static ImageSearchCondition.Chip scopeChip(ImageSearchCondition.Parsed parsed) {
        if (parsed == null || parsed.chips() == null) {
            return null;
        }
        for (ImageSearchCondition.Chip chip : parsed.chips()) {
            if ("scope".equals(chip.id())) {
                return chip;
            }
        }
        return null;
    }

    private record Gathered(List<ImageSearchModels.ImageSearchHitView> hits,
                            List<ImageSearchModels.ImageSearchHitView> references,
                            EmbeddingVariant variant,
                            String scope) {
    }
}
