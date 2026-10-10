package com.imagemanager.imagesearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 硬条件在补全之后过滤。时间、打样员、相册不在图片向量的标量字段里。
 * 颜色、材质、款式用中文向量和库里的图片向量比对：比这批里最贴近的一条明显更低就丢掉。
 * 一条都对不上，或向量没取到时，不假装筛过，标签标成已放宽。
 */
public final class ImageSearchConditionRank {

    /** 和配置默认值一致。调用方没传差距时用这个。 */
    public static final double DEFAULT_TEXT_MARGIN = 0.18d;

    private ImageSearchConditionRank() {
    }

    public record Outcome(List<ImageSearchModels.ImageSearchHitView> hits,
                          List<ImageSearchCondition.Chip> filters,
                          String notice,
                          boolean relaxed) {
        public Outcome {
            hits = hits == null ? List.of() : List.copyOf(hits);
            filters = filters == null ? List.of() : List.copyOf(filters);
        }

        public static Outcome unchanged(List<ImageSearchModels.ImageSearchHitView> hits) {
            return new Outcome(hits, List.of(), "", false);
        }
    }

    public static Outcome apply(List<ImageSearchModels.ImageSearchHitView> hits,
                                ImageSearchCondition.Parsed parsed,
                                Map<String, float[]> vectors,
                                float[] textVector,
                                double imageWeight,
                                double textWeight,
                                double textFloor) {
        return apply(hits, parsed, vectors, textVector, imageWeight, textWeight, textFloor, DEFAULT_TEXT_MARGIN);
    }

    public static Outcome apply(List<ImageSearchModels.ImageSearchHitView> hits,
                                ImageSearchCondition.Parsed parsed,
                                Map<String, float[]> vectors,
                                float[] textVector,
                                double imageWeight,
                                double textWeight,
                                double textFloor,
                                double textMargin) {
        if (parsed == null || !parsed.active()) {
            return Outcome.unchanged(hits);
        }
        List<ImageSearchModels.ImageSearchHitView> working = hits == null ? new ArrayList<>() : new ArrayList<>(hits);
        List<ImageSearchCondition.Chip> chips = new ArrayList<>(parsed.chips());
        List<String> relaxedLabels = new ArrayList<>();

        for (HardFilter filter : hardFilters(parsed)) {
            if (working.isEmpty()) {
                break;
            }
            List<ImageSearchModels.ImageSearchHitView> next = filter.apply(working);
            if (next.isEmpty()) {
                relaxedLabels.add(filter.label);
                markRelaxed(chips, filter.id);
                continue;
            }
            working = next;
        }

        String attributeNote = "";
        if (parsed.hasAttributes()) {
            if (textVector == null || textVector.length == 0) {
                relaxAttributes(chips, parsed);
                attributeNote = "颜色、材质或款式没能参与筛选，下面仍按图片相似度排列。";
            } else {
                TextRerank reranked = rerank(working, vectors == null ? Map.of() : vectors, textVector,
                        imageWeight, textWeight, textFloor, textMargin);
                if (reranked.passed()) {
                    working = reranked.hits();
                } else if (reranked.rejectedAll()) {
                    relaxAttributes(chips, parsed);
                    for (String term : parsed.attributes()) {
                        relaxedLabels.add(term);
                    }
                } else {
                    relaxAttributes(chips, parsed);
                    attributeNote = "颜色、材质或款式没能参与筛选，下面仍按图片相似度排列。";
                }
            }
        }
        String notice = joinNotice(notice(relaxedLabels), attributeNote);
        boolean relaxed = !relaxedLabels.isEmpty() || !attributeNote.isEmpty();
        return new Outcome(working, chips, notice, relaxed);
    }

    /**
     * 文本里的范围已经把向量检索收窄到 0 条时，用去掉该范围后的结果补上，并标明是哪一条条件。
     */
    public static Outcome relaxScope(Outcome wider, ImageSearchCondition.Chip scopeChip) {
        if (scopeChip == null) {
            return wider;
        }
        List<ImageSearchCondition.Chip> chips = new ArrayList<>();
        chips.add(scopeChip.relaxedCopy());
        if (wider != null && wider.filters() != null) {
            chips.addAll(wider.filters());
        }
        List<String> labels = new ArrayList<>();
        labels.add(scopeChip.label());
        if (wider != null && wider.relaxed()) {
            for (ImageSearchCondition.Chip chip : wider.filters()) {
                if (chip.relaxed()) {
                    labels.add(chip.label());
                }
            }
        }
        String notice = notice(labels);
        List<ImageSearchModels.ImageSearchHitView> hits = wider == null ? List.of() : wider.hits();
        return new Outcome(hits, chips, notice, true);
    }

    public static String summary(List<ImageSearchCondition.Chip> chips, String notice) {
        StringBuilder text = new StringBuilder();
        if (chips != null) {
            List<String> applied = new ArrayList<>();
            for (ImageSearchCondition.Chip chip : chips) {
                if (chip.applied() && chip.label() != null && !chip.label().isBlank()) {
                    applied.add(chip.label());
                }
            }
            if (!applied.isEmpty()) {
                text.append("已应用筛选：").append(String.join("、", applied)).append('。');
            }
        }
        if (notice != null && !notice.isBlank()) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(notice);
        }
        return text.toString();
    }

    public static void attachSources(List<Map<String, Object>> sources, Outcome outcome) {
        if (sources == null || outcome == null || (outcome.filters().isEmpty() && (outcome.notice() == null || outcome.notice().isBlank()))) {
            return;
        }
        List<Map<String, Object>> filters = new ArrayList<>();
        for (ImageSearchCondition.Chip chip : outcome.filters()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("id", chip.id());
            one.put("kind", chip.kind());
            one.put("label", chip.label());
            one.put("value", chip.value());
            one.put("applied", chip.applied());
            one.put("relaxed", chip.relaxed());
            filters.add(one);
        }
        for (Map<String, Object> source : sources) {
            if (!filters.isEmpty()) {
                source.put("filters", filters);
            }
            if (outcome.notice() != null && !outcome.notice().isBlank()) {
                source.put("filterNotice", outcome.notice());
            }
        }
    }

    public static List<ImageSearchModels.SearchFilter> toViews(List<ImageSearchCondition.Chip> chips) {
        List<ImageSearchModels.SearchFilter> views = new ArrayList<>();
        if (chips == null) {
            return views;
        }
        for (ImageSearchCondition.Chip chip : chips) {
            ImageSearchModels.SearchFilter view = new ImageSearchModels.SearchFilter();
            view.setId(chip.id());
            view.setKind(chip.kind());
            view.setLabel(chip.label());
            view.setValue(chip.value());
            view.setApplied(chip.applied());
            view.setRelaxed(chip.relaxed());
            views.add(view);
        }
        return views;
    }

    public static double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) {
            return 0d;
        }
        double dot = 0d;
        double leftNorm = 0d;
        double rightNorm = 0d;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm <= 0d || rightNorm <= 0d) {
            return 0d;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * passed：留下贴近文字的命中，取不到向量的不能绕过颜色条件。
     * rejectedAll：文字分都低于下限，调用方放宽条件并恢复原列表（此时还没改分数）。
     * 两者都不是：这批没有可比的图片向量。
     */
    private record TextRerank(List<ImageSearchModels.ImageSearchHitView> hits, boolean passed, boolean rejectedAll) {
        private static TextRerank pass(List<ImageSearchModels.ImageSearchHitView> hits) {
            return new TextRerank(hits, true, false);
        }

        private static TextRerank rejectAll() {
            return new TextRerank(List.of(), false, true);
        }

        private static TextRerank unavailable() {
            return new TextRerank(List.of(), false, false);
        }
    }

    private static TextRerank rerank(List<ImageSearchModels.ImageSearchHitView> hits,
                                 Map<String, float[]> vectors,
                                 float[] textVector,
                                 double imageWeight,
                                 double textWeight,
                                 double textFloor,
                                 double textMargin) {
        double margin = textMargin;
        if (margin < 0d) {
            margin = 0d;
        } else if (margin > 1d) {
            margin = 1d;
        }
        List<Scored> scored = new ArrayList<>();
        boolean anyKnown = false;
        double best = Double.NEGATIVE_INFINITY;
        for (ImageSearchModels.ImageSearchHitView hit : hits) {
            if (hit == null) {
                continue;
            }
            float[] stored = vectors.get(hit.getVectorId() == null ? "" : hit.getVectorId());
            if (stored == null) {
                scored.add(new Scored(hit, Double.NaN));
                continue;
            }
            double textScore = cosine(textVector, stored);
            anyKnown = true;
            if (textScore > best) {
                best = textScore;
            }
            scored.add(new Scored(hit, textScore));
        }
        if (!anyKnown) {
            return TextRerank.unavailable();
        }
        double cutoff = Math.max(textFloor, best - margin);
        List<ImageSearchModels.ImageSearchHitView> kept = new ArrayList<>();
        for (Scored item : scored) {
            if (Double.isNaN(item.text) || item.text < cutoff) {
                continue;
            }
            ImageSearchModels.ImageSearchHitView hit = item.hit;
            float imageScore = hit.getScore();
            double fused = imageWeight * imageScore + textWeight * item.text;
            if (fused < 0d) {
                fused = 0d;
            } else if (fused > 1d) {
                fused = 1d;
            }
            hit.setImageScore(imageScore);
            hit.setTextScore((float) item.text);
            hit.setScore((float) fused);
            hit.setScorePercent(ImageSearchModels.scorePercent((float) fused));
            kept.add(hit);
        }
        if (kept.isEmpty()) {
            return TextRerank.rejectAll();
        }
        kept.sort((left, right) -> Float.compare(right.getScore(), left.getScore()));
        return TextRerank.pass(kept);
    }

    private record Scored(ImageSearchModels.ImageSearchHitView hit, double text) {
    }

    private static void relaxAttributes(List<ImageSearchCondition.Chip> chips, ImageSearchCondition.Parsed parsed) {
        if (parsed.attributes() == null) {
            return;
        }
        for (String term : parsed.attributes()) {
            markRelaxed(chips, "attr:" + term);
        }
    }

    private static String joinNotice(String hard, String attribute) {
        StringBuilder text = new StringBuilder();
        if (hard != null && !hard.isBlank()) {
            text.append(hard);
        }
        if (attribute != null && !attribute.isBlank()) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(attribute);
        }
        return text.toString();
    }

    private static void markRelaxed(List<ImageSearchCondition.Chip> chips, String id) {
        for (int i = 0; i < chips.size(); i++) {
            if (id.equals(chips.get(i).id())) {
                chips.set(i, chips.get(i).relaxedCopy());
            }
        }
    }

    static String notice(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (String label : labels) {
            text.append('「').append(label).append('」');
        }
        text.append("把符合条件的图片都筛掉了，下面是去掉");
        text.append(labels.size() > 1 ? "这些条件" : "这个条件");
        text.append("后的结果。");
        return text.toString();
    }

    private static List<HardFilter> hardFilters(ImageSearchCondition.Parsed parsed) {
        List<HardFilter> filters = new ArrayList<>();
        if (parsed.scope() != null && !parsed.scope().isBlank()) {
            String scope = parsed.scope();
            String label = ImageSearchFilters.SOURCE_LIBRARY.equals(scope) ? "只看素材" : "只看打样";
            filters.add(new HardFilter("scope", label, hits -> {
                List<ImageSearchModels.ImageSearchHitView> next = new ArrayList<>();
                for (ImageSearchModels.ImageSearchHitView hit : hits) {
                    if (scope.equals(hit.getSource())) {
                        next.add(hit);
                    }
                }
                return next;
            }));
        }
        if (parsed.time() != null) {
            ImageSearchCondition.Window window = parsed.time();
            filters.add(new HardFilter("time", window.label(), hits -> {
                List<ImageSearchModels.ImageSearchHitView> next = new ArrayList<>();
                for (ImageSearchModels.ImageSearchHitView hit : hits) {
                    Long at = hit.getCreatedAt();
                    if (at != null && at >= window.fromEpochMs() && at < window.toEpochMs()) {
                        next.add(hit);
                    }
                }
                return next;
            }));
        }
        if (parsed.sampler() != null && !parsed.sampler().isBlank()) {
            String name = parsed.sampler().trim();
            filters.add(new HardFilter("sampler", "打样员 " + name, hits -> {
                List<ImageSearchModels.ImageSearchHitView> next = new ArrayList<>();
                for (ImageSearchModels.ImageSearchHitView hit : hits) {
                    ImageSearchModels.GoodsBrief goods = samplerOf(hit);
                    if (goods != null && name.equals(text(goods.getSampler()))) {
                        next.add(hit);
                    }
                }
                return next;
            }));
        }
        if (parsed.album() != null && !parsed.album().isBlank()) {
            String album = parsed.album().trim();
            String albumLabel = album.endsWith("相册") || album.endsWith("分类") ? album : album + "相册";
            filters.add(new HardFilter("album", albumLabel, hits -> {
                List<ImageSearchModels.ImageSearchHitView> next = new ArrayList<>();
                for (ImageSearchModels.ImageSearchHitView hit : hits) {
                    if (albumMatches(hit.getAlbumName(), album)) {
                        next.add(hit);
                    }
                }
                return next;
            }));
        }
        return filters;
    }

    /**
     * 打样员写在商品上。素材只有唯一货号关联时，用那条关联上的打样员，避免同款素材被滤掉。
     */
    private static ImageSearchModels.GoodsBrief samplerOf(ImageSearchModels.ImageSearchHitView hit) {
        if (hit.getGoods() != null) {
            return hit.getGoods();
        }
        return SameProductGrouping.reliableLink(hit);
    }

    /**
     * 相册名包含用户说的全称可以留，例如库里「春季新品」、条件「新品」。
     * 反过来只有库里的名字够长、至少有条件一半长时才算，避免「衣」命中「滑雪服」。
     */
    static boolean albumMatches(String hitAlbum, String wanted) {
        String name = text(hitAlbum);
        String album = text(wanted);
        if (name.isEmpty() || album.isEmpty()) {
            return false;
        }
        if (name.equals(album) || name.contains(album)) {
            return true;
        }
        return name.length() >= 2 && name.length() * 2 >= album.length() && album.contains(name);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private record HardFilter(String id, String label, java.util.function.Function<List<ImageSearchModels.ImageSearchHitView>, List<ImageSearchModels.ImageSearchHitView>> body) {
        List<ImageSearchModels.ImageSearchHitView> apply(List<ImageSearchModels.ImageSearchHitView> hits) {
            return body.apply(hits);
        }
    }
}
