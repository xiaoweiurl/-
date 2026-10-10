package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelatedGoodsLinksTest {

    @Test
    void goodsNumberWinsOverACollidingNumericId() {
        ImageSearchModels.GoodsBrief wrong = brief(10086, "AB-1");
        ImageSearchModels.GoodsBrief real = brief(55, "10086");
        List<ImageSearchModels.GoodsBrief> chosen = RelatedGoodsLinks.choose("10086", List.of(wrong, real));
        assertEquals(List.of(55L), chosen.stream().map(ImageSearchModels.GoodsBrief::getId).toList());
    }

    @Test
    void numericIdIsUsedOnlyWhenNoGoodsNumberMatches() {
        ImageSearchModels.GoodsBrief row = brief(9, "H9");
        assertEquals(9L, RelatedGoodsLinks.choose("9", List.of(row)).get(0).getId());
        assertTrue(RelatedGoodsLinks.choose("H1", List.of(row, brief(2, "H2"))).isEmpty());
    }

    @Test
    void caseAndSpaceStillMatchTheGoodsNumber() {
        List<ImageSearchModels.GoodsBrief> chosen = RelatedGoodsLinks.choose("h100", List.of(brief(3, "H 100")));
        assertEquals(3L, chosen.get(0).getId());
    }

    @Test
    void sameGoodsNumberKeepsTheSmallerIdFirst() {
        List<ImageSearchModels.GoodsBrief> chosen = RelatedGoodsLinks.choose(
                "H100", List.of(brief(12, "H100"), brief(4, "ｈ100")));
        assertEquals(List.of(4L, 12L), chosen.stream().map(ImageSearchModels.GoodsBrief::getId).toList());
    }

    private static ImageSearchModels.GoodsBrief brief(long id, String goodsNo) {
        ImageSearchModels.GoodsBrief brief = new ImageSearchModels.GoodsBrief();
        brief.setId(id);
        brief.setGoodsNo(goodsNo);
        return brief;
    }
}
