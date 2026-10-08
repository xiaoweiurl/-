package com.imagemanager.imagesearch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测里按场景拆开的指标。同款看商品是否出现，相似素材仍按单张图。
 */
public final class ImageSearchScenarioMetrics {

    private ImageSearchScenarioMetrics() {
    }

    public static Map<String, Object> scenarios(int k,
                                               List<List<String>> imageRelevant,
                                               List<List<String>> imageRetrieved,
                                               List<List<String>> productRelevant,
                                               List<List<String>> productRetrieved) {
        int at5 = Math.min(5, Math.max(k, 1));
        Map<String, Object> same = new LinkedHashMap<>();
        same.put("productRecallAt1", round(ImageSearchRecall.hitRate(productRelevant, productRetrieved, 1)));
        same.put("productRecallAt5", round(ImageSearchRecall.hitRate(productRelevant, productRetrieved, at5)));
        same.put("productRecallAtK", round(ImageSearchRecall.hitRate(productRelevant, productRetrieved, k)));
        same.put("queries", productRelevant == null ? 0 : productRelevant.size());
        same.put("note", "去掉查询图自己之后按商品合并。TopK 里只要有同一货号或同一商品的任意一张图，这条查询就命中。");

        Map<String, Object> similar = new LinkedHashMap<>();
        similar.put("recallAt1", round(ImageSearchRecall.meanRecall(imageRelevant, imageRetrieved, 1)));
        similar.put("recallAt5", round(ImageSearchRecall.meanRecall(imageRelevant, imageRetrieved, at5)));
        similar.put("recallAtK", round(ImageSearchRecall.meanRecall(imageRelevant, imageRetrieved, k)));
        similar.put("queries", imageRelevant == null ? 0 : imageRelevant.size());
        similar.put("note", "不分组，按单张图计算 Recall，用来和整图集合的旧口径对比。");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(VisualSearchScenario.SAME_PRODUCT.name(), same);
        body.put(VisualSearchScenario.SIMILAR_REFERENCE.name(), similar);
        return body;
    }

    private static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }
}
