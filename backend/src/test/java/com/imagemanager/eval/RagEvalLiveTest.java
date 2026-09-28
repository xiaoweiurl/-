package com.imagemanager.eval;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实检索评测。默认 {@code mvn test} 通过 surefire excludedGroups=rag-eval 排除。
 */
@Tag("rag-eval")
@SpringBootTest
class RagEvalLiveTest {

    @Autowired
    private RagEvalService ragEvalService;

    @Test
    void runExampleSuiteAndWriteReports() throws Exception {
        var cases = ragEvalService.loadDefaultCases();
        assertFalse(cases.isEmpty());
        var summary = ragEvalService.run(cases);
        ragEvalService.writeReports(summary, Path.of("target/rag-eval"));
        String markdown = ragEvalService.toMarkdown(summary);
        assertTrue(markdown.contains("召回命中率"));
        assertTrue(markdown.contains("引用正确率"));
        assertTrue(markdown.contains("拒答正确率"));
        assertTrue(markdown.contains("P95"));
    }
}
