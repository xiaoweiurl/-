package com.imagemanager.milvus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridCompareEvalTest {

    @Test
    void samplesProductFabricModelAndChineseWithoutSplittingCodes() {
        List<HybridCompareEval.Question> questions = HybridCompareEval.sample(List.of(
                new HybridCompareEval.Chunk("doc-code", 3, "工艺单 货号25YK00022 采用棉氨面料"),
                new HybridCompareEval.Chunk("doc-fabric", 0, "面料编号C100-40S 用于夏季针织"),
                new HybridCompareEval.Chunk("doc-model", 1, "型号ZX9K2 机台参数说明"),
                new HybridCompareEval.Chunk("doc-zh", 0, "棉质面料需要低温洗涤避免缩水"),
                new HybridCompareEval.Chunk("doc-skip", 0, "附件说明.pdf 编码utf8")), 10, 5, 7);

        HybridCompareEval.Question product = questions.stream()
                .filter(q -> "product".equals(q.kind())).findFirst().orElseThrow();
        assertEquals("25YK00022", product.code());
        assertEquals("货号 25YK00022", product.text());
        assertEquals("doc-code", product.goldDocId());
        assertTrue(questions.stream().anyMatch(q -> "fabric".equals(q.kind()) && "C100-40S".equals(q.code())));
        assertTrue(questions.stream().anyMatch(q -> "model".equals(q.kind()) && "ZX9K2".equals(q.code())));
        assertTrue(questions.stream().anyMatch(q -> "chinese".equals(q.kind())
                && "doc-zh".equals(q.goldDocId())
                && q.text().startsWith("棉质面料")));
        assertTrue(questions.stream().noneMatch(q -> "utf8".equalsIgnoreCase(q.code()) || "pdf".equalsIgnoreCase(q.code())));
    }

    @Test
    void pinsProductCodeWhenTheSampleIsSmall() {
        List<HybridCompareEval.Chunk> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            chunks.add(new HybridCompareEval.Chunk("other-" + i, 0, "型号AB" + (100 + i)));
        }
        chunks.add(new HybridCompareEval.Chunk("gold", 2, "参见 25YK00022 工艺"));
        List<HybridCompareEval.Question> questions = HybridCompareEval.sample(chunks, 1, 0, 3);
        assertEquals(1, questions.size());
        assertEquals("25YK00022", questions.get(0).code());
    }

    @Test
    void codeHitDoesNotNeedTheSameChunkAndChineseHitDoes() {
        HybridCompareEval.Question code = new HybridCompareEval.Question(
                "product", "货号 25YK00022", "25YK00022", "gold", 2);
        HybridCompareEval.Question chinese = new HybridCompareEval.Question(
                "chinese", "棉质面料需要", null, "doc-zh", 0);
        List<HybridCompareEval.Hit> hits = List.of(
                new HybridCompareEval.Hit("other", 0, "无关会议纪要"),
                new HybridCompareEval.Hit("sibling", 9, "另一页也写了25yk00022"));
        assertEquals(2, HybridCompareEval.firstRank(code, hits));
        assertEquals(0, HybridCompareEval.firstRank(chinese, hits));

        List<HybridCompareEval.Trial> trials = List.of(new HybridCompareEval.Trial(
                code, hits, 2_000_000L));
        HybridCompareEval.Summary summary = HybridCompareEval.summarize("混合", "all", trials);
        assertEquals(1, summary.questions());
        assertEquals(1.0, summary.recallAt5(), 0.0001);
        assertEquals(0.5, summary.mrr(), 0.0001);
        assertEquals(2.0, summary.latencyMeanMs(), 0.001);

        String markdown = HybridCompareEval.markdown(
                "salesperson_docs", "salesperson_docs_hybrid", "test", "", trials, trials);
        assertTrue(markdown.contains("Recall@5"));
        assertTrue(markdown.contains("货号 25YK00022"));
        assertTrue(markdown.contains("不连接 Postgres"));
    }
}
