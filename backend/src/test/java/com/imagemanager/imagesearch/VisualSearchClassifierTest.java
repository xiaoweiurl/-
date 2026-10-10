package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisualSearchClassifierTest {

    @Test
    void sameProductPhrases() {
        for (String message : new String[]{
                "这款做过吗",
                "我们做过这款吗",
                "这个是什么货号",
                "哪个货号",
                "找历史打样",
                "帮我搜同款",
                "找同款",
                "像这款"
        }) {
            VisualSearchClassifier.Intent intent = VisualSearchClassifier.classify(message);
            assertEquals(VisualSearchScenario.SAME_PRODUCT, intent.scenario(), message);
            assertFalse(intent.probe(), message);
        }
    }

    @Test
    void similarReferencePhrases() {
        for (String message : new String[]{
                "找相似的素材",
                "有没有参考图",
                "类似风格",
                "帮我找图",
                "搜图",
                "找相似款",
                "有没有类似的",
                "像这张"
        }) {
            VisualSearchClassifier.Intent intent = VisualSearchClassifier.classify(message);
            assertEquals(VisualSearchScenario.SIMILAR_REFERENCE, intent.scenario(), message);
            assertFalse(intent.probe(), message);
        }
    }

    @Test
    void textDefectAndFabricStayOutOfSearch() {
        for (String message : new String[]{
                "请读出图片上的文字",
                "这张图有什么瑕疵",
                "分析一下这块面料的成分",
                "请把图上的文字全部读出来",
                "所有瑕疵都指出来"
        }) {
            VisualSearchClassifier.Intent intent = VisualSearchClassifier.classify(message);
            assertEquals(VisualSearchScenario.NON_SEARCH, intent.scenario(), message);
            assertFalse(intent.probe(), message);
        }
    }

    @Test
    void similarAskStillSearchesWhenFabricIsMentioned() {
        VisualSearchClassifier.Intent intent = VisualSearchClassifier.classify("有没有类似的面料");
        assertEquals(VisualSearchScenario.SIMILAR_REFERENCE, intent.scenario());
    }

    @Test
    void allOverrideAppliesToBothScenarios() {
        assertTrue(VisualSearchClassifier.classify("不限数量找同款").all());
        assertEquals(VisualSearchScenario.SAME_PRODUCT, VisualSearchClassifier.classify("不限数量找同款").scenario());
        assertTrue(VisualSearchClassifier.classify("找相似款，全部列出来").all());
        assertEquals(VisualSearchScenario.SIMILAR_REFERENCE,
                VisualSearchClassifier.classify("找相似款，全部列出来").scenario());
        assertTrue(VisualSearchClassifier.classify("全部").probe());
        assertTrue(VisualSearchClassifier.classify("全部").all());
    }

    @Test
    void shortTextWaitsForTheTopHit() {
        assertTrue(VisualSearchClassifier.classify(null).probe());
        assertTrue(VisualSearchClassifier.classify("").probe());
        assertTrue(VisualSearchClassifier.classify("看看").probe());
        assertFalse(VisualSearchClassifier.classify("看看").all());
    }

    @Test
    void probeUsesGoodsHitOnlyAboveTheThreshold() {
        ImageSearchModels.ImageSearchHitView goods = new ImageSearchModels.ImageSearchHitView();
        goods.setSource(ImageSearchFilters.SOURCE_GOODS);
        goods.setScore(0.62f);
        assertEquals(VisualSearchScenario.SAME_PRODUCT, VisualSearchClassifier.fromProbe(List.of(goods), 0.45d));

        goods.setScore(0.40f);
        assertEquals(VisualSearchScenario.SIMILAR_REFERENCE, VisualSearchClassifier.fromProbe(List.of(goods), 0.45d));

        ImageSearchModels.ImageSearchHitView library = new ImageSearchModels.ImageSearchHitView();
        library.setSource(ImageSearchFilters.SOURCE_LIBRARY);
        library.setScore(0.91f);
        assertEquals(VisualSearchScenario.SIMILAR_REFERENCE, VisualSearchClassifier.fromProbe(List.of(library), 0.45d));
        assertEquals(VisualSearchScenario.SIMILAR_REFERENCE, VisualSearchClassifier.fromProbe(List.of(), 0.45d));
    }

    @Test
    void probeKeepsSameProductWhenALibraryCopyOutranksTheGoodsImage() {
        ImageSearchModels.ImageSearchHitView library = new ImageSearchModels.ImageSearchHitView();
        library.setSource(ImageSearchFilters.SOURCE_LIBRARY);
        library.setScore(0.90f);
        ImageSearchModels.ImageSearchHitView goods = new ImageSearchModels.ImageSearchHitView();
        goods.setSource(ImageSearchFilters.SOURCE_GOODS);
        goods.setScore(0.80f);
        assertEquals(VisualSearchScenario.SAME_PRODUCT,
                VisualSearchClassifier.fromProbe(List.of(library, goods), 0.45d));

        goods.setScore(0.50f);
        assertEquals(VisualSearchScenario.SIMILAR_REFERENCE,
                VisualSearchClassifier.fromProbe(List.of(library, goods), 0.45d));
    }

    @Test
    void probeUsesALibraryImageThatAlreadyHasOneGoodsLink() {
        ImageSearchModels.ImageSearchHitView library = new ImageSearchModels.ImageSearchHitView();
        library.setSource(ImageSearchFilters.SOURCE_LIBRARY);
        library.setScore(0.70f);
        ImageSearchModels.GoodsBrief brief = new ImageSearchModels.GoodsBrief();
        brief.setId(9);
        brief.setGoodsNo("H100");
        library.setRelatedGoods(List.of(brief));
        assertEquals(VisualSearchScenario.SAME_PRODUCT, VisualSearchClassifier.fromProbe(List.of(library), 0.45d));
    }

    @Test
    void scopeTabsMapToScenarios() {
        assertEquals(VisualSearchScenario.SAME_PRODUCT, ScopeScenario.fromScope("goods"));
        assertEquals(VisualSearchScenario.SIMILAR_REFERENCE, ScopeScenario.fromScope("library"));
        assertEquals(VisualSearchScenario.MIXED, ScopeScenario.fromScope("all"));
    }
}
