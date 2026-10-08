package com.imagemanager.imagesearch;

import java.util.List;

/**
 * 同款。向 Milvus 多取几倍，再按商品收成一张卡片。
 */
public final class SameProductStrategy implements VisualSearchStrategy {

    @Override
    public VisualSearchScenario scenario() {
        return VisualSearchScenario.SAME_PRODUCT;
    }

    @Override
    public int internalTopK(int requested, ImageSearchProperties properties) {
        int factor = properties == null ? 5 : properties.getInternalTopKFactor();
        if (factor < 1) {
            factor = 5;
        }
        int asked = Math.max(1, requested);
        return VisualSearchLimits.capInternal(asked * factor);
    }

    @Override
    public List<ImageSearchModels.ImageSearchHitView> assemble(List<ImageSearchModels.ImageSearchHitView> hits,
                                                               double minScore,
                                                               int limit,
                                                               ImageSearchProperties properties) {
        double bonus = properties == null ? 0.02d : properties.getSameProductMultiBonus();
        double cap = properties == null ? 0.06d : properties.getSameProductMultiBonusCap();
        return SameProductGrouping.group(hits, limit, minScore, bonus, cap);
    }
}
