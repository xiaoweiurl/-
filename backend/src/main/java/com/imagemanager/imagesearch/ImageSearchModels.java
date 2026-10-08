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
        /** 向量主键，文字重排时取回库里的图片向量。 */
        private String vectorId;
        /** 素材 created_at 或商品库 created_at，毫秒。时间条件靠它后过滤。 */
        private Long createdAt;
        /** 融合前的图片相似度。阈值仍看它，避免只因文字分把结果丢掉。 */
        private float imageScore;
        /** 中文条件与图片向量的相似度。没有重排时为 0。 */
        private float textScore;
        /** product：同款一张卡片。image：一张图一条。 */
        private String cardType;
        /** SAME_PRODUCT / SIMILAR_REFERENCE / MIXED。历史记录靠它选卡片。 */
        private String scenario;
        /** 同款卡片里，主图以外的其他图片。 */
        private List<ImageThumb> images = new ArrayList<>();
        private GoodsBrief goods;
        private List<GoodsBrief> relatedGoods = new ArrayList<>();
        /**
         * 已核对存在、且属于当前公司的打样单。没有记录时为空，前端不渲染链接。
         */
        private String sampleOrderPath;
        /**
         * 已核对存在、且属于当前公司的商品详情。没有记录时为空。
         */
        private String productDetailPath;
    }

    @Data
    public static class ImageSearchResponse {
        private boolean enabled = true;
        private String mode;
        /** 本次结果用的场景，前端按它画卡片。 */
        private String scenario;
        private int tookMs;
        private List<ImageSearchHitView> results = new ArrayList<>();
        /** 从文字里解析出的条件，前端画成可去掉的标签。 */
        private List<SearchFilter> filters = new ArrayList<>();
        /** 某条硬条件把结果筛空时的说明。此时 results 是去掉该条件后的结果。 */
        private String filterNotice;
        private boolean filterRelaxed;
    }

    @Data
    public static class SearchFilter {
        private String id;
        private String kind;
        private String label;
        private String value;
        private boolean applied = true;
        private boolean relaxed;
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
        view.setVectorId(record.vectorId());
        return view;
    }

    /**
     * 对话阈值看图片分。重排后的展示分可以更低，但不能因此把原本过线的图丢掉。
     */
    public static float gateScore(ImageSearchHitView hit) {
        if (hit == null) {
            return 0f;
        }
        if (hit.getImageScore() > 0f) {
            return hit.getImageScore();
        }
        return hit.getScore();
    }
}
