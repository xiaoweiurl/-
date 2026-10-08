package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SamplerImageSearchTest {

    @Test
    void goodsCompanyFilterDropsLibraryAndOtherCompanies() {
        String filter = ImageSearchFilters.toMilvus(ImageSearchFilters.SOURCE_GOODS_COMPANY, "宝娜斯集团");
        assertTrue(filter.contains("source == \"goods\""));
        assertTrue(filter.contains("company == \"宝娜斯集团\""));
        assertFalse(filter.contains("library"));

        ImageVectorRecord library = ImageVectorRecord.library("a", "宝娜斯集团", "k", "素材", "", "宝娜斯集团");
        ImageVectorRecord other = ImageVectorRecord.goods(8L, "main", "其他公司", "g", "H8", "乙", "宝娜斯集团");
        ImageVectorRecord ours = ImageVectorRecord.goods(9L, "main", "宝娜斯集团", "g", "H9", "甲", "宝娜斯集团");
        assertFalse(ImageSearchFilters.matches(library, ImageSearchFilters.SOURCE_GOODS_COMPANY, "宝娜斯集团"));
        assertFalse(ImageSearchFilters.matches(other, ImageSearchFilters.SOURCE_GOODS_COMPANY, "宝娜斯集团"));
        assertTrue(ImageSearchFilters.matches(ours, ImageSearchFilters.SOURCE_GOODS_COMPANY, "宝娜斯集团"));
    }

    @Test
    void samplerSearchReturnsOnlySameCompanyGoodsGroupedByProduct() {
        ImageSearchProperties properties = new ImageSearchProperties();
        properties.setEnabled(true);
        properties.setSamplerEnabled(true);
        properties.setChatTopK(5);
        RecordingIndex index = new RecordingIndex();
        index.hits.add(new ImageSearchModels.RawHit(
                ImageVectorRecord.library("lib", "宝娜斯集团", "k", "素材", "", "宝娜斯集团"), 0.99f));
        index.hits.add(new ImageSearchModels.RawHit(
                ImageVectorRecord.goods(8L, "main", "其他公司", "g", "H8", "乙", "宝娜斯集团"), 0.95f));
        index.hits.add(new ImageSearchModels.RawHit(
                ImageVectorRecord.goods(9L, "main", "宝娜斯集团", "g", "H9", "甲", "宝娜斯集团"), 0.80f));
        index.hits.add(new ImageSearchModels.RawHit(
                ImageVectorRecord.goods(9L, "side", "宝娜斯集团", "g2", "H9", "甲侧", "宝娜斯集团"), 0.70f));
        VisualSearchExecutor executor = new VisualSearchExecutor(properties, query(properties, index));

        ImageSearchModels.ImageSearchResponse response = executor.searchSampler(
                new byte[]{1, 2, 3, 4}, "shot.jpg", 5, "宝娜斯集团");

        assertEquals(List.of(ImageSearchFilters.SOURCE_GOODS_COMPANY), index.scopes);
        assertEquals(VisualSearchScenario.SAME_PRODUCT.name(), response.getScenario());
        assertEquals(1, response.getResults().size());
        ImageSearchModels.ImageSearchHitView card = response.getResults().get(0);
        assertEquals("product", card.getCardType());
        assertEquals("goods", card.getSource());
        assertEquals("H9", card.getGoods().getGoodsNo());
        assertTrue(response.getResults().stream().noneMatch(hit -> "library".equals(hit.getSource())));
    }

    @Test
    void samplerFlagCanTurnTheEntryOff() {
        ImageSearchProperties properties = new ImageSearchProperties();
        properties.setEnabled(true);
        properties.setSamplerEnabled(false);
        VisualSearchExecutor executor = new VisualSearchExecutor(properties, query(properties, new RecordingIndex()));
        assertThrows(ImageSearchDisabledException.class,
                () -> executor.searchSampler(new byte[]{1, 2, 3, 4}, "shot.jpg", 5, "宝娜斯集团"));
    }

    private static ImageSearchQueryService query(ImageSearchProperties properties, RecordingIndex index) {
        return new ImageSearchQueryService(properties, new ImageEmbedder() {
            @Override
            public float[] embedImage(byte[] data, String filename) {
                return new float[512];
            }

            @Override
            public float[] embedText(String text) {
                return new float[512];
            }
        }, index, (found, company) -> {
            List<ImageSearchModels.ImageSearchHitView> views = new ArrayList<>();
            for (ImageSearchModels.RawHit hit : found) {
                ImageSearchModels.ImageSearchHitView view = ImageSearchModels.fromRecord(
                        hit.record(), hit.score(), "https://example.test/" + hit.record().sourceId());
                if (ImageSearchFilters.SOURCE_GOODS.equals(hit.record().source())) {
                    ImageSearchModels.GoodsBrief brief = new ImageSearchModels.GoodsBrief();
                    brief.setId(Long.parseLong(hit.record().sourceId()));
                    brief.setGoodsNo(hit.record().goodsNo());
                    brief.setProductName(hit.record().title());
                    brief.setSampler("张三");
                    view.setGoods(brief);
                }
                views.add(view);
            }
            return views;
        });
    }

    private static final class RecordingIndex implements ImageVectorIndex {
        private final List<String> scopes = new CopyOnWriteArrayList<>();
        private final List<ImageSearchModels.RawHit> hits = new ArrayList<>();

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
            scopes.add(scope);
            List<ImageSearchModels.RawHit> kept = new ArrayList<>();
            for (ImageSearchModels.RawHit hit : hits) {
                if (ImageSearchFilters.matches(hit.record(), scope, company)) {
                    kept.add(hit);
                }
            }
            return kept;
        }
    }
}
