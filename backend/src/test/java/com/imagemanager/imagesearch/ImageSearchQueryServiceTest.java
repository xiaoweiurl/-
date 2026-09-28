package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageSearchQueryServiceTest {

    @Test
    void disabledDoesNotCallEmbedder() {
        ImageSearchProperties properties = new ImageSearchProperties();
        AtomicInteger calls = new AtomicInteger();
        ImageSearchQueryService service = service(properties, calls, List.of());
        assertThrows(ImageSearchDisabledException.class,
                () -> service.search(new byte[]{1, 2, 3}, "a.jpg", null, "all", 5, "宝娜斯集团"));
        assertEquals(0, calls.get());
    }

    @Test
    void searchDropsOtherCompanyLibraryButKeepsGoods() {
        ImageSearchProperties properties = new ImageSearchProperties();
        properties.setEnabled(true);
        AtomicInteger calls = new AtomicInteger();
        ImageVectorRecord ours = ImageVectorRecord.library("a", "宝娜斯集团", "k", "甲", "P1", "宝娜斯集团");
        ImageVectorRecord theirs = ImageVectorRecord.library("b", "其他公司", "k2", "乙", "", "宝娜斯集团");
        ImageVectorRecord goods = ImageVectorRecord.goods(9L, "main", "宝娜斯集团", "g", "H1", "款式", "宝娜斯集团");
        ImageSearchQueryService service = service(properties, calls, List.of(
                new ImageSearchModels.RawHit(ours, 0.91f),
                new ImageSearchModels.RawHit(theirs, 0.8f),
                new ImageSearchModels.RawHit(goods, 0.7f)));

        ImageSearchModels.ImageSearchResponse response = service.search(
                new byte[]{1, 2, 3}, "query.jpg", "红色蕾丝", "all", 10, "宝娜斯集团");

        assertEquals(1, calls.get());
        assertEquals("image", response.getMode());
        assertEquals(2, response.getResults().size());
        assertEquals("a", response.getResults().get(0).getSourceId());
        assertEquals(91, response.getResults().get(0).getScorePercent());
        assertEquals("9", response.getResults().get(1).getSourceId());
        assertEquals("主图", response.getResults().get(1).getSlotLabel());
    }

    @Test
    void libraryScopeHidesGoods() {
        ImageSearchProperties properties = new ImageSearchProperties();
        properties.setEnabled(true);
        ImageVectorRecord ours = ImageVectorRecord.library("a", "宝娜斯集团", "k", "甲", "", "宝娜斯集团");
        ImageVectorRecord goods = ImageVectorRecord.goods(9L, "side", "宝娜斯集团", "g", "H1", "款式", "宝娜斯集团");
        ImageVectorIndex index = stubIndex(List.of(
                new ImageSearchModels.RawHit(ours, 0.5f),
                new ImageSearchModels.RawHit(goods, 0.4f)));
        ImageSearchQueryService service = new ImageSearchQueryService(properties, unusedEmbedder(new AtomicInteger()),
                index, (hits, company) -> hits.stream()
                .map(hit -> ImageSearchModels.fromRecord(hit.record(), hit.score(), null))
                .toList());
        List<ImageSearchModels.RawHit> hits = service.searchRaw(new float[512], "library", "宝娜斯集团", 5);
        assertEquals(1, hits.size());
        assertEquals("library", hits.get(0).record().source());
    }

    private static ImageSearchQueryService service(ImageSearchProperties properties, AtomicInteger calls,
                                                   List<ImageSearchModels.RawHit> hits) {
        return new ImageSearchQueryService(properties, unusedEmbedder(calls), stubIndex(hits),
                (found, company) -> {
                    List<ImageSearchModels.ImageSearchHitView> views = new ArrayList<>();
                    for (ImageSearchModels.RawHit hit : found) {
                        views.add(ImageSearchModels.fromRecord(hit.record(), hit.score(), null));
                    }
                    return views;
                });
    }

    private static ImageEmbedder unusedEmbedder(AtomicInteger calls) {
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
