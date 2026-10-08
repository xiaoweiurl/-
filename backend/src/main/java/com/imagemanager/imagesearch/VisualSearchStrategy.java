package com.imagemanager.imagesearch;

import java.util.List;

/**
 * 一个场景一种结果形态。实现类里不再判断“是不是另一个场景”。
 */
public interface VisualSearchStrategy {

    VisualSearchScenario scenario();

    /** 向 Milvus 多取的条数，保证分组之后仍够返回。 */
    int internalTopK(int requested, ImageSearchProperties properties);

    List<ImageSearchModels.ImageSearchHitView> assemble(List<ImageSearchModels.ImageSearchHitView> hits,
                                                        double minScore,
                                                        int limit,
                                                        ImageSearchProperties properties);
}
