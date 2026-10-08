package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSearchConditionRankTest {

    @Test
    void hardFiltersKeepTimeSamplerAlbumAndScope() {
        long inYear = ZonedDateTime.of(2025, 6, 1, 0, 0, 0, 0, ImageSearchConditionParser.ZONE)
                .toInstant().toEpochMilli();
        long old = ZonedDateTime.of(2020, 1, 1, 0, 0, 0, 0, ImageSearchConditionParser.ZONE)
                .toInstant().toEpochMilli();
        ImageSearchModels.ImageSearchHitView zhang = goods("1", "张三", inYear, 0.9f);
        ImageSearchModels.ImageSearchHitView li = goods("2", "李四", inYear, 0.8f);
        ImageSearchModels.ImageSearchHitView oldZhang = goods("3", "张三", old, 0.95f);
        ImageSearchModels.ImageSearchHitView album = library("lib", "滑雪服", inYear, 0.7f);

        ImageSearchCondition.Parsed parsed = parsed(ImageSearchFilters.SOURCE_GOODS, year2025(), "张三", null, List.of());
        ImageSearchConditionRank.Outcome outcome = ImageSearchConditionRank.apply(
                List.of(album, oldZhang, li, zhang), parsed, Map.of(), null, 0.65, 0.35, 0);
        assertEquals(List.of("1"), outcome.hits().stream().map(ImageSearchModels.ImageSearchHitView::getSourceId).toList());
        assertFalse(outcome.relaxed());
    }

    @Test
    void emptyFilterIsLiftedAndNamed() {
        long inYear = ZonedDateTime.of(2025, 6, 1, 0, 0, 0, 0, ImageSearchConditionParser.ZONE)
                .toInstant().toEpochMilli();
        ImageSearchModels.ImageSearchHitView zhang = goods("1", "张三", inYear, 0.9f);
        ImageSearchModels.ImageSearchHitView li = goods("2", "李四", inYear, 0.4f);
        ImageSearchCondition.Window future = new ImageSearchCondition.Window(
                ZonedDateTime.of(2030, 1, 1, 0, 0, 0, 0, ImageSearchConditionParser.ZONE).toInstant().toEpochMilli(),
                ZonedDateTime.of(2031, 1, 1, 0, 0, 0, 0, ImageSearchConditionParser.ZONE).toInstant().toEpochMilli(),
                "2030年");
        ImageSearchCondition.Parsed parsed = parsed(null, future, "张三", "滑雪服", List.of());
        ImageSearchConditionRank.Outcome outcome = ImageSearchConditionRank.apply(
                List.of(zhang, li), parsed, Map.of(), null, 0.65, 0.35, 0);

        assertEquals(List.of("1"), outcome.hits().stream().map(ImageSearchModels.ImageSearchHitView::getSourceId).toList());
        assertTrue(outcome.relaxed());
        assertTrue(outcome.notice().contains("2030年"));
        assertTrue(outcome.notice().contains("滑雪服相册"));
        assertTrue(outcome.filters().stream().anyMatch(chip -> "time".equals(chip.id()) && chip.relaxed()));
        assertTrue(outcome.filters().stream().anyMatch(chip -> "sampler".equals(chip.id()) && chip.applied()));
        assertTrue(ImageSearchConditionRank.summary(outcome.filters(), outcome.notice()).contains("打样员 张三"));
    }

    @Test
    void textScoreReranksAndFloorDropsOnlyBelowTheLine() {
        ImageSearchModels.ImageSearchHitView red = library("red", "秋冬", 0L, 0.40f);
        red.setVectorId("lib:red");
        ImageSearchModels.ImageSearchHitView other = library("other", "秋冬", 0L, 0.90f);
        other.setVectorId("lib:other");
        ImageSearchModels.ImageSearchHitView missing = library("miss", "秋冬", 0L, 0.50f);
        missing.setVectorId("lib:miss");
        Map<String, float[]> vectors = Map.of(
                "lib:red", new float[]{0f, 1f},
                "lib:other", new float[]{1f, 0f});
        float[] text = new float[]{0f, 1f};
        ImageSearchCondition.Parsed parsed = parsed(null, null, null, null, List.of("红色"));

        ImageSearchConditionRank.Outcome ranked = ImageSearchConditionRank.apply(
                List.of(other, red, missing), parsed, vectors, text, 0.5, 0.5, 0);
        assertEquals("red", ranked.hits().get(0).getSourceId());
        assertEquals(0.70f, ranked.hits().get(0).getScore(), 0.001f);
        assertEquals(0.40f, ranked.hits().get(0).getImageScore(), 0.001f);
        assertTrue(ranked.hits().stream().anyMatch(hit -> "miss".equals(hit.getSourceId())));
        assertTrue(ranked.hits().stream().anyMatch(hit -> "other".equals(hit.getSourceId())));

        ImageSearchConditionRank.Outcome floored = ImageSearchConditionRank.apply(
                List.of(copy(other), copy(red)), parsed, vectors, text, 0.5, 0.5, 0.5);
        assertEquals(List.of("red"), floored.hits().stream().map(ImageSearchModels.ImageSearchHitView::getSourceId).toList());
    }

    @Test
    void fusedScoreDoesNotFailTheImageThreshold() {
        ImageSearchModels.ImageSearchHitView hit = library("red", "秋冬", 0L, 0.80f);
        hit.setImageScore(0.80f);
        hit.setScore(0.20f);
        List<ImageSearchModels.ImageSearchHitView> kept = ChatVisualSearchRank.select(List.of(hit), 0.30d, 5);
        assertEquals(1, kept.size());
    }

    private static ImageSearchCondition.Parsed parsed(String scope, ImageSearchCondition.Window time,
                                                      String sampler, String album, List<String> attributes) {
        List<ImageSearchCondition.Chip> chips = new java.util.ArrayList<>();
        if (scope != null) {
            String label = ImageSearchFilters.SOURCE_LIBRARY.equals(scope) ? "只看素材" : "只看打样";
            chips.add(new ImageSearchCondition.Chip("scope", "scope", label, scope, true, false));
        }
        if (time != null) {
            chips.add(new ImageSearchCondition.Chip("time", "time", time.label(), time.label(), true, false));
        }
        if (sampler != null) {
            chips.add(new ImageSearchCondition.Chip("sampler", "sampler", "打样员 " + sampler, sampler, true, false));
        }
        if (album != null) {
            chips.add(new ImageSearchCondition.Chip("album", "album", album + "相册", album, true, false));
        }
        List<String> terms = attributes == null ? List.of() : attributes;
        for (String term : terms) {
            chips.add(new ImageSearchCondition.Chip("attr:" + term, "attribute", term, term, true, false));
        }
        return new ImageSearchCondition.Parsed(scope, time, sampler, album, terms, chips);
    }

    private static ImageSearchCondition.Window year2025() {
        return new ImageSearchCondition.Window(
                ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ImageSearchConditionParser.ZONE).toInstant().toEpochMilli(),
                ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, ImageSearchConditionParser.ZONE).toInstant().toEpochMilli(),
                "2025年");
    }

    private static ImageSearchModels.ImageSearchHitView goods(String id, String sampler, long createdAt, float score) {
        ImageSearchModels.ImageSearchHitView view = ImageSearchModels.fromRecord(
                ImageVectorRecord.goods(Long.parseLong(id), "main", "宝娜斯集团", "k", "H" + id, "款", "宝娜斯集团"),
                score, "https://example.test/" + id);
        ImageSearchModels.GoodsBrief brief = new ImageSearchModels.GoodsBrief();
        brief.setId(Long.parseLong(id));
        brief.setSampler(sampler);
        brief.setGoodsNo("H" + id);
        view.setGoods(brief);
        view.setCreatedAt(createdAt);
        return view;
    }

    private static ImageSearchModels.ImageSearchHitView library(String id, String album, long createdAt, float score) {
        ImageSearchModels.ImageSearchHitView view = ImageSearchModels.fromRecord(
                ImageVectorRecord.library(id, "宝娜斯集团", "k", id, "", "宝娜斯集团"),
                score, "https://example.test/" + id);
        view.setAlbumName(album);
        view.setCreatedAt(createdAt);
        return view;
    }

    private static ImageSearchModels.ImageSearchHitView copy(ImageSearchModels.ImageSearchHitView hit) {
        ImageSearchModels.ImageSearchHitView view = library(hit.getSourceId(), hit.getAlbumName(), hit.getCreatedAt(), hit.getScore());
        view.setVectorId(hit.getVectorId());
        return view;
    }
}
