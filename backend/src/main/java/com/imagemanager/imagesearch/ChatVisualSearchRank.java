package com.imagemanager.imagesearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话里的相似图：丢掉低分，合并重复，只留前几条，并写成给模型看的短摘要。
 */
public final class ChatVisualSearchRank {

    public static final String SOURCE_KIND = "visual_match";

    private ChatVisualSearchRank() {
    }

    public static List<ImageSearchModels.ImageSearchHitView> select(
            List<ImageSearchModels.ImageSearchHitView> hits, double minScore, int limit) {
        if (hits == null || hits.isEmpty() || limit <= 0) {
            return List.of();
        }
        float floor = (float) minScore;
        Map<String, ImageSearchModels.ImageSearchHitView> best = new LinkedHashMap<>();
        for (ImageSearchModels.ImageSearchHitView hit : hits) {
            if (hit == null || Float.isNaN(hit.getScore()) || hit.getScore() < floor) {
                continue;
            }
            String key = key(hit);
            ImageSearchModels.ImageSearchHitView current = best.get(key);
            if (current == null || hit.getScore() > current.getScore()) {
                best.put(key, hit);
            }
        }
        List<ImageSearchModels.ImageSearchHitView> ordered = new ArrayList<>(best.values());
        ordered.sort((a, b) -> Float.compare(b.getScore(), a.getScore()));
        if (ordered.size() > limit) {
            return List.copyOf(ordered.subList(0, limit));
        }
        return List.copyOf(ordered);
    }

    public static String context(List<ImageSearchModels.ImageSearchHitView> kept) {
        StringBuilder text = new StringBuilder();
        text.append("## 以图搜图结果〔上传图片在素材库和商品库/打样里的相似图。")
                .append("只能使用下列命中，禁止编造货号、品名、打样员或相似度〕：\n");
        if (kept == null || kept.isEmpty()) {
            text.append("未找到达到相似度阈值的图片。请明确告诉用户没有找到相似的产品图或素材图，")
                    .append("不要猜测货号，也不要编造历史打样记录。\n");
            return text.toString();
        }
        int index = 1;
        for (ImageSearchModels.ImageSearchHitView hit : kept) {
            text.append(index++).append(". ").append(describe(hit)).append('\n');
        }
        text.append("回答里提到的货号、品名、打样员和相似度必须与上面的列表一致，列表里没有的不要补充。\n");
        return text.toString();
    }

    public static List<Map<String, Object>> toSources(List<ImageSearchModels.ImageSearchHitView> kept) {
        List<Map<String, Object>> sources = new ArrayList<>();
        if (kept == null || kept.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("id", "visual-empty");
            empty.put("source", SOURCE_KIND);
            empty.put("empty", true);
            empty.put("title", "未找到相似图片");
            empty.put("excerpt", "没有高于相似度阈值的素材或产品图");
            sources.add(empty);
            return sources;
        }
        int index = 1;
        for (ImageSearchModels.ImageSearchHitView hit : kept) {
            ImageSearchModels.GoodsBrief goods = goodsOf(hit);
            String label = sourceLabel(hit);
            int percent = ImageSearchModels.scorePercent(hit.getScore());
            String line = describe(hit);
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("id", "visual-" + index);
            source.put("source", SOURCE_KIND);
            source.put("recordId", key(hit));
            source.put("title", titleOf(hit, goods));
            source.put("excerpt", line);
            source.put("score", hit.getScore());
            source.put("scorePercent", percent);
            if (hit.getImageUrl() != null && !hit.getImageUrl().isBlank()) {
                source.put("imageUrl", hit.getImageUrl());
            }
            source.put("sourceLabel", label);
            source.put("visualSource", hit.getSource() == null ? "" : hit.getSource());
            if (goods != null) {
                putText(source, "goodsNo", goods.getGoodsNo());
                putText(source, "productName", goods.getProductName());
                putText(source, "sampler", goods.getSampler());
            }
            putText(source, "albumName", hit.getAlbumName());
            putText(source, "slotLabel", hit.getSlotLabel());
            sources.add(source);
            index++;
        }
        return sources;
    }

    static String describe(ImageSearchModels.ImageSearchHitView hit) {
        ImageSearchModels.GoodsBrief goods = goodsOf(hit);
        StringBuilder line = new StringBuilder();
        line.append("相似度 ").append(ImageSearchModels.scorePercent(hit.getScore()))
                .append("%｜").append(sourceLabel(hit));
        if (goods != null) {
            append(line, "货号", goods.getGoodsNo());
            append(line, "品名", goods.getProductName());
            append(line, "打样员", goods.getSampler());
        }
        if (!ImageSearchFilters.SOURCE_GOODS.equals(hit.getSource())) {
            append(line, "标题", hit.getTitle());
            append(line, "相册", hit.getAlbumName());
        } else {
            append(line, "槽位", hit.getSlotLabel());
        }
        return line.toString();
    }

    private static String sourceLabel(ImageSearchModels.ImageSearchHitView hit) {
        return ImageSearchFilters.SOURCE_GOODS.equals(hit.getSource()) ? "产品" : "素材";
    }

    private static String titleOf(ImageSearchModels.ImageSearchHitView hit, ImageSearchModels.GoodsBrief goods) {
        if (goods != null) {
            String named = join(goods.getGoodsNo(), goods.getProductName());
            if (!named.isBlank()) {
                return named;
            }
            if (goods.getFolderName() != null && !goods.getFolderName().isBlank()) {
                return goods.getFolderName().trim();
            }
        }
        if (hit.getTitle() != null && !hit.getTitle().isBlank()) {
            return hit.getTitle().trim();
        }
        return sourceLabel(hit);
    }

    private static ImageSearchModels.GoodsBrief goodsOf(ImageSearchModels.ImageSearchHitView hit) {
        if (hit.getGoods() != null) {
            return hit.getGoods();
        }
        if (hit.getRelatedGoods() != null && !hit.getRelatedGoods().isEmpty()) {
            return hit.getRelatedGoods().get(0);
        }
        return null;
    }

    private static String key(ImageSearchModels.ImageSearchHitView hit) {
        return text(hit.getSource()) + "|" + text(hit.getSourceId()) + "|" + text(hit.getSlot());
    }

    private static void append(StringBuilder line, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        line.append('｜').append(label).append(' ').append(value.trim());
    }

    private static void putText(Map<String, Object> source, String key, String value) {
        if (value != null && !value.isBlank()) {
            source.put(key, value.trim());
        }
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
}
