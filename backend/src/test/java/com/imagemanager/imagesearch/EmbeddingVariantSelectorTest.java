package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingVariantSelectorTest {

    @Test
    void cropFallsBackWhenTheCollectionIsOff() {
        ImageSearchProperties properties = new ImageSearchProperties();
        properties.setCropEnabled(false);
        properties.setSameProductVariant("crop");
        properties.setSimilarReferenceVariant("crop");
        assertEquals(EmbeddingVariant.FULL, EmbeddingVariantSelector.select(
                VisualSearchScenario.SAME_PRODUCT, properties, true));
        assertEquals(EmbeddingVariant.FULL, EmbeddingVariantSelector.select(
                VisualSearchScenario.SIMILAR_REFERENCE, properties, false));
    }

    @Test
    void similarReferenceUsesCropWhenItIsReady() {
        ImageSearchProperties properties = enabledCrop();
        assertEquals(EmbeddingVariant.CROP, EmbeddingVariantSelector.select(
                VisualSearchScenario.SIMILAR_REFERENCE, properties, true));
    }

    @Test
    void sameProductCanBePinnedToTheWholeImageCollection() {
        ImageSearchProperties properties = enabledCrop();
        properties.setSameProductVariant("full");
        assertEquals(EmbeddingVariant.FULL, EmbeddingVariantSelector.select(
                VisualSearchScenario.SAME_PRODUCT, properties, true));
        assertEquals(EmbeddingVariant.CROP, EmbeddingVariantSelector.select(
                VisualSearchScenario.SIMILAR_REFERENCE, properties, true));
    }

    @Test
    void sameProductDefaultsToCropOnlyAfterTheCollectionExists() {
        ImageSearchProperties properties = enabledCrop();
        assertEquals(EmbeddingVariant.CROP, EmbeddingVariantSelector.select(
                VisualSearchScenario.SAME_PRODUCT, properties, true));
        assertEquals(EmbeddingVariant.FULL, EmbeddingVariantSelector.select(
                VisualSearchScenario.SAME_PRODUCT, properties, false));
    }

    @Test
    void chatSearchesTheSelectedCollection() {
        ImageSearchProperties properties = enabledCrop();
        properties.setSimilarReferenceVariant("crop");
        properties.setSameProductVariant("full");
        RecordingIndex index = new RecordingIndex(true);
        ChatVisualSearchService service = new ChatVisualSearchService(properties, query(properties, index));

        service.search("找相似的素材", List.of(image()), "宝娜斯集团", true);
        assertEquals(List.of(EmbeddingVariant.CROP), index.variants);

        index.variants.clear();
        service.search("这个是什么货号", List.of(image()), "宝娜斯集团", true);
        assertEquals(List.of(EmbeddingVariant.FULL), index.variants);
    }

    @Test
    void cropRequestFallsBackToFullWhenTheCollectionIsMissing() {
        ImageSearchProperties properties = enabledCrop();
        RecordingIndex index = new RecordingIndex(false);
        ChatVisualSearchService service = new ChatVisualSearchService(properties, query(properties, index));
        service.search("找图", List.of(image()), "宝娜斯集团", true);
        assertEquals(List.of(EmbeddingVariant.FULL), index.variants);
    }

    @Test
    void shortTextProbesTheWholeImageCollectionThenMaySearchCrop() {
        ImageSearchProperties properties = enabledCrop();
        properties.setSameProductProbeMinScore(0.45d);
        properties.setSameProductVariant("crop");
        RecordingIndex index = new RecordingIndex(true);
        index.hits = List.of(new ImageSearchModels.RawHit(
                ImageVectorRecord.goods(9, "main", "宝娜斯集团", "g", "H9", "甲", "宝娜斯集团"), 0.7f));
        ChatVisualSearchService service = new ChatVisualSearchService(properties, query(properties, index));
        ChatVisualSearchService.Outcome outcome = service.search("", List.of(image()), "宝娜斯集团", true);
        assertEquals(List.of(EmbeddingVariant.FULL, EmbeddingVariant.CROP), index.variants);
        assertEquals("SAME_PRODUCT", outcome.sources().get(0).get("scenario"));
        assertEquals("product", outcome.sources().get(0).get("cardType"));
    }

    @Test
    void modalScopesPickCollectionsAndCardTypes() {
        ImageSearchProperties properties = enabledCrop();
        properties.setSameProductVariant("full");
        properties.setSimilarReferenceVariant("crop");
        RecordingIndex index = new RecordingIndex(true);
        index.hits = List.of(
                new ImageSearchModels.RawHit(
                        ImageVectorRecord.goods(9, "main", "宝娜斯集团", "g", "H9", "甲", "宝娜斯集团"), 0.8f),
                new ImageSearchModels.RawHit(
                        ImageVectorRecord.goods(9, "side", "宝娜斯集团", "g2", "H9", "甲", "宝娜斯集团"), 0.6f),
                new ImageSearchModels.RawHit(
                        ImageVectorRecord.library("lib", "宝娜斯集团", "k", "红色", "", "宝娜斯集团"), 0.5f)
        );
        VisualSearchExecutor executor = new VisualSearchExecutor(properties, query(properties, index));

        index.variants.clear();
        ImageSearchModels.ImageSearchResponse goods = executor.search(new byte[]{1, 2, 3, 4}, "a.jpg", null, "goods", 5, "宝娜斯集团");
        assertEquals("SAME_PRODUCT", goods.getScenario());
        assertEquals(EmbeddingVariant.FULL, index.variants.get(index.variants.size() - 1));
        assertEquals("product", goods.getResults().get(0).getCardType());
        assertEquals(1, goods.getResults().size());

        index.variants.clear();
        ImageSearchModels.ImageSearchResponse library = executor.search(new byte[]{1, 2, 3, 4}, "a.jpg", null, "library", 5, "宝娜斯集团");
        assertEquals("SIMILAR_REFERENCE", library.getScenario());
        assertTrue(index.variants.contains(EmbeddingVariant.CROP));
        assertEquals("image", library.getResults().get(0).getCardType());

        index.variants.clear();
        ImageSearchModels.ImageSearchResponse mixed = executor.search(new byte[]{1, 2, 3, 4}, "a.jpg", "红色蕾丝", "all", 5, "宝娜斯集团");
        assertEquals("MIXED", mixed.getScenario());
        assertEquals("product", mixed.getResults().get(0).getCardType());
        assertTrue(mixed.getResults().stream().anyMatch(hit -> "image".equals(hit.getCardType())));
    }

    private static ImageSearchProperties enabledCrop() {
        ImageSearchProperties properties = new ImageSearchProperties();
        properties.setEnabled(true);
        properties.setCropEnabled(true);
        return properties;
    }

    private static String image() {
        return java.util.Base64.getEncoder().encodeToString(new byte[64]);
    }

    private static ImageSearchQueryService query(ImageSearchProperties properties, RecordingIndex index) {
        AtomicInteger calls = new AtomicInteger();
        return new ImageSearchQueryService(properties, new ImageEmbedder() {
            @Override
            public float[] embedImage(byte[] data, String filename) {
                calls.incrementAndGet();
                return new float[512];
            }

            @Override
            public float[] embedImage(byte[] data, String filename, boolean crop) {
                calls.incrementAndGet();
                return new float[512];
            }

            @Override
            public float[] embedText(String text) {
                calls.incrementAndGet();
                return new float[512];
            }
        }, index, (found, company) -> {
            List<ImageSearchModels.ImageSearchHitView> views = new ArrayList<>();
            for (ImageSearchModels.RawHit hit : found) {
                ImageSearchModels.ImageSearchHitView view = ImageSearchModels.fromRecord(
                        hit.record(), hit.score(), "https://example.test/" + hit.record().sourceId() + "/" + hit.record().slot());
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
        private final boolean cropReady;
        private final List<EmbeddingVariant> variants = new CopyOnWriteArrayList<>();
        private List<ImageSearchModels.RawHit> hits = List.of(
                new ImageSearchModels.RawHit(
                        ImageVectorRecord.library("lib", "宝娜斯集团", "k", "素材", "", "宝娜斯集团"), 0.8f));

        private RecordingIndex(boolean cropReady) {
            this.cropReady = cropReady;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean supports(EmbeddingVariant variant) {
            return variant == EmbeddingVariant.FULL || cropReady;
        }

        @Override
        public void upsert(ImageVectorRecord record, float[] embedding) {
        }

        @Override
        public void deleteById(String vectorId) {
        }

        @Override
        public List<ImageSearchModels.RawHit> search(float[] embedding, String scope, String company, int topK) {
            return search(EmbeddingVariant.FULL, embedding, scope, company, topK);
        }

        @Override
        public List<ImageSearchModels.RawHit> search(EmbeddingVariant variant, float[] embedding,
                                                     String scope, String company, int topK) {
            variants.add(variant);
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
