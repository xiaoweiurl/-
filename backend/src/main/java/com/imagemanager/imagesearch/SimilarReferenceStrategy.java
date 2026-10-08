package com.imagemanager.imagesearch;

import java.util.List;

/**
 * 相似素材。一张图一条，不按货号合并。检索用主体裁剪向量（集合没就绪时由选择器退回整图）。
 */
public final class SimilarReferenceStrategy implements VisualSearchStrategy {

    @Override
    public VisualSearchScenario scenario() {
        return VisualSearchScenario.SIMILAR_REFERENCE;
    }

    @Override
    public int internalTopK(int requested, ImageSearchProperties properties) {
        int asked = Math.max(1, requested);
        return VisualSearchLimits.capInternal(Math.max(asked, asked * 4));
    }

    @Override
    public List<ImageSearchModels.ImageSearchHitView> assemble(List<ImageSearchModels.ImageSearchHitView> hits,
                                                               double minScore,
                                                               int limit,
                                                               ImageSearchProperties properties) {
        List<ImageSearchModels.ImageSearchHitView> kept = ChatVisualSearchRank.select(hits, minScore, limit);
        for (ImageSearchModels.ImageSearchHitView hit : kept) {
            hit.setCardType("image");
            hit.setScenario(VisualSearchScenario.SIMILAR_REFERENCE.name());
        }
        return kept;
    }
}
