package com.imagemanager.milvus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reciprocal Rank Fusion。公式与 Milvus {@code RRFRanker} 一致：
 * {@code score = Σ 1 / (k + rank)}，rank 从 1 起算，默认 k=60。
 *
 * <p>稠密余弦分和 BM25 分不在一个尺度上。WeightedRanker 要把两边先归一再配权重，
 * 没有评测集时很容易让其中一路淹没另一路。RRF 只看名次：货号在稀疏通道排第 1、
 * 在稠密通道很靠后时，仍然能压过「只在稠密通道中游」的切片。k=60 是 Milvus 和
 * 原始 RRF 论文的默认值，不需要再标一轮权重。
 */
public final class RrfFusion {

    public static final int DEFAULT_K = 60;

    public record Fused<T>(T item, String id, double score, int denseRank, int sparseRank) {
    }

    private RrfFusion() {
    }

    public static <T> List<Fused<T>> fuse(List<T> denseRanked,
                                          List<T> sparseRanked,
                                          Function<T, String> idFn,
                                          int k,
                                          int limit) {
        int rankConstant = k > 0 ? k : DEFAULT_K;
        Map<String, Acc<T>> byId = new LinkedHashMap<>();
        accumulate(byId, denseRanked, idFn, rankConstant, true);
        accumulate(byId, sparseRanked, idFn, rankConstant, false);
        List<Fused<T>> fused = new ArrayList<>();
        for (Acc<T> acc : byId.values()) {
            fused.add(new Fused<>(acc.item, acc.id, acc.score, acc.denseRank, acc.sparseRank));
        }
        fused.sort(Comparator
                .comparingDouble((Fused<T> row) -> row.score).reversed()
                .thenComparingInt(row -> row.denseRank == 0 ? Integer.MAX_VALUE : row.denseRank)
                .thenComparing(row -> row.id == null ? "" : row.id));
        if (limit > 0 && fused.size() > limit) {
            return List.copyOf(fused.subList(0, limit));
        }
        return List.copyOf(fused);
    }

    private static <T> void accumulate(Map<String, Acc<T>> byId,
                                       List<T> ranked,
                                       Function<T, String> idFn,
                                       int k,
                                       boolean dense) {
        if (ranked == null) {
            return;
        }
        int rank = 0;
        for (T item : ranked) {
            if (item == null) {
                continue;
            }
            rank++;
            String id = idFn == null ? String.valueOf(item) : idFn.apply(item);
            if (id == null || id.isBlank()) {
                id = "#" + rank + (dense ? "d" : "s");
            }
            Acc<T> acc = byId.get(id);
            if (acc == null) {
                acc = new Acc<>();
                acc.id = id;
                acc.item = item;
                byId.put(id, acc);
            }
            acc.score += 1.0d / (k + rank);
            if (dense) {
                if (acc.denseRank == 0) {
                    acc.denseRank = rank;
                }
            } else if (acc.sparseRank == 0) {
                acc.sparseRank = rank;
            }
        }
    }

    private static final class Acc<T> {
        String id;
        T item;
        double score;
        int denseRank;
        int sparseRank;
    }
}
