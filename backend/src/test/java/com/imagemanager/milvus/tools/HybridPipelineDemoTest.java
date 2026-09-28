package com.imagemanager.milvus.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 连上 Milvus 2.5+ 才跑。默认 mvn test 不跑，避免在没有向量库的环境里失败。
 */
@EnabledIfEnvironmentVariable(named = "MILVUS_IT", matches = "true")
class HybridPipelineDemoTest {

    @Test
    void backfillAndCompareOnFakeData() throws Exception {
        String host = System.getenv().getOrDefault("MILVUS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("MILVUS_PORT", "19530"));
        Path report = Path.of("target", "hybrid-smoke-report.md");
        HybridPipelineDemo.Result result = HybridPipelineDemo.run(host, port, report);
        assertEquals(result.sourceRows(), result.shadowRows());
        assertTrue(result.hybrid().recallAt5() > result.dense().recallAt5());
        assertEquals(1.0, result.hybridCode().recallAt5(), 0.0001);
        assertEquals(0.0, result.denseCode().recallAt10(), 0.0001);
        assertEquals(1.0, result.hybridChinese().recallAt5(), 0.0001);
        String text = Files.readString(report);
        assertTrue(text.contains("Recall@5"));
        assertTrue(text.contains("25YK00022"));
        assertTrue(text.contains("hybrid_smoke_src"));
        assertTrue(!text.contains("salesperson_docs`"));
    }
}
