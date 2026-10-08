package com.imagemanager.imagesearch;

import com.imagemanager.service.ChatCitation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatVisualSearchRankTest {

    @Test
    void keepsTopHitsAboveTheThresholdAndDropsDuplicates() {
        ImageSearchModels.ImageSearchHitView strong = goods("9", "main", 0.91f, "H100", "蕾丝中筒", "张三");
        ImageSearchModels.ImageSearchHitView weakerSame = goods("9", "main", 0.40f, "H100", "蕾丝中筒", "张三");
        List<ImageSearchModels.ImageSearchHitView> hits = new ArrayList<>();
        hits.add(weakerSame);
        hits.add(strong);
        hits.add(goods("8", "side", 0.80f, "H80", "棉袜", "李四"));
        hits.add(goods("7", "main", 0.70f, "H70", "船袜", "王五"));
        hits.add(goods("6", "main", 0.60f, "H60", "连裤袜", "赵六"));
        hits.add(goods("5", "main", 0.50f, "H50", "短袜", "钱七"));
        hits.add(library("lib-1", 0.45f, "红色蕾丝", "秋冬"));
        hits.add(goods("4", "main", 0.20f, "H20", "弱匹配", "周八"));

        List<ImageSearchModels.ImageSearchHitView> kept = ChatVisualSearchRank.select(hits, 0.30d, 5);

        assertEquals(5, kept.size());
        assertEquals("9", kept.get(0).getSourceId());
        assertEquals(0.91f, kept.get(0).getScore());
        assertTrue(kept.stream().noneMatch(hit -> "4".equals(hit.getSourceId())));
        assertTrue(kept.stream().noneMatch(hit -> hit.getScore() < 0.30f));
    }

    @Test
    void exactThresholdIsKept() {
        List<ImageSearchModels.ImageSearchHitView> kept = ChatVisualSearchRank.select(
                List.of(library("a", 0.30f, "刚好", "相册"), library("b", 0.299f, "差一点", "相册")),
                0.30d, 5);
        assertEquals(1, kept.size());
        assertEquals("a", kept.get(0).getSourceId());
    }

    @Test
    void allModeCanReturnMoreThanTheDefaultFive() {
        List<ImageSearchModels.ImageSearchHitView> hits = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            hits.add(library("lib-" + i, 0.9f - i * 0.05f, "素材" + i, "相册"));
        }
        assertEquals(5, ChatVisualSearchRank.select(hits, 0.30d, 5).size());
        assertEquals(8, ChatVisualSearchRank.select(hits, 0.30d, 50).size());
    }

    @Test
    void emptyResultTellsTheModelNotToInventMatches() {
        String context = ChatVisualSearchRank.context(List.of());
        assertTrue(context.contains("未找到达到相似度阈值的图片"));
        assertTrue(context.contains("不要猜测货号"));
        List<Map<String, Object>> sources = ChatVisualSearchRank.toSources(List.of());
        assertEquals(1, sources.size());
        assertEquals(Boolean.TRUE, sources.get(0).get("empty"));
        assertEquals("visual_match", sources.get(0).get("source"));
    }

    @Test
    void sourcesCarryGoodsAndDoNotBecomeCitations() {
        List<Map<String, Object>> visual = ChatVisualSearchRank.toSources(List.of(
                goods("9", "main", 0.86f, "H100", "蕾丝中筒", "张三"),
                library("lib-1", 0.71f, "红色蕾丝", "秋冬")));
        assertEquals("产品", visual.get(0).get("sourceLabel"));
        assertEquals("H100", visual.get(0).get("goodsNo"));
        assertEquals("张三", visual.get(0).get("sampler"));
        assertEquals(86, visual.get(0).get("scorePercent"));
        assertEquals("素材", visual.get(1).get("sourceLabel"));
        assertTrue(ChatVisualSearchRank.context(List.of(
                goods("9", "main", 0.86f, "H100", "蕾丝中筒", "张三"))).contains("货号 H100"));

        List<Map<String, Object>> mixed = new ArrayList<>(visual);
        Map<String, Object> citation = new java.util.LinkedHashMap<>();
        citation.put("id", "E1");
        citation.put("source", "supply_chain");
        citation.put("title", "供应链");
        mixed.add(citation);
        String clause = ChatCitation.allowedCiteClause(mixed);
        assertTrue(clause.contains("E1"));
        assertFalse(clause.contains("visual-1"));
    }

    private static ImageSearchModels.ImageSearchHitView goods(
            String id, String slot, float score, String goodsNo, String name, String sampler) {
        ImageSearchModels.ImageSearchHitView view = new ImageSearchModels.ImageSearchHitView();
        view.setScore(score);
        view.setScorePercent(ImageSearchModels.scorePercent(score));
        view.setSource(ImageSearchFilters.SOURCE_GOODS);
        view.setSourceId(id);
        view.setSlot(slot);
        view.setSlotLabel(ImageSearchFilters.slotLabel(slot));
        view.setImageUrl("https://example.test/goods/" + id);
        ImageSearchModels.GoodsBrief brief = new ImageSearchModels.GoodsBrief();
        brief.setGoodsNo(goodsNo);
        brief.setProductName(name);
        brief.setSampler(sampler);
        view.setGoods(brief);
        return view;
    }

    private static ImageSearchModels.ImageSearchHitView library(String id, float score, String title, String album) {
        ImageSearchModels.ImageSearchHitView view = new ImageSearchModels.ImageSearchHitView();
        view.setScore(score);
        view.setScorePercent(ImageSearchModels.scorePercent(score));
        view.setSource(ImageSearchFilters.SOURCE_LIBRARY);
        view.setSourceId(id);
        view.setTitle(title);
        view.setAlbumName(album);
        view.setImageUrl("https://example.test/lib/" + id);
        return view;
    }
}
