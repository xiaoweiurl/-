package com.imagemanager.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagEvalScorerTest {

    @Test
    void recallAndCitationCountExpectedSourceIds() {
        RagEvalCase evalCase = new RagEvalCase();
        evalCase.question = "EXAMPLE";
        evalCase.expectedSourceIds = List.of("example-doc-ex0001", "example-kb-dye");
        evalCase.shouldRefuse = false;

        RagEvalScorer.CaseResult partial = RagEvalScorer.judge(evalCase, List.of(
                new RagEvalScorer.Hit("example-doc-ex0001", "EXAMPLE克重")), 10);
        assertTrue(partial.recallHit());
        assertFalse(partial.citationCorrect());

        RagEvalScorer.CaseResult full = RagEvalScorer.judge(evalCase, List.of(
                new RagEvalScorer.Hit("example-doc-ex0001", "a"),
                new RagEvalScorer.Hit("example-kb-dye", "b")), 20);
        assertTrue(full.citationCorrect());
    }

    @Test
    void huohaoInTopHitCountsAsCitationWhenNoSourceIds() {
        RagEvalCase evalCase = new RagEvalCase();
        evalCase.question = "EXAMPLE 货号 EX0001";
        evalCase.huohao = "EX0001";
        evalCase.keywords = List.of("EXAMPLE");
        RagEvalScorer.CaseResult result = RagEvalScorer.judge(evalCase, List.of(
                new RagEvalScorer.Hit("other", "正文没有货号"),
                new RagEvalScorer.Hit("later", "EX0001 在第二条")), 5);
        assertTrue(result.recallHit());
        assertFalse(result.citationCorrect());
    }

    @Test
    void refusalIsCorrectOnlyWhenRetrievalIsEmpty() {
        RagEvalCase evalCase = new RagEvalCase();
        evalCase.shouldRefuse = true;
        evalCase.keywords = List.of("EXAMPLE无此客户");

        RagEvalScorer.CaseResult correct = RagEvalScorer.judge(evalCase, List.of(), 3);
        assertTrue(correct.refusalCorrect());
        assertFalse(correct.countedRecall());

        RagEvalScorer.CaseResult wrong = RagEvalScorer.judge(evalCase, List.of(
                new RagEvalScorer.Hit("x", "EXAMPLE无此客户 采购额 1")), 3);
        assertFalse(wrong.refusalCorrect());
    }

    @Test
    void summaryComputesRatesAndP95() {
        RagEvalScorer.Summary summary = RagEvalScorer.summarize(List.of(
                result(true, true, false, false, 10),
                result(true, false, false, false, 30),
                result(false, false, true, true, 100),
                result(false, false, true, false, 40)
        ));
        assertEquals(0.5, summary.recallHitRate());
        assertEquals(2, summary.recallTotal());
        assertEquals(0.5, summary.citationAccuracy());
        assertEquals(2, summary.citationTotal());
        assertEquals(0.5, summary.refusalAccuracy());
        assertEquals(45.0, summary.avgLatencyMs());
        assertEquals(100.0, summary.p95LatencyMs());
    }

    @Test
    void refusalRateIsNullWithoutRefuseCases() {
        RagEvalScorer.Summary summary = RagEvalScorer.summarize(List.of(
                result(true, true, false, false, 1)
        ));
        assertNull(summary.refusalAccuracy());
        assertEquals(0, summary.refusalTotal());
    }

    private static RagEvalScorer.CaseResult result(boolean recallCounted, boolean recall,
                                                    boolean refusalCounted, boolean refusal,
                                                    long latency) {
        return new RagEvalScorer.CaseResult(
                "q", refusalCounted, recall, recall, refusal,
                recallCounted, recallCounted, refusalCounted, latency, List.of());
    }
}
