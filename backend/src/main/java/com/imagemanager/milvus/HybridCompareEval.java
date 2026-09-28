package com.imagemanager.milvus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 纯稠密和混合检索的对比指标。不连接 Milvus，也不写 Postgres。
 * 货号类问题：命中正文包含该编号即算召回（同一编号可能出现在多个切片）。
 * 中文问题：必须命中抽样时的那一条 doc_id + chunk_index。
 */
public final class HybridCompareEval {

    public static final String PINNED_CODE = "25YK00022";

    public record Chunk(String docId, int chunkIndex, String content) {
    }

    public record Question(String kind, String text, String code, String goldDocId, int goldChunkIndex) {
    }

    public record Hit(String docId, int chunkIndex, String content) {
    }

    public record Trial(Question question, List<Hit> hits, long latencyNanos) {
    }

    public record Summary(String method, String kind, int questions,
                          double recallAt5, double recallAt10, double mrr,
                          double latencyMeanMs, double latencyP50Ms, double latencyP95Ms) {
    }

    private HybridCompareEval() {
    }

    public static String codeKind(String code) {
        if (code.indexOf('-') >= 0 || code.indexOf('/') >= 0 || code.indexOf('_') >= 0 || code.indexOf('.') >= 0) {
            return "fabric";
        }
        if (code.matches("\\d{2,}[A-Za-z].*")) {
            return "product";
        }
        return "model";
    }

    public static String questionText(String kind, String code) {
        return switch (kind) {
            case "product" -> "货号 " + code;
            case "fabric" -> "面料编号 " + code;
            case "model" -> "型号 " + code;
            default -> code;
        };
    }

    public static String kindLabel(String kind) {
        return switch (kind) {
            case "product" -> "货号";
            case "fabric" -> "面料编号";
            case "model" -> "型号";
            case "chinese" -> "中文";
            case "all" -> "全部";
            default -> kind;
        };
    }

    public static List<Question> sample(List<Chunk> chunks, int codeLimit, int chineseLimit, long seed) {
        List<Question> codes = new ArrayList<>();
        List<Question> chinese = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        Question pinned = null;
        for (Chunk chunk : chunks) {
            if (chunk == null || chunk.content == null || chunk.content.isBlank()) {
                continue;
            }
            List<String> surfaces = HybridLexicalTokenizer.surfaceCodes(chunk.content);
            boolean asked = false;
            for (String code : surfaces) {
                if (!HybridLexicalTokenizer.isQuestionCode(code)) {
                    continue;
                }
                asked = true;
                if (!seen.add(code.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String kind = codeKind(code);
                Question question = new Question(kind, questionText(kind, code), code, chunk.docId, chunk.chunkIndex);
                codes.add(question);
                if (PINNED_CODE.equalsIgnoreCase(code)) {
                    pinned = question;
                }
            }
            if (!asked) {
                String phrase = chinesePhrase(chunk.content);
                if (phrase != null) {
                    chinese.add(new Question("chinese", phrase, null, chunk.docId, chunk.chunkIndex));
                }
            }
        }
        Collections.shuffle(codes, new Random(seed));
        Collections.shuffle(chinese, new Random(seed + 1));
        if (pinned != null) {
            codes.remove(pinned);
            codes.add(0, pinned);
        }
        List<Question> out = new ArrayList<>();
        int codesToTake = Math.max(0, codeLimit);
        int chineseToTake = Math.max(0, chineseLimit);
        out.addAll(codes.subList(0, Math.min(codesToTake, codes.size())));
        out.addAll(chinese.subList(0, Math.min(chineseToTake, chinese.size())));
        return List.copyOf(out);
    }

    public static boolean relevant(Question question, Hit hit) {
        if (question == null || hit == null || hit.content == null) {
            return false;
        }
        if ("chinese".equals(question.kind)) {
            return question.goldDocId.equals(hit.docId) && question.goldChunkIndex == hit.chunkIndex;
        }
        if (question.code == null) {
            return false;
        }
        return hit.content.toLowerCase(Locale.ROOT).contains(question.code.toLowerCase(Locale.ROOT));
    }

    public static int firstRank(Question question, List<Hit> hits) {
        if (hits == null) {
            return 0;
        }
        for (int i = 0; i < hits.size(); i++) {
            if (relevant(question, hits.get(i))) {
                return i + 1;
            }
        }
        return 0;
    }

    public static Summary summarize(String method, String kind, List<Trial> trials) {
        List<Trial> selected = new ArrayList<>();
        for (Trial trial : trials) {
            if ("all".equals(kind) || kind.equals(trial.question.kind)) {
                selected.add(trial);
            }
        }
        int n = selected.size();
        if (n == 0) {
            return new Summary(method, kind, 0, 0, 0, 0, 0, 0, 0);
        }
        int hit5 = 0;
        int hit10 = 0;
        double rr = 0;
        long[] latency = new long[n];
        long latencySum = 0;
        for (int i = 0; i < n; i++) {
            Trial trial = selected.get(i);
            int rank = firstRank(trial.question, trial.hits);
            if (rank > 0 && rank <= 5) {
                hit5++;
            }
            if (rank > 0 && rank <= 10) {
                hit10++;
            }
            if (rank > 0) {
                rr += 1.0 / rank;
            }
            latency[i] = trial.latencyNanos;
            latencySum += trial.latencyNanos;
        }
        java.util.Arrays.sort(latency);
        return new Summary(
                method,
                kind,
                n,
                hit5 / (double) n,
                hit10 / (double) n,
                rr / n,
                (latencySum / (double) n) / 1_000_000.0,
                percentile(latency, 0.50),
                percentile(latency, 0.95));
    }

    public static String markdown(String source, String target, String embedMode, String note,
                                  List<Trial> dense, List<Trial> hybrid) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 稠密检索与混合检索对比\n\n");
        sb.append("- 原集合（只读）：`").append(source).append("`\n");
        sb.append("- 影子集合（只读）：`").append(target).append("`\n");
        sb.append("- 查询向量：").append(embedMode).append("\n");
        sb.append("- 不写原集合，不连接 Postgres。\n");
        if (note != null && !note.isBlank()) {
            sb.append("- ").append(note).append("\n");
        }
        sb.append("\n货号、面料编号、型号：返回正文里包含该编号就算命中。");
        sb.append("中文题：必须命中抽样的那一条 `doc_id + chunk_index`。\n\n");
        sb.append("| 方式 | 题型 | 题数 | Recall@5 | Recall@10 | MRR | 延迟均值 ms | P50 ms | P95 ms |\n");
        sb.append("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        for (String kind : List.of("all", "product", "fabric", "model", "chinese")) {
            appendSummary(sb, summarize("稠密", kind, dense));
            appendSummary(sb, summarize("混合", kind, hybrid));
        }
        sb.append("\n## 逐题名次\n\n");
        sb.append("| 题型 | 问题 | 稠密名次 | 混合名次 | 稠密 ms | 混合 ms |\n");
        sb.append("| --- | --- | ---: | ---: | ---: | ---: |\n");
        int rows = Math.min(dense.size(), hybrid.size());
        int shown = Math.min(rows, 30);
        for (int i = 0; i < shown; i++) {
            Trial left = dense.get(i);
            Trial right = hybrid.get(i);
            sb.append("| ").append(kindLabel(left.question.kind))
                    .append(" | ").append(escapeCell(left.question.text))
                    .append(" | ").append(rankText(firstRank(left.question, left.hits)))
                    .append(" | ").append(rankText(firstRank(right.question, right.hits)))
                    .append(" | ").append(formatMs(left.latencyNanos))
                    .append(" | ").append(formatMs(right.latencyNanos))
                    .append(" |\n");
        }
        if (rows > shown) {
            sb.append("\n其余 ").append(rows - shown).append(" 题未列入表。\n");
        }
        return sb.toString();
    }

    private static void appendSummary(StringBuilder sb, Summary summary) {
        if (!"all".equals(summary.kind) && summary.questions == 0) {
            return;
        }
        sb.append("| ").append(summary.method)
                .append(" | ").append(kindLabel(summary.kind))
                .append(" | ").append(summary.questions)
                .append(" | ").append(formatRatio(summary.recallAt5))
                .append(" | ").append(formatRatio(summary.recallAt10))
                .append(" | ").append(formatRatio(summary.mrr))
                .append(" | ").append(formatDouble(summary.latencyMeanMs))
                .append(" | ").append(formatDouble(summary.latencyP50Ms))
                .append(" | ").append(formatDouble(summary.latencyP95Ms))
                .append(" |\n");
    }

    static String chinesePhrase(String content) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]+").matcher(content);
        String best = null;
        while (matcher.find()) {
            String run = matcher.group();
            if (run.length() >= 4 && (best == null || run.length() > best.length())) {
                best = run;
            }
        }
        if (best == null) {
            return null;
        }
        return best.substring(0, Math.min(8, best.length()));
    }

    private static double percentile(long[] sortedNanos, double p) {
        int index = (int) Math.ceil(p * sortedNanos.length) - 1;
        if (index < 0) {
            index = 0;
        }
        if (index >= sortedNanos.length) {
            index = sortedNanos.length - 1;
        }
        return sortedNanos[index] / 1_000_000.0;
    }

    private static String rankText(int rank) {
        return rank <= 0 ? "未命中" : Integer.toString(rank);
    }

    private static String formatMs(long nanos) {
        return formatDouble(nanos / 1_000_000.0);
    }

    private static String formatRatio(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String escapeCell(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }

    public static Map<String, Summary> overview(List<Trial> dense, List<Trial> hybrid) {
        Map<String, Summary> map = new LinkedHashMap<>();
        map.put("dense", summarize("稠密", "all", dense));
        map.put("hybrid", summarize("混合", "all", hybrid));
        return map;
    }
}
