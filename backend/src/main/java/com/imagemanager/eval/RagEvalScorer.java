package com.imagemanager.eval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 评测指标只看检索结果，不调用模型。
 * <ul>
 *   <li>召回命中：非拒答题里，命中期望来源 id、货号、事实或关键词</li>
 *   <li>引用正确：给了 expectedSourceIds 时这些 id 全部出现在检索结果里；否则首条命中包含货号或关键词</li>
 *   <li>拒答正确：shouldRefuse 的题检索为空</li>
 * </ul>
 */
public final class RagEvalScorer {

    private RagEvalScorer() {
    }

    public record Hit(String recordId, String content) {
    }

    public record CaseResult(
            String question,
            boolean shouldRefuse,
            boolean recallHit,
            boolean citationCorrect,
            boolean refusalCorrect,
            boolean countedRecall,
            boolean countedCitation,
            boolean countedRefusal,
            long latencyMs,
            List<String> retrievedIds
    ) {
    }

    public record Summary(
            int total,
            double recallHitRate,
            int recallHits,
            int recallTotal,
            double citationAccuracy,
            int citationHits,
            int citationTotal,
            Double refusalAccuracy,
            int refusalHits,
            int refusalTotal,
            double avgLatencyMs,
            double p95LatencyMs,
            List<CaseResult> cases
    ) {
    }

    public static CaseResult judge(RagEvalCase evalCase, List<Hit> hits, long latencyMs) {
        List<Hit> safeHits = hits == null ? List.of() : hits;
        boolean empty = safeHits.isEmpty();
        boolean refuse = evalCase != null && evalCase.shouldRefuse;
        boolean recallCounted = evalCase != null && !refuse && hasRecallExpectation(evalCase);
        boolean citationCounted = evalCase != null && !refuse && hasCitationExpectation(evalCase);
        boolean recall = recallCounted && matchesRecall(evalCase, safeHits);
        boolean citation = citationCounted && matchesCitation(evalCase, safeHits);
        boolean refusalCounted = refuse;
        boolean refusalOk = refusalCounted && empty;
        List<String> ids = new ArrayList<>();
        for (Hit hit : safeHits) {
            if (hit.recordId() != null && !hit.recordId().isBlank()) {
                ids.add(hit.recordId());
            }
        }
        String question = evalCase == null ? "" : evalCase.question;
        return new CaseResult(question, refuse, recall, citation, refusalOk,
                recallCounted, citationCounted, refusalCounted, Math.max(0, latencyMs), ids);
    }

    public static Summary summarize(List<CaseResult> cases) {
        List<CaseResult> safe = cases == null ? List.of() : cases;
        int recallHits = 0;
        int recallTotal = 0;
        int citationHits = 0;
        int citationTotal = 0;
        int refusalHits = 0;
        int refusalTotal = 0;
        long latencySum = 0;
        List<Long> latencies = new ArrayList<>();
        for (CaseResult c : safe) {
            if (c.countedRecall()) {
                recallTotal++;
                if (c.recallHit()) {
                    recallHits++;
                }
            }
            if (c.countedCitation()) {
                citationTotal++;
                if (c.citationCorrect()) {
                    citationHits++;
                }
            }
            if (c.countedRefusal()) {
                refusalTotal++;
                if (c.refusalCorrect()) {
                    refusalHits++;
                }
            }
            latencySum += c.latencyMs();
            latencies.add(c.latencyMs());
        }
        double avg = safe.isEmpty() ? 0 : (double) latencySum / safe.size();
        return new Summary(
                safe.size(),
                rate(recallHits, recallTotal),
                recallHits,
                recallTotal,
                rate(citationHits, citationTotal),
                citationHits,
                citationTotal,
                refusalTotal == 0 ? null : rate(refusalHits, refusalTotal),
                refusalHits,
                refusalTotal,
                avg,
                percentile(latencies, 0.95),
                List.copyOf(safe)
        );
    }

    static boolean hasRecallExpectation(RagEvalCase evalCase) {
        return notEmpty(evalCase.expectedSourceIds)
                || notBlank(evalCase.huohao)
                || notEmpty(evalCase.expectedFacts)
                || notEmpty(evalCase.keywords);
    }

    static boolean hasCitationExpectation(RagEvalCase evalCase) {
        return notEmpty(evalCase.expectedSourceIds) || notBlank(evalCase.huohao) || notEmpty(evalCase.keywords);
    }

    private static boolean matchesRecall(RagEvalCase evalCase, List<Hit> hits) {
        if (notEmpty(evalCase.expectedSourceIds) && containsAnyId(hits, evalCase.expectedSourceIds)) {
            return true;
        }
        if (notBlank(evalCase.huohao) && containsText(hits, evalCase.huohao)) {
            return true;
        }
        if (notEmpty(evalCase.expectedFacts) && containsAnyText(hits, evalCase.expectedFacts)) {
            return true;
        }
        return notEmpty(evalCase.keywords) && containsAnyText(hits, evalCase.keywords);
    }

    private static boolean matchesCitation(RagEvalCase evalCase, List<Hit> hits) {
        if (hits.isEmpty()) {
            return false;
        }
        if (notEmpty(evalCase.expectedSourceIds)) {
            for (String expected : evalCase.expectedSourceIds) {
                if (!containsAnyId(hits, List.of(expected))) {
                    return false;
                }
            }
            return true;
        }
        Hit top = hits.get(0);
        if (notBlank(evalCase.huohao) && containsText(List.of(top), evalCase.huohao)) {
            return true;
        }
        return notEmpty(evalCase.keywords) && containsAnyText(List.of(top), evalCase.keywords);
    }

    private static boolean containsAnyId(List<Hit> hits, List<String> expectedIds) {
        for (String expected : expectedIds) {
            if (expected == null || expected.isBlank()) {
                continue;
            }
            String needle = expected.trim().toLowerCase(Locale.ROOT);
            for (Hit hit : hits) {
                if (hit.recordId() != null && hit.recordId().toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
                if (hit.content() != null && hit.content().toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsAnyText(List<Hit> hits, List<String> needles) {
        for (String needle : needles) {
            if (containsText(hits, needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsText(List<Hit> hits, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        String n = needle.trim().toLowerCase(Locale.ROOT);
        for (Hit hit : hits) {
            String blob = ((hit.recordId() == null ? "" : hit.recordId()) + "\n"
                    + (hit.content() == null ? "" : hit.content())).toLowerCase(Locale.ROOT);
            if (blob.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static double rate(int hit, int total) {
        if (total <= 0) {
            return 0;
        }
        return (double) hit / total;
    }

    static double percentile(List<Long> values, double p) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sorted.size()) {
            index = sorted.size() - 1;
        }
        return sorted.get(index);
    }

    private static boolean notEmpty(List<String> values) {
        return values != null && values.stream().anyMatch(v -> v != null && !v.isBlank());
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
