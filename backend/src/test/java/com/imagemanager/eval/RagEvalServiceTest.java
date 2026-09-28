package com.imagemanager.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.enhance.QueryEnhancer;
import com.imagemanager.enhance.RagPipeline;
import com.imagemanager.enhance.Reranker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagEvalServiceTest {

    @Test
    void retrieveCallsEnhancerPipelineAndReranker() {
        QueryEnhancer enhancer = mock(QueryEnhancer.class);
        RagPipeline pipeline = mock(RagPipeline.class);
        Reranker reranker = mock(Reranker.class);
        MemorySearchResult hit = MemorySearchResult.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .recordKey("example-doc-ex0001")
                .content("EXAMPLE克重 120")
                .score(0.9)
                .build();
        when(enhancer.enhance(any())).thenReturn(List.of("EXAMPLE 货号 EX0001", "变体"));
        when(pipeline.enhancedSearch(any(), eq("EXAMPLE"), anyInt())).thenReturn(List.of(hit));
        when(reranker.rerank(any(), any(), anyInt())).thenReturn(List.of(hit));

        RagEvalService service = new RagEvalService(enhancer, pipeline, reranker, new ObjectMapper());
        List<RagEvalScorer.Hit> hits = service.retrieve("EXAMPLE 货号 EX0001 的工艺克重是多少？", "EXAMPLE");

        verify(enhancer).enhance("EXAMPLE 货号 EX0001 的工艺克重是多少？");
        verify(pipeline).enhancedSearch(eq("EXAMPLE 货号 EX0001"), eq("EXAMPLE"), eq(8));
        verify(reranker).rerank(eq("EXAMPLE 货号 EX0001 的工艺克重是多少？"), any(), eq(5));
        assertEquals("example-doc-ex0001", hits.get(0).recordId());
    }

    @Test
    void shippedExamplesAreMarkedExample() throws Exception {
        List<RagEvalCase> cases = RagEvalService.readJsonl(
                RagEvalServiceTest.class.getResourceAsStream("/rag-eval/example.jsonl"));
        assertFalse(cases.isEmpty());
        for (RagEvalCase evalCase : cases) {
            assertTrue(evalCase.example);
            assertTrue(evalCase.question.contains("EXAMPLE"));
            assertEquals("EXAMPLE", evalCase.company);
        }
    }

    @Test
    void markdownReportNamesTheMetrics() {
        RagEvalService service = new RagEvalService(
                mock(QueryEnhancer.class), mock(RagPipeline.class), mock(Reranker.class), new ObjectMapper());
        String markdown = service.toMarkdown(RagEvalScorer.summarize(List.of()));
        assertTrue(markdown.contains("召回命中率"));
        assertTrue(markdown.contains("引用正确率"));
        assertTrue(markdown.contains("拒答正确率"));
        assertTrue(markdown.contains("P95"));
    }
}
