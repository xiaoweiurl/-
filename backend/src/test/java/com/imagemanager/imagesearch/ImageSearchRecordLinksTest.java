package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageSearchRecordLinksTest {

    @Test
    void sameCompanyGoodsOpensSampleOrderAndProductDetail() {
        ImageSearchModels.ImageSearchHitView hit = goods(9L, "H100");
        ImageSearchRecordLinks.apply(List.of(hit), "宝娜斯集团",
                new ImageSearchRecordLinks.Targets(Map.of(9L, "宝娜斯集团"), Map.of()));

        assertEquals("/sampler/9", hit.getSampleOrderPath());
        assertEquals("/goods-library/9", hit.getProductDetailPath());
    }

    @Test
    void otherCompanyGoodsHasNoLink() {
        ImageSearchModels.ImageSearchHitView hit = goods(9L, "H100");
        ImageSearchRecordLinks.apply(List.of(hit), "宝娜斯集团",
                new ImageSearchRecordLinks.Targets(Map.of(9L, "盈云"), Map.of()));

        assertNull(hit.getSampleOrderPath());
        assertNull(hit.getProductDetailPath());
    }

    @Test
    void untaggedGoodsOnlyOpenForTheDefaultCompany() {
        ImageSearchModels.ImageSearchHitView ours = goods(9L, "H100");
        ImageSearchModels.ImageSearchHitView theirs = goods(8L, "H80");
        ImageSearchRecordLinks.Targets targets = new ImageSearchRecordLinks.Targets(Map.of(9L, "", 8L, ""), Map.of());

        ImageSearchRecordLinks.apply(List.of(ours), "宝娜斯集团", targets);
        ImageSearchRecordLinks.apply(List.of(theirs), "盈云", targets);

        assertEquals("/sampler/9", ours.getSampleOrderPath());
        assertNull(theirs.getSampleOrderPath());
        assertNull(theirs.getProductDetailPath());
    }

    @Test
    void missingGoodsOrProductDoesNotInventALink() {
        ImageSearchModels.ImageSearchHitView missingGoods = goods(9L, "H100");
        missingGoods.setProductId("not-a-row");
        ImageSearchModels.ImageSearchHitView library = library("lib-1", "p-1");
        ImageSearchRecordLinks.apply(List.of(missingGoods, library), "宝娜斯集团",
                new ImageSearchRecordLinks.Targets(Map.of(), Map.of("p-1", "宝娜斯集团")));

        assertNull(missingGoods.getSampleOrderPath());
        assertNull(missingGoods.getProductDetailPath());
        assertNull(library.getSampleOrderPath());
        assertEquals("/products/p-1", library.getProductDetailPath());
    }

    @Test
    void productInAnotherCompanyIsNotLinked() {
        ImageSearchModels.ImageSearchHitView hit = library("lib-1", "p-1");
        ImageSearchRecordLinks.apply(List.of(hit), "宝娜斯集团",
                new ImageSearchRecordLinks.Targets(Map.of(), Map.of("p-1", "盈云")));

        assertNull(hit.getProductDetailPath());
    }

    @Test
    void goodsFolderIsTheProductDetailWhenBothRecordsExist() {
        ImageSearchModels.ImageSearchHitView hit = goods(9L, "H100");
        hit.setProductId("p-1");
        ImageSearchRecordLinks.apply(List.of(hit), "宝娜斯集团",
                new ImageSearchRecordLinks.Targets(Map.of(9L, "宝娜斯集团"), Map.of("p-1", "宝娜斯集团")));

        assertEquals("/sampler/9", hit.getSampleOrderPath());
        assertEquals("/goods-library/9", hit.getProductDetailPath());
    }

    @Test
    void ownedGoodsWithoutACompanyIsNotOpened() {
        assertNull(ImageSearchRecordLinks.ownerCompany("user-1", " "));
        assertEquals("", ImageSearchRecordLinks.ownerCompany("", null));
        assertEquals("盈云", ImageSearchRecordLinks.ownerCompany("user-1", "盈云"));
    }

    @Test
    void unsafeProductIdIsNotALink() {
        ImageSearchModels.ImageSearchHitView hit = library("lib-1", "../admin");
        ImageSearchRecordLinks.apply(List.of(hit), "宝娜斯集团",
                new ImageSearchRecordLinks.Targets(Map.of(), Map.of("../admin", "宝娜斯集团")));

        assertNull(hit.getProductDetailPath());
        assertNull(ImageSearchRecordLinks.safeProductId("javascript:alert(1)"));
        assertNull(ImageSearchRecordLinks.safeProductId(""));
    }

    @Test
    void conflictingRelatedGoodsAreNotOpened() {
        ImageSearchModels.ImageSearchHitView hit = library("lib-1", "");
        hit.setRelatedGoods(List.of(brief(4L, "H4"), brief(5L, "H5")));
        ImageSearchRecordLinks.apply(List.of(hit), "宝娜斯集团",
                new ImageSearchRecordLinks.Targets(Map.of(4L, "宝娜斯集团", 5L, "宝娜斯集团"), Map.of()));

        assertNull(hit.getSampleOrderPath());
        assertNull(hit.getProductDetailPath());
    }

    @Test
    void sameGoodsNumberOnTwoRowsOpensTheSmallerId() {
        ImageSearchModels.ImageSearchHitView hit = library("lib-1", "");
        hit.setRelatedGoods(List.of(brief(12L, "H100"), brief(4L, "h 100")));
        ImageSearchRecordLinks.apply(List.of(hit), "宝娜斯集团",
                new ImageSearchRecordLinks.Targets(Map.of(12L, "宝娜斯集团", 4L, "宝娜斯集团"), Map.of()));

        assertEquals("/sampler/4", hit.getSampleOrderPath());
        assertEquals("/goods-library/4", hit.getProductDetailPath());
    }

    @Test
    void samplerSessionKeepsOnlyItsOwnGoods() {
        ImageSearchModels.ImageSearchHitView own = goods(9L, "H9");
        own.setSampleOrderPath("/sampler/9");
        own.setProductDetailPath("/goods-library/9");
        ImageSearchModels.ImageSearchHitView other = goods(8L, "H8");
        other.setSampleOrderPath("/sampler/8");
        other.setProductDetailPath("/products/p-8");

        ImageSearchRecordLinks.retainSampler(List.of(own, other), "9");

        assertEquals("/sampler/9", own.getSampleOrderPath());
        assertEquals("/goods-library/9", own.getProductDetailPath());
        assertNull(other.getSampleOrderPath());
        assertNull(other.getProductDetailPath());
    }

    @Test
    void samplerWithoutAGoodsIdLosesEveryLink() {
        ImageSearchModels.ImageSearchHitView hit = goods(9L, "H9");
        hit.setSampleOrderPath("/sampler/9");
        hit.setProductDetailPath("/goods-library/9");

        ImageSearchRecordLinks.retainSampler(List.of(hit), " ");

        assertNull(hit.getSampleOrderPath());
        assertNull(hit.getProductDetailPath());
    }

    @Test
    void chatSourcesCarryOnlyRealPaths() {
        ImageSearchModels.ImageSearchHitView linked = goods(9L, "H100");
        linked.setSampleOrderPath("/sampler/9");
        linked.setProductDetailPath("/goods-library/9");
        ImageSearchModels.ImageSearchHitView plain = library("lib-1", "");
        plain.setSampleOrderPath("  ");

        List<Map<String, Object>> sources = ChatVisualSearchRank.toSources(List.of(linked, plain));

        assertEquals("/sampler/9", sources.get(0).get("sampleOrderPath"));
        assertEquals("/goods-library/9", sources.get(0).get("productDetailPath"));
        assertFalse(sources.get(1).containsKey("sampleOrderPath"));
        assertFalse(sources.get(1).containsKey("productDetailPath"));
    }

    private static ImageSearchModels.ImageSearchHitView goods(long id, String goodsNo) {
        ImageSearchModels.ImageSearchHitView view = new ImageSearchModels.ImageSearchHitView();
        view.setSource(ImageSearchFilters.SOURCE_GOODS);
        view.setSourceId(Long.toString(id));
        view.setGoods(brief(id, goodsNo));
        return view;
    }

    private static ImageSearchModels.ImageSearchHitView library(String id, String productId) {
        ImageSearchModels.ImageSearchHitView view = new ImageSearchModels.ImageSearchHitView();
        view.setSource(ImageSearchFilters.SOURCE_LIBRARY);
        view.setSourceId(id);
        view.setProductId(productId);
        return view;
    }

    private static ImageSearchModels.GoodsBrief brief(long id, String goodsNo) {
        ImageSearchModels.GoodsBrief brief = new ImageSearchModels.GoodsBrief();
        brief.setId(id);
        brief.setGoodsNo(goodsNo);
        brief.setProductName("品名");
        brief.setSampler("张三");
        return brief;
    }
}
