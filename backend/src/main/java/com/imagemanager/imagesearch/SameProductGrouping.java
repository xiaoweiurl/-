package com.imagemanager.imagesearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 同款：按商品合并。商品用货号，没有货号再用商品库 id。
 * 素材没有货号时各自一条；只有唯一一条可靠的商品关联，或素材自己带了同一个 product_id，才并到一起。
 * 分数是组内最高相似度，多张命中可以加一小段奖励，加完不超过 1。
 */
public final class SameProductGrouping {

    private SameProductGrouping() {
    }

    public static List<ImageSearchModels.ImageSearchHitView> group(List<ImageSearchModels.ImageSearchHitView> hits,
                                                                   int limit,
                                                                   double minScore,
                                                                   double bonusPerExtra,
                                                                   double bonusCap) {
        if (hits == null || hits.isEmpty() || limit <= 0) {
            return List.of();
        }
        float floor = (float) minScore;
        Map<String, List<ImageSearchModels.ImageSearchHitView>> buckets = new LinkedHashMap<>();
        for (ImageSearchModels.ImageSearchHitView hit : hits) {
            if (hit == null || Float.isNaN(hit.getScore()) || ImageSearchModels.gateScore(hit) < floor) {
                continue;
            }
            buckets.computeIfAbsent(groupKey(hit), key -> new ArrayList<>()).add(hit);
        }
        List<ImageSearchModels.ImageSearchHitView> cards = new ArrayList<>();
        for (List<ImageSearchModels.ImageSearchHitView> members : buckets.values()) {
            cards.add(toCard(members, bonusPerExtra, bonusCap));
        }
        cards.sort(Comparator.comparingDouble(ImageSearchModels.ImageSearchHitView::getScore).reversed());
        if (cards.size() > limit) {
            return List.copyOf(cards.subList(0, limit));
        }
        return List.copyOf(cards);
    }

    /**
     * 评测用。去掉查询图自己之后，按同款规则排出商品键。
     * 命中指这组键里出现了同一商品，不要求命中某一张特定的图。
     */
    public static List<String> rankedProductKeys(List<ImageSearchModels.RawHit> hits,
                                                 String excludeVectorId,
                                                 int limit,
                                                 double bonusPerExtra,
                                                 double bonusCap) {
        if (hits == null || hits.isEmpty() || limit <= 0) {
            return List.of();
        }
        Map<String, Bucket> buckets = new LinkedHashMap<>();
        for (ImageSearchModels.RawHit hit : hits) {
            if (hit == null || hit.record() == null || Float.isNaN(hit.score())) {
                continue;
            }
            if (excludeVectorId != null && !excludeVectorId.isBlank()
                    && excludeVectorId.equals(hit.record().vectorId())) {
                continue;
            }
            String key = productKey(hit.record());
            Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket());
            bucket.count++;
            if (hit.score() > bucket.max) {
                bucket.max = hit.score();
            }
        }
        List<Map.Entry<String, Bucket>> ordered = new ArrayList<>(buckets.entrySet());
        ordered.sort((left, right) -> Float.compare(scoreOf(right.getValue(), bonusPerExtra, bonusCap),
                scoreOf(left.getValue(), bonusPerExtra, bonusCap)));
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Bucket> entry : ordered) {
            keys.add(entry.getKey());
            if (keys.size() >= limit) {
                break;
            }
        }
        return List.copyOf(keys);
    }

    public static String productKey(ImageVectorRecord record) {
        String company = record.company() == null ? "" : record.company().trim();
        if (ImageSearchFilters.SOURCE_GOODS.equals(record.source())) {
            return company + "|" + goodsIdentity(record.goodsNo(), record.sourceId());
        }
        if (record.productId() != null && !record.productId().isBlank()) {
            return company + "|library-product:" + record.productId().trim();
        }
        return company + "|library:" + record.vectorId();
    }

    static String groupKey(ImageSearchModels.ImageSearchHitView hit) {
        String company = hit.getCompany() == null ? "" : hit.getCompany().trim();
        if (ImageSearchFilters.SOURCE_GOODS.equals(hit.getSource())) {
            ImageSearchModels.GoodsBrief goods = hit.getGoods();
            String goodsNo = goods == null ? "" : goods.getGoodsNo();
            String id = goods != null && goods.getId() > 0 ? Long.toString(goods.getId()) : hit.getSourceId();
            return company + "|" + goodsIdentity(goodsNo, id);
        }
        ImageSearchModels.GoodsBrief linked = reliableLink(hit);
        if (linked != null) {
            String id = linked.getId() > 0 ? Long.toString(linked.getId()) : hit.getSourceId();
            return company + "|" + goodsIdentity(linked.getGoodsNo(), id);
        }
        if (hit.getProductId() != null && !hit.getProductId().isBlank()) {
            return company + "|library-product:" + hit.getProductId().trim();
        }
        return company + "|library:" + text(hit.getSourceId());
    }

    private static ImageSearchModels.ImageSearchHitView toCard(List<ImageSearchModels.ImageSearchHitView> members,
                                                              double bonusPerExtra,
                                                              double bonusCap) {
        members.sort(Comparator.comparingDouble(ImageSearchModels.ImageSearchHitView::getScore).reversed());
        ImageSearchModels.ImageSearchHitView best = members.get(0);
        float max = best.getScore();
        int extra = members.size() - 1;
        float bonus = extra <= 0 ? 0f : (float) Math.min(Math.max(bonusCap, 0d), Math.max(bonusPerExtra, 0d) * extra);
        float score = Math.min(1f, max + bonus);

        ImageSearchModels.ImageSearchHitView card = new ImageSearchModels.ImageSearchHitView();
        card.setScore(score);
        card.setScorePercent(ImageSearchModels.scorePercent(score));
        card.setCardType("product");
        card.setScenario(VisualSearchScenario.SAME_PRODUCT.name());
        card.setImageUrl(best.getImageUrl());
        card.setSlot(best.getSlot());
        card.setSlotLabel(best.getSlotLabel());
        card.setTitle(best.getTitle());
        card.setAlbumName(best.getAlbumName());
        card.setProductId(best.getProductId());
        card.setCompany(best.getCompany());
        card.setImageScore(best.getImageScore());
        card.setTextScore(best.getTextScore());
        card.setCreatedAt(best.getCreatedAt());
        card.setVectorId(best.getVectorId());

        ImageSearchModels.GoodsBrief goods = goodsOf(members);
        card.setGoods(goods);
        if (goods != null) {
            card.setSource(ImageSearchFilters.SOURCE_GOODS);
            card.setSourceId(goods.getId() > 0 ? Long.toString(goods.getId()) : best.getSourceId());
            card.setRelatedGoods(List.of(goods));
            if (card.getTitle() == null || card.getTitle().isBlank()) {
                card.setTitle(join(goods.getGoodsNo(), goods.getProductName()));
            }
        } else {
            card.setSource(best.getSource());
            card.setSourceId(best.getSourceId());
            card.setRelatedGoods(best.getRelatedGoods());
        }

        List<ImageSearchModels.ImageThumb> thumbs = new ArrayList<>();
        for (int i = 1; i < members.size(); i++) {
            thumbs.add(thumb(members.get(i)));
        }
        card.setImages(thumbs);
        return card;
    }

    private static ImageSearchModels.GoodsBrief goodsOf(List<ImageSearchModels.ImageSearchHitView> members) {
        for (ImageSearchModels.ImageSearchHitView member : members) {
            if (member.getGoods() != null) {
                return member.getGoods();
            }
        }
        for (ImageSearchModels.ImageSearchHitView member : members) {
            ImageSearchModels.GoodsBrief linked = reliableLink(member);
            if (linked != null) {
                return linked;
            }
        }
        return null;
    }

    /**
     * 多条关联算不可靠，不并进商品。唯一一条，并且有货号或商品 id，才并。
     */
    static ImageSearchModels.GoodsBrief reliableLink(ImageSearchModels.ImageSearchHitView hit) {
        List<ImageSearchModels.GoodsBrief> related = hit.getRelatedGoods();
        if (related == null || related.size() != 1 || related.get(0) == null) {
            return null;
        }
        ImageSearchModels.GoodsBrief brief = related.get(0);
        boolean hasNo = brief.getGoodsNo() != null && !brief.getGoodsNo().isBlank();
        if (!hasNo && brief.getId() <= 0) {
            return null;
        }
        return brief;
    }

    private static ImageSearchModels.ImageThumb thumb(ImageSearchModels.ImageSearchHitView hit) {
        ImageSearchModels.ImageThumb thumb = new ImageSearchModels.ImageThumb();
        thumb.setImageUrl(hit.getImageUrl());
        thumb.setSlot(hit.getSlot());
        thumb.setSlotLabel(hit.getSlotLabel());
        thumb.setScore(hit.getScore());
        thumb.setScorePercent(hit.getScorePercent());
        thumb.setSource(hit.getSource());
        thumb.setSourceId(hit.getSourceId());
        return thumb;
    }

    static String goodsIdentity(String goodsNo, String fallbackId) {
        if (goodsNo != null && !goodsNo.isBlank()) {
            return "goods-no:" + goodsNo.trim().toLowerCase(Locale.ROOT);
        }
        return "goods-id:" + text(fallbackId);
    }

    private static float scoreOf(Bucket bucket, double bonusPerExtra, double bonusCap) {
        int extra = Math.max(0, bucket.count - 1);
        float bonus = extra == 0 ? 0f : (float) Math.min(Math.max(bonusCap, 0d), Math.max(bonusPerExtra, 0d) * extra);
        return Math.min(1f, bucket.max + bonus);
    }

    private static String join(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + " " + b;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Bucket {
        private float max = Float.NEGATIVE_INFINITY;
        private int count;
    }
}
