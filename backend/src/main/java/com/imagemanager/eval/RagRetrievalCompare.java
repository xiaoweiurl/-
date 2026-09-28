package com.imagemanager.eval;

import java.util.Locale;

/**
 * 同一套 {@link RagEvalScorer} 指标下，对比纯稠密检索和混合检索。
 */
public final class RagRetrievalCompare {

    private RagRetrievalCompare() {
    }

    public static String markdown(RagEvalScorer.Summary dense, RagEvalScorer.Summary hybrid) {
        StringBuilder md = new StringBuilder();
        md.append("# 混合检索 vs 纯稠密\n\n");
        md.append("融合排序是 RRF（k=60）。稠密列是只按向量名次召回的结果；混合列是稠密名次和稀疏名次融合后的结果。\n\n");
        md.append("| 指标 | 纯稠密 | 混合检索 |\n| --- | --- | --- |\n");
        md.append("| 题数 | ").append(safeTotal(dense)).append(" | ").append(safeTotal(hybrid)).append(" |\n");
        md.append("| 召回命中率 | ").append(rate(dense)).append(" | ").append(rate(hybrid)).append(" |\n");
        md.append("| 引用正确率 | ").append(citation(dense)).append(" | ").append(citation(hybrid)).append(" |\n");
        md.append("| 拒答正确率 | ").append(refusal(dense)).append(" | ").append(refusal(hybrid)).append(" |\n");
        return md.toString();
    }

    private static int safeTotal(RagEvalScorer.Summary summary) {
        return summary == null ? 0 : summary.total();
    }

    private static String rate(RagEvalScorer.Summary summary) {
        if (summary == null) {
            return "无";
        }
        return percent(summary.recallHitRate()) + " (" + summary.recallHits() + "/" + summary.recallTotal() + ")";
    }

    private static String citation(RagEvalScorer.Summary summary) {
        if (summary == null) {
            return "无";
        }
        return percent(summary.citationAccuracy()) + " (" + summary.citationHits() + "/" + summary.citationTotal() + ")";
    }

    private static String refusal(RagEvalScorer.Summary summary) {
        if (summary == null || summary.refusalAccuracy() == null) {
            return "无拒答样本";
        }
        return percent(summary.refusalAccuracy()) + " (" + summary.refusalHits() + "/" + summary.refusalTotal() + ")";
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100);
    }
}
