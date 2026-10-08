package com.imagemanager.imagesearch;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

public final class ImageSearchModels {

    private ImageSearchModels() {
    }

    public record RawHit(ImageVectorRecord record, float score) {
    }

    @Data
    public static class GoodsBrief {
        private long id;
        private String folderName;
        private String goodsNo;
        private String productName;
        private String sampler;
        private String initiator;
        private String customer;
        private String orderNo;
    }

    @Data
    public static class ImageSearchHitView {
        private float score;
        private int scorePercent;
        private String source;
        private String sourceId;
        private String slot;
        private String slotLabel;
        private String title;
        private String imageUrl;
        private String albumName;
        private String productId;
        private GoodsBrief goods;
        private List<GoodsBrief> relatedGoods = new ArrayList<>();
    }

    @Data
    public static class ImageSearchResponse {
        private boolean enabled = true;
        private String mode;
        private int tookMs;
        private List<ImageSearchHitView> results = new ArrayList<>();
    }

    public static int scorePercent(float score) {
        double clamped = Math.max(0d, Math.min(1d, score));
        return (int) Math.round(clamped * 100d);
    }

    public static ImageSearchHitView fromRecord(ImageVectorRecord record, float score, String imageUrl) {
        ImageSearchHitView view = new ImageSearchHitView();
        view.setScore(score);
        view.setScorePercent(scorePercent(score));
        view.setSource(record.source());
        view.setSourceId(record.sourceId());
        view.setSlot(record.slot());
        view.setSlotLabel(ImageSearchFilters.slotLabel(record.slot()));
        view.setTitle(record.title());
        view.setImageUrl(imageUrl);
        view.setProductId(record.productId());
        return view;
    }
}
