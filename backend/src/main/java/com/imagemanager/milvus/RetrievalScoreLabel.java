package com.imagemanager.milvus;

import java.util.Locale;

/**
 * 提示词里的相关度文案。稠密余弦仍是「相关度: 35.0%」；融合分不是 0-1 相似度，不能再乘 100。
 */
public final class RetrievalScoreLabel {

    private RetrievalScoreLabel() {
    }

    public static String format(double score, String scoreMetric) {
        if (MilvusHitFilter.isFusionMetric(scoreMetric)) {
            return String.format(Locale.ROOT, "混合检索%s=%.4f", scoreMetric, score);
        }
        return String.format(Locale.ROOT, "相关度: %.1f%%", score * 100);
    }
}
