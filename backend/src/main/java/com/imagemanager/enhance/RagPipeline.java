package com.imagemanager.enhance;

import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.service.KnowledgeBaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * RAG增强流水线：整合查询增强 + 多路向量并行召回 + 去重 + Reranker重排序。
 * 对应PDF教程中的完整RAG Pipeline。
 *
 * 流程：
 * 1. QueryEnhancer 生成原始查询的3-5个变体
 * 2. 对每个变体并行执行向量检索（CompletableFuture + 1秒超时熔断）
 * 3. 合并所有结果，按chunk_text去重
 * 4. Reranker 对去重后的结果做二次打分排序
 * 5. 返回Top-N最相关的文档片段
 */
@Slf4j
@Component
public class RagPipeline {

    @Autowired
    private QueryEnhancer queryEnhancer;

    @Autowired
    private Reranker reranker;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    /** 并行检索线程池 */
    private final ExecutorService retrievalExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "rag-retrieval");
        t.setDaemon(true);
        return t;
    });

    /** 单路向量检索超时时间（毫秒） */
    private static final long RETRIEVAL_TIMEOUT_MS = 1000;

    /** 向量召回粗筛阈值（bge-m3 COSINE）：低于此分的切片直接不召回 */
    private static final float RECALL_MIN_SCORE = 0.30f;

    /**
     * Rerank 后注入上下文的相关性阈值（bge-reranker-v2-m3 normalize 分数）：
     * >0.5 通常相关，0.3-0.5 弱相关，<0.4 视为不相关。
     * 低分切片注入上下文会引发幻觉（如问"模式切换"却给货号数据），必须过滤。
     */
    private static final double RERANK_MIN_SCORE = 0.40;

    /**
     * 完整RAG增强检索
     *
     * @param query 用户原始问题
     * @param company 租户
     * @param topK 最终返回的文档数量
     * @return 重排序后的Top-K文档片段
     */
    public List<MemorySearchResult> enhancedSearch(String query, String company, int topK) {
        // ========== Step 1: 查询增强 ==========
        List<String> enhancedQueries = queryEnhancer.enhance(query);

        // ========== Step 2: 多路向量并行召回 ==========
        // ensure原始查询在列表首位
        if (!enhancedQueries.contains(query)) {
            enhancedQueries.add(0, query);
        }

        List<CompletableFuture<List<MemorySearchResult>>> futures = new ArrayList<>();
        for (String q : enhancedQueries) {
            CompletableFuture<List<MemorySearchResult>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    List<MemorySearchResult> results = knowledgeBaseService.search(q, RECALL_MIN_SCORE, 10, company);
                    return results != null ? results : Collections.<MemorySearchResult>emptyList();
                } catch (Exception e) {
                    log.warn("[RagPipeline] 查询'{}' 检索失败: {}", q, e.getMessage());
                    return Collections.<MemorySearchResult>emptyList();
                }
            }, retrievalExecutor);

            // 1秒超时熔断：超时返回空列表，不阻塞其他路
            futures.add(future.orTimeout(RETRIEVAL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> {
                        log.warn("[RagPipeline] 查询'{}' 超时熔断({}ms)", q, RETRIEVAL_TIMEOUT_MS);
                        return Collections.emptyList();
                    }));
        }

        // 等待所有路召回完成（整体最多 1.5 秒）
        List<MemorySearchResult> allResults = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(1500, TimeUnit.MILLISECONDS);
        } catch (@SuppressWarnings("unused") TimeoutException e) {
            log.warn("[RagPipeline] 整体召回超时(1.5s)，使用已返回的结果");
        } catch (Exception e) {
            log.warn("[RagPipeline] 并行召回异常: {}", e.getMessage());
        }

        for (CompletableFuture<List<MemorySearchResult>> f : futures) {
            if (f.isDone() && !f.isCompletedExceptionally()) {
                try {
                    List<MemorySearchResult> r = f.getNow(Collections.emptyList());
                    allResults.addAll(r);
                } catch (@SuppressWarnings("unused") Exception ignored) {}
            }
        }

        if (allResults.isEmpty()) {
            // 降级：略降阈值重试（仍保持合理下限，避免召回垃圾切片）
            try {
                allResults = knowledgeBaseService.search(query, 0.25f, 15, company);
                if (allResults == null) allResults = new ArrayList<>();
            } catch (Exception e) {
                log.warn("[RagPipeline] 降级检索也失败: {}", e.getMessage());
            }
        }

        if (allResults.isEmpty()) {
            return Collections.emptyList();
        }

        // ========== Step 3: 去重 ==========
        List<MemorySearchResult> deduped = dedupByContent(allResults);

        // ========== Step 4: Reranker重排序 ==========
        List<MemorySearchResult> reranked = reranker.rerank(query, deduped, topK);

        // ========== Step 5: 相关性过滤 ==========
        // rerank normalize 分数 < RERANK_MIN_SCORE 的切片视为不相关，直接丢弃：
        // 低分切片注入上下文会引发幻觉并浪费 token（如指令性输入召回到货号数据）
        List<MemorySearchResult> filtered = new ArrayList<>();
        for (MemorySearchResult r : reranked) {
            double s = r.getScore() != null ? r.getScore() : 0;
            if (s >= RERANK_MIN_SCORE) {
                filtered.add(r);
            }
        }

        return filtered;
    }

    /**
     * 增强检索（重载，返回Map格式，兼容现有SmartChatServiceImpl）
     * @param query 用户原始问题
     * @param company 公司标识
     * @return 重排序后的文档列表，每条包含content/score/source字段
     */
    public List<Map<String, Object>> enhancedSearchAsMap(String query, String company) {
        List<MemorySearchResult> results = enhancedSearch(query, company, 5);
        List<Map<String, Object>> output = new ArrayList<>();
        for (MemorySearchResult r : results) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("content", r.getContent());
            item.put("score", r.getScore());
            item.put("source", r.getSource() != null ? r.getSource() : "知识库");
            output.add(item);
        }
        return output;
    }

    /**
     * 按内容去重（截取前200字符作为指纹）
     */
    private List<MemorySearchResult> dedupByContent(List<MemorySearchResult> results) {
        Map<String, MemorySearchResult> seen = new LinkedHashMap<>();
        for (MemorySearchResult r : results) {
            String content = r.getContent();
            if (content == null) continue;
            String fingerprint = content.length() > 200 ? content.substring(0, 200) : content;
            // 保留相似度更高的那个
            double rScore = r.getScore() != null ? r.getScore() : 0;
            double existScore = seen.containsKey(fingerprint) && seen.get(fingerprint).getScore() != null ? seen.get(fingerprint).getScore() : 0;
            if (!seen.containsKey(fingerprint) || rScore > existScore) {
                seen.put(fingerprint, r);
            }
        }
        return new ArrayList<>(seen.values());
    }
}
