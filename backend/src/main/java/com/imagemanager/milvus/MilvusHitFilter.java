package com.imagemanager.milvus;

/**
 * 稠密检索继续用余弦阈值；RRF / Weighted / BM25 的分数不是余弦，不能再用 0.35 砍掉。
 */
public final class MilvusHitFilter {

    private MilvusHitFilter() {
    }

    public static boolean isFusionMetric(String scoreMetric) {
        return "rrf".equals(scoreMetric) || "weighted".equals(scoreMetric) || "bm25".equals(scoreMetric);
    }

    public static boolean keep(float score, String scoreMetric, double cosineMinScore) {
        if (isFusionMetric(scoreMetric)) {
            return score > 0f;
        }
        return score >= cosineMinScore;
    }
}
