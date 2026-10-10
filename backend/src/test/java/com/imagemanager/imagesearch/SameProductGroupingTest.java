package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SameProductGroupingTest {

    @Test
    void goodsSlotsOfTheSameStyleBecomeOneCard() {
        List<ImageSearchModels.ImageSearchHitView> hits = List.of(
                goods("9", "side", 0.55f, "H100", "蕾丝中筒", "https://img/side"),
                goods("9", "main", 0.80f, "H100", "蕾丝中筒", "https://img/main"),
                goods("9", "detail", 0.61f, "H100", "蕾丝中筒", "https://img/detail"),
                goods("8", "main", 0.70f, "H80", "棉袜", "https://img/other")
        );
        List<ImageSearchModels.ImageSearchHitView> cards = SameProductGrouping.group(hits, 5, 0.30d, 0.02d, 0.06d);
        assertEquals(2, cards.size());
        ImageSearchModels.ImageSearchHitView first = cards.get(0);
        assertEquals("product", first.getCardType());
        assertEquals("https://img/main", first.getImageUrl());
        assertEquals("H100", first.getGoods().getGoodsNo());
        assertEquals(2, first.getImages().size());
        assertEquals("https://img/detail", first.getImages().get(0).getImageUrl());
        assertEquals(0.84f, first.getScore(), 0.001f);
        assertEquals("H80", cards.get(1).getGoods().getGoodsNo());
        assertTrue(cards.get(1).getImages().isEmpty());
    }

    @Test
    void libraryWithoutAReliableLinkStaysAlone() {
        ImageSearchModels.ImageSearchHitView alone = library("lib-1", 0.66f, "");
        ImageSearchModels.ImageSearchHitView other = library("lib-2", 0.40f, "");
        List<ImageSearchModels.ImageSearchHitView> cards = SameProductGrouping.group(List.of(alone, other), 5, 0d, 0.02d, 0.06d);
        assertEquals(2, cards.size());
        assertEquals("lib-1", cards.get(0).getSourceId());
        assertEquals("lib-2", cards.get(1).getSourceId());
    }

    @Test
    void oneRelatedGoodsMergesTheLibraryImage() {
        ImageSearchModels.ImageSearchHitView product = goods("9", "main", 0.50f, "H100", "蕾丝", "https://img/main");
        ImageSearchModels.ImageSearchHitView library = library("lib-1", 0.72f, "");
        ImageSearchModels.GoodsBrief brief = new ImageSearchModels.GoodsBrief();
        brief.setId(9);
        brief.setGoodsNo("H100");
        brief.setProductName("蕾丝");
        library.setRelatedGoods(List.of(brief));
        List<ImageSearchModels.ImageSearchHitView> cards = SameProductGrouping.group(
                List.of(product, library), 5, 0d, 0d, 0d);
        assertEquals(1, cards.size());
        assertEquals("https://img/lib-1", cards.get(0).getImageUrl());
        assertEquals("H100", cards.get(0).getGoods().getGoodsNo());
        assertEquals(1, cards.get(0).getImages().size());
    }

    @Test
    void severalRelatedGoodsAreNotTrusted() {
        ImageSearchModels.ImageSearchHitView library = library("lib-1", 0.8f, "P");
        ImageSearchModels.GoodsBrief first = new ImageSearchModels.GoodsBrief();
        first.setId(1);
        first.setGoodsNo("A");
        ImageSearchModels.GoodsBrief second = new ImageSearchModels.GoodsBrief();
        second.setId(2);
        second.setGoodsNo("B");
        library.setRelatedGoods(List.of(first, second));
        List<ImageSearchModels.ImageSearchHitView> cards = SameProductGrouping.group(List.of(library), 5, 0d, 0d, 0d);
        assertEquals(1, cards.size());
        assertEquals("library", cards.get(0).getSource());
        assertTrue(cards.get(0).getGoods() == null);
    }

    @Test
    void sameLibraryProductIdGroupsTogether() {
        List<ImageSearchModels.ImageSearchHitView> cards = SameProductGrouping.group(List.of(
                library("a", 0.4f, "款A"),
                library("b", 0.7f, "款A")
        ), 5, 0d, 0.02d, 0.06d);
        assertEquals(1, cards.size());
        assertEquals("b", cards.get(0).getSourceId());
        assertEquals(1, cards.get(0).getImages().size());
    }

    @Test
    void goodsNumbersThatDifferOnlyByCaseOrSpaceMerge() {
        List<ImageSearchModels.ImageSearchHitView> cards = SameProductGrouping.group(List.of(
                goods("9", "main", 0.70f, "H 100", "蕾丝", "https://a"),
                goods("12", "side", 0.60f, "ｈ100", "蕾丝", "https://b")
        ), 5, 0d, 0d, 0d);
        assertEquals(1, cards.size());
        assertEquals("H 100", cards.get(0).getGoods().getGoodsNo());
        assertEquals(1, cards.get(0).getImages().size());
    }

    @Test
    void hyphenatedGoodsNumbersStaySeparate() {
        List<ImageSearchModels.ImageSearchHitView> cards = SameProductGrouping.group(List.of(
                goods("1", "main", 0.80f, "AB-1", "甲", "https://a"),
                goods("2", "main", 0.70f, "AB1", "乙", "https://b")
        ), 5, 0d, 0d, 0d);
        assertEquals(2, cards.size());
    }

    @Test
    void duplicateRowsOfTheSameGoodsNumberMerge() {
        ImageSearchModels.ImageSearchHitView library = library("lib-1", 0.66f, "");
        ImageSearchModels.GoodsBrief later = new ImageSearchModels.GoodsBrief();
        later.setId(12);
        later.setGoodsNo("H100");
        ImageSearchModels.GoodsBrief earlier = new ImageSearchModels.GoodsBrief();
        earlier.setId(4);
        earlier.setGoodsNo("h 100");
        library.setRelatedGoods(List.of(later, earlier));
        ImageSearchModels.ImageSearchHitView product = goods("4", "main", 0.50f, "H100", "蕾丝", "https://img/main");
        List<ImageSearchModels.ImageSearchHitView> cards = SameProductGrouping.group(
                List.of(library, product), 5, 0d, 0d, 0d);
        assertEquals(1, cards.size());
        assertEquals("H100", cards.get(0).getGoods().getGoodsNo());
    }

    @Test
    void differentCompaniesDoNotMerge() {
        ImageSearchModels.ImageSearchHitView left = goods("1", "main", 0.9f, "H1", "甲", "https://a");
        left.setCompany("宝娜斯集团");
        ImageSearchModels.ImageSearchHitView right = goods("2", "main", 0.8f, "H1", "甲", "https://b");
        right.setCompany("其他公司");
        List<ImageSearchModels.ImageSearchHitView> cards = SameProductGrouping.group(List.of(left, right), 5, 0d, 0d, 0d);
        assertEquals(2, cards.size());
    }

    @Test
    void productRecallCountsAnyImageOfTheSameGoods() {
        ImageVectorRecord query = ImageVectorRecord.goods(9, "main", "宝娜斯集团", "q", "H100", "蕾丝", "宝娜斯集团");
        ImageVectorRecord sibling = ImageVectorRecord.goods(9, "side", "宝娜斯集团", "s", "H100", "蕾丝", "宝娜斯集团");
        ImageVectorRecord other = ImageVectorRecord.goods(3, "main", "宝娜斯集团", "o", "H3", "别的", "宝娜斯集团");
        List<ImageSearchModels.RawHit> hits = List.of(
                new ImageSearchModels.RawHit(query, 0.99f),
                new ImageSearchModels.RawHit(other, 0.90f),
                new ImageSearchModels.RawHit(sibling, 0.60f)
        );
        List<String> ranked = SameProductGrouping.rankedProductKeys(hits, query.vectorId(), 1, 0d, 0d);
        assertEquals(List.of(SameProductGrouping.productKey(other)), ranked);
        List<String> wider = SameProductGrouping.rankedProductKeys(hits, query.vectorId(), 5, 0.02d, 0.06d);
        assertTrue(wider.contains(SameProductGrouping.productKey(sibling)));
        double recall = ImageSearchRecall.hitRate(
                List.of(List.of(SameProductGrouping.productKey(query))),
                List.of(wider),
                5);
        assertEquals(1d, recall);
    }

    @Test
    void internalTopKIsSeveralTimesTheRequestedCount() {
        ImageSearchProperties properties = new ImageSearchProperties();
        properties.setInternalTopKFactor(5);
        assertEquals(25, new SameProductStrategy().internalTopK(5, properties));
        assertEquals(250, new SameProductStrategy().internalTopK(50, properties));
    }

    private static ImageSearchModels.ImageSearchHitView goods(
            String id, String slot, float score, String goodsNo, String name, String url) {
        ImageSearchModels.ImageSearchHitView view = new ImageSearchModels.ImageSearchHitView();
        view.setScore(score);
        view.setScorePercent(ImageSearchModels.scorePercent(score));
        view.setSource(ImageSearchFilters.SOURCE_GOODS);
        view.setSourceId(id);
        view.setSlot(slot);
        view.setImageUrl(url);
        ImageSearchModels.GoodsBrief brief = new ImageSearchModels.GoodsBrief();
        brief.setId(Long.parseLong(id));
        brief.setGoodsNo(goodsNo);
        brief.setProductName(name);
        brief.setSampler("张三");
        view.setGoods(brief);
        return view;
    }

    private static ImageSearchModels.ImageSearchHitView library(String id, float score, String productId) {
        ImageSearchModels.ImageSearchHitView view = new ImageSearchModels.ImageSearchHitView();
        view.setScore(score);
        view.setSource(ImageSearchFilters.SOURCE_LIBRARY);
        view.setSourceId(id);
        view.setProductId(productId);
        view.setImageUrl("https://img/" + id);
        view.setTitle(id);
        return view;
    }

    @Test
    void lowScoresDropOutBeforeGrouping() {
        List<ImageSearchModels.ImageSearchHitView> hits = new ArrayList<>();
        hits.add(goods("1", "main", 0.2f, "H1", "弱", "https://w"));
        assertTrue(SameProductGrouping.group(hits, 5, 0.30d, 0d, 0d).isEmpty());
    }
}
