package com.imagemanager.imagesearch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 弹层「全部」：同款卡片在前，没被收进同款的素材图跟在后面。
 */
public final class MixedCatalogStrategy implements VisualSearchStrategy {

    private final SameProductStrategy products = new SameProductStrategy();
    private final SimilarReferenceStrategy references = new SimilarReferenceStrategy();

    @Override
    public VisualSearchScenario scenario() {
        return VisualSearchScenario.MIXED;
    }

    @Override
    public int internalTopK(int requested, ImageSearchProperties properties) {
        return Math.max(products.internalTopK(requested, properties), references.internalTopK(requested, properties));
    }

    @Override
    public List<ImageSearchModels.ImageSearchHitView> assemble(List<ImageSearchModels.ImageSearchHitView> hits,
                                                               double minScore,
                                                               int limit,
                                                               ImageSearchProperties properties) {
        return present(hits, hits, minScore, limit, properties);
    }

    public List<ImageSearchModels.ImageSearchHitView> present(List<ImageSearchModels.ImageSearchHitView> productPool,
                                                              List<ImageSearchModels.ImageSearchHitView> referencePool,
                                                              double minScore,
                                                              int limit,
                                                              ImageSearchProperties properties) {
        List<ImageSearchModels.ImageSearchHitView> productCards = products.assemble(
                groupable(productPool), minScore, limit, properties);
        Set<String> used = new HashSet<>();
        for (ImageSearchModels.ImageSearchHitView card : productCards) {
            used.add(imageKey(card.getSource(), card.getSourceId(), card.getSlot()));
            if (card.getImages() == null) {
                continue;
            }
            for (ImageSearchModels.ImageThumb thumb : card.getImages()) {
                used.add(imageKey(thumb.getSource(), thumb.getSourceId(), thumb.getSlot()));
            }
        }
        List<ImageSearchModels.ImageSearchHitView> leftover = new ArrayList<>();
        if (referencePool != null) {
            for (ImageSearchModels.ImageSearchHitView hit : referencePool) {
                if (hit == null || ImageSearchFilters.SOURCE_GOODS.equals(hit.getSource())) {
                    continue;
                }
                if (used.contains(imageKey(hit.getSource(), hit.getSourceId(), hit.getSlot()))) {
                    continue;
                }
                leftover.add(hit);
            }
        }
        List<ImageSearchModels.ImageSearchHitView> imageCards = references.assemble(leftover, minScore, limit, properties);
        List<ImageSearchModels.ImageSearchHitView> ordered = new ArrayList<>(productCards.size() + imageCards.size());
        ordered.addAll(productCards);
        ordered.addAll(imageCards);
        for (ImageSearchModels.ImageSearchHitView hit : ordered) {
            hit.setScenario(VisualSearchScenario.MIXED.name());
        }
        return List.copyOf(ordered);
    }

    /**
     * 全部页里，没有货号、也连不上商品的素材不进同款卡片，留到后面当参考图。
     */
    private static List<ImageSearchModels.ImageSearchHitView> groupable(List<ImageSearchModels.ImageSearchHitView> hits) {
        Map<String, Integer> productIds = new HashMap<>();
        if (hits != null) {
            for (ImageSearchModels.ImageSearchHitView hit : hits) {
                String productId = productIdKey(hit);
                if (!productId.isEmpty()) {
                    productIds.merge(productId, 1, Integer::sum);
                }
            }
        }
        List<ImageSearchModels.ImageSearchHitView> kept = new ArrayList<>();
        if (hits == null) {
            return kept;
        }
        for (ImageSearchModels.ImageSearchHitView hit : hits) {
            if (hit == null) {
                continue;
            }
            if (ImageSearchFilters.SOURCE_GOODS.equals(hit.getSource())
                    || SameProductGrouping.reliableLink(hit) != null
                    || productIds.getOrDefault(productIdKey(hit), 0) > 1) {
                kept.add(hit);
            }
        }
        return kept;
    }

    private static String productIdKey(ImageSearchModels.ImageSearchHitView hit) {
        if (hit == null || hit.getProductId() == null || hit.getProductId().isBlank()) {
            return "";
        }
        if (ImageSearchFilters.SOURCE_GOODS.equals(hit.getSource())) {
            return "";
        }
        String company = hit.getCompany() == null ? "" : hit.getCompany().trim();
        return company + "|" + hit.getProductId().trim();
    }

    private static String imageKey(String source, String sourceId, String slot) {
        return text(source) + "|" + text(sourceId) + "|" + text(slot);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
