package com.imagemanager.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.enhance.QueryEnhancer;
import com.imagemanager.enhance.RagPipeline;
import com.imagemanager.enhance.Reranker;
import com.imagemanager.util.KeywordExtractor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 用真实检索链路评测：KeywordExtractor、QueryEnhancer、RagPipeline、Reranker。
 * 默认构建不跑这套（见 JUnit tag rag-eval）；管理端可以触发同一套。
 */
@Service
public class RagEvalService {

    private final QueryEnhancer queryEnhancer;
    private final RagPipeline ragPipeline;
    private final Reranker reranker;
    private final ObjectMapper objectMapper;

    public RagEvalService(QueryEnhancer queryEnhancer,
                          RagPipeline ragPipeline,
                          Reranker reranker,
                          ObjectMapper objectMapper) {
        this.queryEnhancer = queryEnhancer;
        this.ragPipeline = ragPipeline;
        this.reranker = reranker;
        this.objectMapper = objectMapper;
    }

    public List<RagEvalScorer.Hit> retrieve(String question, String company) {
        String q = question == null ? "" : question;
        String tenant = company == null || company.isBlank() ? "" : company;
        KeywordExtractor.extractKeywords(q);
        KeywordExtractor.extractProductCode(q);
        List<String> variants;
        try {
            variants = queryEnhancer.enhance(q);
        } catch (RuntimeException ex) {
            variants = List.of(q);
        }
        String searchQuery = (variants != null && !variants.isEmpty() && variants.get(0) != null)
                ? variants.get(0) : q;
        List<MemorySearchResult> recalled;
        try {
            recalled = ragPipeline.enhancedSearch(searchQuery, tenant, 8);
        } catch (RuntimeException ex) {
            recalled = List.of();
        }
        if (recalled == null) {
            recalled = List.of();
        }
        List<MemorySearchResult> reranked;
        try {
            reranked = reranker.rerank(q, recalled, 5);
        } catch (RuntimeException ex) {
            reranked = recalled;
        }
        if (reranked == null || reranked.isEmpty()) {
            reranked = recalled;
        }
        List<RagEvalScorer.Hit> hits = new ArrayList<>();
        for (MemorySearchResult result : reranked) {
            hits.add(new RagEvalScorer.Hit(recordId(result), result.getContent()));
        }
        return hits;
    }

    public RagEvalScorer.Summary run(List<RagEvalCase> cases) {
        List<RagEvalScorer.CaseResult> results = new ArrayList<>();
        if (cases != null) {
            for (RagEvalCase evalCase : cases) {
                long start = System.nanoTime();
                List<RagEvalScorer.Hit> hits = retrieve(
                        evalCase.question,
                        evalCase.company == null ? "" : evalCase.company);
                long latencyMs = (System.nanoTime() - start) / 1_000_000L;
                results.add(RagEvalScorer.judge(evalCase, hits, latencyMs));
            }
        }
        return RagEvalScorer.summarize(results);
    }

    public RagEvalScorer.Summary runDefault() throws IOException {
        return run(loadDefaultCases());
    }

    public List<RagEvalCase> loadDefaultCases() throws IOException {
        for (String candidate : List.of(
                "src/test/resources/rag-eval",
                "backend/src/test/resources/rag-eval")) {
            Path dir = Path.of(candidate);
            if (Files.isDirectory(dir)) {
                List<RagEvalCase> fromDisk = loadDirectory(dir);
                if (!fromDisk.isEmpty()) {
                    return fromDisk;
                }
            }
        }
        return loadClasspath("rag-eval");
    }

    public List<RagEvalCase> loadDirectory(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        List<RagEvalCase> cases = new ArrayList<>();
        try (Stream<Path> paths = Files.list(dir)) {
            List<Path> files = paths.filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path file : files) {
                try (InputStream in = Files.newInputStream(file)) {
                    cases.addAll(readJsonl(in, objectMapper));
                }
            }
        }
        return cases;
    }

    public List<RagEvalCase> loadClasspath(String folder) throws IOException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = RagEvalService.class.getClassLoader();
        }
        String name = folder.endsWith("/") ? folder + "example.jsonl" : folder + "/example.jsonl";
        try (InputStream in = loader.getResourceAsStream(name)) {
            if (in == null) {
                return List.of();
            }
            return readJsonl(in, objectMapper);
        }
    }

    public static List<RagEvalCase> readJsonl(InputStream in) throws IOException {
        return readJsonl(in, new ObjectMapper());
    }

    public static List<RagEvalCase> readJsonl(InputStream in, ObjectMapper mapper) throws IOException {
        List<RagEvalCase> cases = new ArrayList<>();
        if (in == null) {
            return cases;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                cases.add(mapper.readValue(trimmed, RagEvalCase.class));
            }
        }
        return cases;
    }

    public String toMarkdown(RagEvalScorer.Summary summary) {
        String refusal = summary.refusalAccuracy() == null
                ? "无拒答样本"
                : percent(summary.refusalAccuracy());
        StringBuilder md = new StringBuilder();
        md.append("# RAG 评测报告\n\n");
        md.append("| 指标 | 结果 |\n| --- | --- |\n");
        md.append("| 题数 | ").append(summary.total()).append(" |\n");
        md.append("| 召回命中率 | ").append(percent(summary.recallHitRate()))
                .append(" (").append(summary.recallHits()).append("/").append(summary.recallTotal()).append(") |\n");
        md.append("| 引用正确率 | ").append(percent(summary.citationAccuracy()))
                .append(" (").append(summary.citationHits()).append("/").append(summary.citationTotal()).append(") |\n");
        md.append("| 拒答正确率 | ").append(refusal)
                .append(" (").append(summary.refusalHits()).append("/").append(summary.refusalTotal()).append(") |\n");
        md.append("| 平均延迟 | ").append(Math.round(summary.avgLatencyMs())).append(" ms |\n");
        md.append("| P95 延迟 | ").append(Math.round(summary.p95LatencyMs())).append(" ms |\n\n");
        md.append("召回命中只统计带了期望来源、货号、事实或关键词的非拒答题。");
        md.append("引用正确：期望来源 id 需全部出现；只给了货号或关键词时，看首条结果是否包含它们。");
        md.append("拒答正确：shouldRefuse 的题检索结果为空。\n\n");
        md.append("## 逐题\n\n");
        for (RagEvalScorer.CaseResult item : summary.cases()) {
            md.append("- ").append(item.question()).append("\n");
            md.append("  - 召回=").append(flag(item.countedRecall(), item.recallHit()));
            md.append(" 引用=").append(flag(item.countedCitation(), item.citationCorrect()));
            md.append(" 拒答=").append(flag(item.countedRefusal(), item.refusalCorrect()));
            md.append(" 延迟=").append(item.latencyMs()).append("ms");
            md.append(" 来源=").append(item.retrievedIds()).append("\n");
        }
        return md.toString();
    }

    public String toJson(RagEvalScorer.Summary summary) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total", summary.total());
        body.put("recallHitRate", summary.recallHitRate());
        body.put("recallHits", summary.recallHits());
        body.put("recallTotal", summary.recallTotal());
        body.put("citationAccuracy", summary.citationAccuracy());
        body.put("citationHits", summary.citationHits());
        body.put("citationTotal", summary.citationTotal());
        body.put("refusalAccuracy", summary.refusalAccuracy());
        body.put("refusalHits", summary.refusalHits());
        body.put("refusalTotal", summary.refusalTotal());
        body.put("avgLatencyMs", summary.avgLatencyMs());
        body.put("p95LatencyMs", summary.p95LatencyMs());
        body.put("cases", summary.cases());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
    }

    public void writeReports(RagEvalScorer.Summary summary, Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("report.md"), toMarkdown(summary), StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("report.json"), toJson(summary), StandardCharsets.UTF_8);
    }

    private static String recordId(MemorySearchResult result) {
        if (result == null) {
            return "";
        }
        if (result.getRecordKey() != null && !result.getRecordKey().isBlank()) {
            return result.getRecordKey();
        }
        if (result.getSourceDocId() != null) {
            return result.getSourceDocId().toString();
        }
        if (result.getId() != null) {
            return result.getId().toString();
        }
        return "";
    }

    private static String percent(double rate) {
        return String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }

    private static String flag(boolean counted, boolean ok) {
        if (!counted) {
            return "不计";
        }
        return ok ? "对" : "错";
    }
}
