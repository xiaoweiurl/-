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
    public static class ImageThumb {
        private String imageUrl;
        private String slot;
        private String slotLabel;
        private float score;
        private int scorePercent;
        private String source;
        private String sourceId;
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
        private String company;
        /** product：同款一张卡片。image：一张图一条。 */
        private String cardType;
        /** SAME_PRODUCT / SIMILAR_REFERENCE / MIXED。历史记录靠它选卡片。 */
        private String scenario;
        /** 同款卡片里，主图以外的其他图片。 */
        private List<ImageThumb> images = new ArrayList<>();
        private GoodsBrief goods;
        private List<GoodsBrief> relatedGoods = new ArrayList<>();
    }

    @Data
    public static class ImageSearchResponse {
        private boolean enabled = true;
        private String mode;
        /** 本次结果用的场景，前端按它画卡片。 */
        private String scenario;
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
        view.setCompany(record.company());
        return view;
    }
}
