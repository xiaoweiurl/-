package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatVisualSearchServiceTest {

    @Test
    void disabledFallsBackWithoutCallingEmbedder() {
        ImageSearchProperties properties = new ImageSearchProperties();
        AtomicInteger calls = new AtomicInteger();
        ChatVisualSearchService service = service(properties, calls, List.of(), false);
        ChatVisualSearchService.Outcome outcome = service.search("找相似款", List.of(image()), "宝娜斯集团", true);
        assertFalse(outcome.ran());
        assertEquals(0, calls.get());
        assertTrue(outcome.sources().isEmpty());
    }

    @Test
    void samplerSessionDoesNotSearch() {
        ImageSearchProperties properties = enabled();
        AtomicInteger calls = new AtomicInteger();
        ChatVisualSearchService service = service(properties, calls, List.of(library("a", 0.9f)), false);
        ChatVisualSearchService.Outcome outcome = service.search("找相似款", List.of(image()), "宝娜斯集团", false);
        assertFalse(outcome.ran());
        assertEquals(0, calls.get());
    }

    @Test
    void nonVisualQuestionDoesNotSearch() {
        ImageSearchProperties properties = enabled();
        AtomicInteger calls = new AtomicInteger();
        ChatVisualSearchService service = service(properties, calls, List.of(library("a", 0.9f)), false);
        ChatVisualSearchService.Outcome outcome = service.search("分析一下这块面料", List.of(image()), "宝娜斯集团", true);
        assertFalse(outcome.ran());
        assertEquals(0, calls.get());
    }

    @Test
    void embedFailureFallsBack() {
        ImageSearchProperties properties = enabled();
        AtomicInteger calls = new AtomicInteger();
        ImageSearchQueryService query = new ImageSearchQueryService(properties, new ImageEmbedder() {
            @Override
            public float[] embedImage(byte[] data, String filename) {
                calls.incrementAndGet();
                throw new RuntimeException("connection refused");
            }

            @Override
            public float[] embedText(String text) {
                calls.incrementAndGet();
                throw new RuntimeException("connection refused");
            }
        }, stubIndex(List.of()), (hits, company) -> List.of());
        ChatVisualSearchService service = new ChatVisualSearchService(properties, query);
        ChatVisualSearchService.Outcome outcome = service.search("找相似款", List.of(image()), "宝娜斯集团", true);
        assertFalse(outcome.ran());
        assertEquals(1, calls.get());
        assertTrue(outcome.context().isEmpty());
    }

    @Test
    void returnsOnlyTopHitsAboveThreshold() {
        ImageSearchProperties properties = enabled();
        properties.setChatTopK(5);
        properties.setChatMinScore(0.30d);
        AtomicInteger calls = new AtomicInteger();
        List<ImageSearchModels.RawHit> raw = new ArrayList<>();
        raw.add(new ImageSearchModels.RawHit(ImageVectorRecord.goods(1, "main", "宝娜斯集团", "g", "H1", "甲", "宝娜斯集团"), 0.95f));
        raw.add(new ImageSearchModels.RawHit(ImageVectorRecord.goods(2, "main", "宝娜斯集团", "g", "H2", "乙", "宝娜斯集团"), 0.90f));
        raw.add(new ImageSearchModels.RawHit(ImageVectorRecord.goods(3, "main", "宝娜斯集团", "g", "H3", "丙", "宝娜斯集团"), 0.80f));
        raw.add(new ImageSearchModels.RawHit(ImageVectorRecord.goods(4, "main", "宝娜斯集团", "g", "H4", "丁", "宝娜斯集团"), 0.70f));
        raw.add(new ImageSearchModels.RawHit(ImageVectorRecord.goods(5, "main", "宝娜斯集团", "g", "H5", "戊", "宝娜斯集团"), 0.60f));
        raw.add(new ImageSearchModels.RawHit(ImageVectorRecord.goods(6, "main", "宝娜斯集团", "g", "H6", "己", "宝娜斯集团"), 0.55f));
        raw.add(new ImageSearchModels.RawHit(ImageVectorRecord.library("lib", "宝娜斯集团", "k", "红色蕾丝", "", "宝娜斯集团"), 0.50f));
        raw.add(new ImageSearchModels.RawHit(ImageVectorRecord.goods(7, "main", "宝娜斯集团", "g", "H7", "弱", "宝娜斯集团"), 0.10f));
        ChatVisualSearchService service = service(properties, calls, raw, true);
        ChatVisualSearchService.Outcome outcome = service.search("这个是什么货号", List.of(image()), "宝娜斯集团", true);
        assertTrue(outcome.ran());
        assertEquals(1, calls.get());
        assertEquals(5, outcome.sources().stream().filter(row -> !Boolean.TRUE.equals(row.get("empty"))).count());
        assertTrue(outcome.context().contains("货号 H1"));
        assertFalse(outcome.context().contains("货号 H6"));
        assertFalse(outcome.context().contains("货号 H7"));
        assertEquals("产品", outcome.sources().get(0).get("sourceLabel"));
    }

    @Test
    void allRequestKeepsMoreThanFive() {
        ImageSearchProperties properties = enabled();
        properties.setChatTopK(5);
        properties.setChatMaxResults(50);
        properties.setChatMinScore(0.30d);
        List<ImageSearchModels.RawHit> raw = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            raw.add(new ImageSearchModels.RawHit(
                    ImageVectorRecord.library("lib-" + i, "宝娜斯集团", "k", "素材" + i, "", "宝娜斯集团"),
                    0.9f - i * 0.02f));
        }
        ChatVisualSearchService service = service(properties, new AtomicInteger(), raw, true);
        ChatVisualSearchService.Outcome outcome = service.search("找相似款，全部列出来", List.of(image()), "宝娜斯集团", true);
        assertEquals(7, outcome.sources().size());
        assertEquals("素材", outcome.sources().get(0).get("sourceLabel"));
    }

    private static ChatVisualSearchService service(ImageSearchProperties properties, AtomicInteger calls,
                                                   List<ImageSearchModels.RawHit> hits, boolean withGoods) {
        return new ChatVisualSearchService(properties, new ImageSearchQueryService(
                properties, embedder(calls), stubIndex(hits), (found, company) -> {
            List<ImageSearchModels.ImageSearchHitView> views = new ArrayList<>();
            for (ImageSearchModels.RawHit hit : found) {
                ImageSearchModels.ImageSearchHitView view = ImageSearchModels.fromRecord(
                        hit.record(), hit.score(), "https://example.test/" + hit.record().sourceId());
                if (withGoods && ImageSearchFilters.SOURCE_GOODS.equals(hit.record().source())) {
                    ImageSearchModels.GoodsBrief brief = new ImageSearchModels.GoodsBrief();
                    brief.setGoodsNo(hit.record().goodsNo());
                    brief.setProductName(hit.record().title());
                    brief.setSampler("张三");
                    view.setGoods(brief);
                }
                views.add(view);
            }
            return views;
        }));
    }

    private static ImageSearchProperties enabled() {
        ImageSearchProperties properties = new ImageSearchProperties();
        properties.setEnabled(true);
        return properties;
    }

    private static String image() {
        return Base64.getEncoder().encodeToString(new byte[64]);
    }

    private static ImageSearchModels.RawHit library(String id, float score) {
        return new ImageSearchModels.RawHit(
                ImageVectorRecord.library(id, "宝娜斯集团", "k", id, "", "宝娜斯集团"), score);
    }

    private static ImageEmbedder embedder(AtomicInteger calls) {
        return new ImageEmbedder() {
            @Override
            public float[] embedImage(byte[] data, String filename) {
                calls.incrementAndGet();
                return new float[512];
            }

            @Override
            public float[] embedText(String text) {
                calls.incrementAndGet();
                return new float[512];
            }
        };
    }

    private static ImageVectorIndex stubIndex(List<ImageSearchModels.RawHit> hits) {
        return new ImageVectorIndex() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void upsert(ImageVectorRecord record, float[] embedding) {
            }

            @Override
            public void deleteById(String vectorId) {
            }

            @Override
            public List<ImageSearchModels.RawHit> search(float[] embedding, String scope, String company, int topK) {
                return hits;
            }
        };
    }
}
