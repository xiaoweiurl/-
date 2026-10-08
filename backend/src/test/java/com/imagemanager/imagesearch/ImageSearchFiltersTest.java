package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSearchFiltersTest {

    @Test
    void collectionNameStaysAwayFromSalesperson() {
        ImageSearchFilters.assertCollectionName("image_vectors");
        assertThrows(IllegalArgumentException.class, () -> ImageSearchFilters.assertCollectionName("salesperson_docs"));
        assertThrows(IllegalArgumentException.class, () -> ImageSearchFilters.assertCollectionName("salesperson_docs_hybrid"));
        assertThrows(IllegalArgumentException.class, () -> ImageSearchFilters.assertCollectionName("salesperson_chunks"));
        assertThrows(IllegalArgumentException.class, () -> ImageSearchFilters.assertCollectionName("image_salesperson"));
        assertThrows(IllegalArgumentException.class, () -> ImageSearchFilters.assertCollectionName("Image_Vectors"));
    }

    @Test
    void filterKeepsGoodsVisibleAndOtherCompaniesOut() {
        String filter = ImageSearchFilters.toMilvus("all", "宝娜斯\"集团");
        assertTrue(filter.contains("source == \"goods\""));
        assertTrue(filter.contains("宝娜斯\\\"集团"));

        ImageVectorRecord ours = ImageVectorRecord.library("a", "宝娜斯集团", "k", "甲", "P1", "宝娜斯集团");
        ImageVectorRecord theirs = ImageVectorRecord.library("b", "其他公司", "k", "乙", "", "宝娜斯集团");
        ImageVectorRecord goods = ImageVectorRecord.goods(9L, "main", "其他公司", "g", "H1", "款", "宝娜斯集团");

        assertTrue(ImageSearchFilters.matches(ours, "all", "宝娜斯集团"));
        assertFalse(ImageSearchFilters.matches(theirs, "all", "宝娜斯集团"));
        assertTrue(ImageSearchFilters.matches(goods, "all", "宝娜斯集团"));
        assertFalse(ImageSearchFilters.matches(goods, "library", "宝娜斯集团"));
        assertTrue(ImageSearchFilters.matches(goods, "goods", "宝娜斯集团"));
        assertFalse(ImageSearchFilters.matches(ours, "goods", "宝娜斯集团"));
    }

    @Test
    void utf8CutRespectsMilvusByteLimit() {
        String chinese = "蕾".repeat(200);
        String cut = ImageSearchFilters.cutUtf8(chinese, 512);
        assertEquals(170, cut.length());
        assertEquals(510, cut.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertEquals("abc", ImageSearchFilters.cutUtf8("abc", 512));
        assertEquals("", ImageSearchFilters.cutUtf8(null, 8));
    }

    @Test
    void vectorIdsAreStable() {
        assertEquals("lib:img-1", ImageSearchFilters.libraryVectorId("img-1"));
        assertEquals("goods:12:main", ImageSearchFilters.goodsVectorId(12L, "MAIN"));
        assertThrows(IllegalArgumentException.class, () -> ImageSearchFilters.goodsVectorId(12L, "cover"));
    }
}
