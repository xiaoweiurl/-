package com.imagemanager.eval;

import com.imagemanager.milvus.HybridLexicalTokenizer;
import com.imagemanager.milvus.RrfFusion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用评测打分器对比纯稠密和 RRF 混合检索。
 * 稠密通道按「丢掉字母数字货号之后的中文重叠」排序，模拟 bge-m3 对罕见货号分数不够。
 */
class RagHybridDenseCompareTest {

    private record Doc(String id, String content) {
    }

    @Test
    void hybridRecallsProductCodeThatDenseDrops() {
        List<Doc> corpus = List.of(
                new Doc("doc-25yk", "工艺单 货号25YK00022 面料编号C100-40S 克重120"),
                new Doc("doc-fabric", "棉质面料洗涤注意事项 色牢度 缩水率"),
                new Doc("doc-noise", "报价单 客户 交期 生产计划"));
        String question = "25YK00022 用的什么面料";

        List<Doc> denseRanked = rankDense(question, corpus);
        List<Doc> sparseRanked = rankSparse(question, corpus);
        List<RrfFusion.Fused<Doc>> fused = RrfFusion.fuse(denseRanked, sparseRanked, Doc::id, 60, 3);

        RagEvalCase codeCase = new RagEvalCase();
        codeCase.question = question;
        codeCase.huohao = "25YK00022";
        codeCase.expectedSourceIds = List.of("doc-25yk");
        codeCase.shouldRefuse = false;

        RagEvalCase refuseCase = new RagEvalCase();
        refuseCase.question = "并不存在的客户";
        refuseCase.shouldRefuse = true;
        refuseCase.keywords = List.of("并不存在的客户");

        List<RagEvalScorer.Hit> denseHits = toHits(denseRanked);
        List<RagEvalScorer.Hit> hybridHits = new ArrayList<>();
        for (RrfFusion.Fused<Doc> row : fused) {
            hybridHits.add(new RagEvalScorer.Hit(row.item().id(), row.item().content()));
        }

        RagEvalScorer.Summary dense = RagEvalScorer.summarize(List.of(
                RagEvalScorer.judge(codeCase, denseHits, 5),
                RagEvalScorer.judge(refuseCase, List.of(), 1)));
        RagEvalScorer.Summary hybrid = RagEvalScorer.summarize(List.of(
                RagEvalScorer.judge(codeCase, hybridHits, 5),
                RagEvalScorer.judge(refuseCase, List.of(), 1)));

        assertFalse(dense.cases().get(0).recallHit());
        assertTrue(hybrid.cases().get(0).recallHit());
        assertTrue(hybrid.cases().get(0).citationCorrect());
        assertTrue(dense.cases().get(1).refusalCorrect());
        assertTrue(hybrid.cases().get(1).refusalCorrect());
        assertTrue(hybrid.recallHitRate() > dense.recallHitRate());

        String markdown = RagRetrievalCompare.markdown(dense, hybrid);
        assertTrue(markdown.contains("纯稠密"));
        assertTrue(markdown.contains("混合检索"));
        assertTrue(markdown.contains("召回命中率"));
        assertTrue(markdown.contains("拒答正确率"));
    }

    /** 模拟稠密通道：正文里的货号不加分，并且含查询货号的切片被 0.35 阈值丢掉。 */
    private static List<Doc> rankDense(String query, List<Doc> corpus) {
        String queryCjk = query.replaceAll("[A-Za-z0-9._/\\-]+", "");
        List<String> codes = new ArrayList<>();
        for (String token : HybridLexicalTokenizer.tokens(query)) {
            if (HybridLexicalTokenizer.acceptCode(token)) {
                codes.add(token);
            }
        }
        List<Doc> ranked = new ArrayList<>();
        for (Doc doc : corpus) {
            String lower = doc.content().toLowerCase(java.util.Locale.ROOT);
            boolean droppedByCosine = false;
            for (String code : codes) {
                if (lower.contains(code.toLowerCase(java.util.Locale.ROOT))) {
                    droppedByCosine = true;
                    break;
                }
            }
            if (droppedByCosine) {
                continue;
            }
            String docCjk = doc.content().replaceAll("[A-Za-z0-9._/\\-]+", "");
            if (overlap(queryCjk, docCjk) > 0) {
                ranked.add(doc);
            }
        }
        ranked.sort(Comparator.comparingInt((Doc doc) -> overlap(queryCjk, doc.content())).reversed());
        return ranked;
    }

    private static List<Doc> rankSparse(String query, List<Doc> corpus) {
        Set<String> queryTokens = Set.copyOf(HybridLexicalTokenizer.tokens(query));
        List<Doc> ranked = new ArrayList<>();
        for (Doc doc : corpus) {
            long shared = HybridLexicalTokenizer.tokens(doc.content()).stream().filter(queryTokens::contains).count();
            if (shared > 0) {
                ranked.add(doc);
            }
        }
        ranked.sort(Comparator.comparingLong((Doc doc) -> HybridLexicalTokenizer.tokens(doc.content()).stream()
                .filter(queryTokens::contains).count()).reversed());
        return ranked;
    }

    private static int overlap(String left, String right) {
        int score = 0;
        for (int i = 0; i + 1 < left.length(); i++) {
            String bigram = left.substring(i, i + 2);
            if (bigram.isBlank()) {
                continue;
            }
            if (right.contains(bigram)) {
                score++;
            }
        }
        return score;
    }

    private static List<RagEvalScorer.Hit> toHits(List<Doc> docs) {
        List<RagEvalScorer.Hit> hits = new ArrayList<>();
        for (Doc doc : docs) {
            hits.add(new RagEvalScorer.Hit(doc.id(), doc.content()));
        }
        return hits;
    }
}
