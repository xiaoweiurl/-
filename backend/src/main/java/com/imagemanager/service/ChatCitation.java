package com.imagemanager.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 句级引用：上下文编号（K/E/B/P/H）与真实记录 id 分开保存。
 * [[K1]] 只对应该次回答里的编号；recordId 是知识切片/文档、ERP 主键或历史问答 id。
 */
public final class ChatCitation {

    /**
     * 追加到系统提示词。每个事实句必须带标记；没有来源就拒答。
     * 优先级：ERP（E）&gt; 知识库/业务员资料（K/B）&gt; 历史问答（H）。
     */
    public static final String RULE =
            "\n\n【句级溯源】上下文里带〔引用 K1〕〔引用 E1〕〔引用 B1〕〔引用 P1〕〔引用 H1〕的材料才可以引用。" +
            "来源优先级：ERP 单据（E）高于知识库与业务员资料（K/B），高于历史问答（H）；岗位卡片（P）只用于岗位经验。" +
            "同一事实冲突时只采用更高优先级来源，禁止用历史问答或文档覆盖 ERP 里的数字。" +
            "每个事实句必须在句末带引用标记，格式严格为 [[K1]] 或 [[E1]]，同一句可写 [[E1]][[K2]]。" +
            "编号只能使用上下文里出现过的，禁止编造编号。" +
            "没有来源支持时，直接说明知识库或 ERP 中没有相关信息并拒答，不要编造数字、货号、客户、工艺或价格。";

    private static final Pattern MARKER = Pattern.compile("\\[\\[([A-Za-z]\\d+)\\]\\]");
    private static final Pattern CITE_TOKEN = Pattern.compile("^[A-Za-z]\\d+$");

    private static final String[] ROW_ID_KEYS = {
            "recordId", "sourceDocId", "docId", "chunkId", "embeddingId", "cardId"
    };
    private static final String[] DATA_ID_KEYS = {
            "记录ID", "编号", "订单号", "报价单号", "id", "dh", "bh",
            "productCode", "产品编码", "生产货号", "货号", "成品货号"
    };

    private ChatCitation() {
    }

    public static int stamp(List<Map<String, Object>> rows, String prefix, int start) {
        if (rows == null) {
            return start;
        }
        int n = start;
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String recordId = resolveRecordId(row);
            if (!recordId.isEmpty()) {
                row.put("recordId", recordId);
            }
            row.put("citeId", prefix + n);
            n++;
        }
        return n;
    }

    public static String mark(Map<String, Object> row) {
        if (row == null) {
            return "";
        }
        Object id = row.get("citeId");
        if (id == null || id.toString().isBlank()) {
            return "";
        }
        Object record = row.get("recordId");
        if (record != null && !record.toString().isBlank()) {
            return "〔引用 " + id + " 记录 " + record + "〕 ";
        }
        return "〔引用 " + id + "〕 ";
    }

    public static Map<String, Object> toSource(Map<String, Object> row, String kind, String title, String excerpt) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", String.valueOf(row.getOrDefault("citeId", "")));
        String recordId = resolveRecordId(row);
        source.put("recordId", recordId);
        Object chunkId = row.get("chunkId");
        if (chunkId != null && !chunkId.toString().isBlank() && !chunkId.toString().equals(recordId)) {
            source.put("chunkId", chunkId.toString());
        }
        source.put("source", kind);
        source.put("title", title == null || title.isBlank() ? kind : title);
        String text = excerpt == null ? "" : excerpt;
        source.put("excerpt", text.length() > 1200 ? text.substring(0, 1200) + "..." : text);
        Object score = row.get("score");
        if (score == null) {
            score = row.get("similarity");
        }
        source.put("score", score == null ? 0 : score);
        return source;
    }

    /**
     * 真实记录 id。引用序号（K1）不会被当成记录 id。
     */
    public static String resolveRecordId(Map<String, Object> row) {
        if (row == null) {
            return "";
        }
        for (String key : ROW_ID_KEYS) {
            String value = text(row.get(key));
            if (!value.isEmpty() && !CITE_TOKEN.matcher(value).matches()) {
                return value;
            }
        }
        Object data = row.get("data");
        if (data instanceof Map<?, ?> map) {
            String single = firstIn(map, DATA_ID_KEYS);
            if (!single.isEmpty()) {
                return single;
            }
            String part = firstIn(map, new String[]{"部件", "工序", "物料名称"});
            String batch = firstIn(map, new String[]{"批号", "batchNo"});
            String huohao = firstIn(map, new String[]{"生产货号", "货号", "成品货号", "产品编码", "productCode"});
            if (!huohao.isEmpty() && !part.isEmpty()) {
                return batch.isEmpty() ? huohao + "|" + part : huohao + "|" + part + "|" + batch;
            }
        }
        return "";
    }

    /**
     * 货号精准查询未命中时，function-calling 兜底分析仍作为一条 E 类来源。
     * 没有 ERP 主键，recordId 固定为工具分析标记，避免和真实单据号混淆。
     */
    public static Map<String, Object> toolAnalysisEntry(String analysis) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "供应链AI工具分析");
        entry.put("summary", "AI 通过 function-calling 调用统计/查询工具得出的分析");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("分析结论", analysis == null ? "" : analysis);
        entry.put("data", data);
        entry.put("recordId", "erp-tool-analysis");
        return entry;
    }

    /**
     * 给检索结果盖引用编号，并生成发给前端的 sources。
     * 工厂模式不把岗位卡片（P）和历史问答（H）放进 sources。
     */
    public static List<Map<String, Object>> collectSources(
            List<Map<String, Object>> knowledgeResults,
            List<Map<String, Object>> salespersonResults,
            List<Map<String, Object>> supplyChainResults,
            List<Map<String, Object>> structuredResults,
            List<Map<String, Object>> positionCardResults,
            List<Map<String, Object>> chatHistoryQAResults,
            boolean includeDesignerSources) {
        stamp(knowledgeResults, "K", 1);
        stamp(salespersonResults, "B", 1);
        int erpSeq = stamp(supplyChainResults, "E", 1);
        stamp(structuredResults, "E", erpSeq);
        stamp(positionCardResults, "P", 1);
        stamp(chatHistoryQAResults, "H", 1);

        List<Map<String, Object>> sources = new ArrayList<>();
        if (knowledgeResults != null) {
            for (Map<String, Object> row : knowledgeResults) {
                if (row == null) {
                    continue;
                }
                String title = firstText(row, "title", "source", "domain");
                sources.add(toSource(row, "knowledge", title.isEmpty() ? "知识库文档" : title, textOf(row.get("content"))));
            }
        }
        if (salespersonResults != null) {
            for (Map<String, Object> row : salespersonResults) {
                if (row == null) {
                    continue;
                }
                String fileName = textOf(row.get("fileName"));
                sources.add(toSource(row, "salesperson_kb", fileName.isEmpty() ? "业务员资料" : fileName, textOf(row.get("content"))));
            }
        }
        appendErp(sources, supplyChainResults);
        appendErp(sources, structuredResults);
        if (includeDesignerSources) {
            if (positionCardResults != null) {
                for (Map<String, Object> row : positionCardResults) {
                    if (row == null) {
                        continue;
                    }
                    sources.add(toSource(row, "position_card", "岗位卡片", textOf(row.get("content"))));
                }
            }
            if (chatHistoryQAResults != null) {
                for (Map<String, Object> row : chatHistoryQAResults) {
                    if (row == null) {
                        continue;
                    }
                    sources.add(toSource(row, "chat_history", "历史问答", textOf(row.get("content"))));
                }
            }
        }
        return sources;
    }

    /** 告诉模型这次回答只能使用已经发给前端的编号，避免凭空写出 [[E1]]。 */
    public static String allowedCiteClause(List<Map<String, Object>> sources) {
        List<String> ids = new ArrayList<>();
        if (sources != null) {
            for (Map<String, Object> source : sources) {
                if (source == null) {
                    continue;
                }
                String id = text(source.get("id"));
                if (CITE_TOKEN.matcher(id).matches()) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            return "\n本次检索没有可引用编号。不要输出 [[K1]]、[[E1]] 这类标记。直接说明知识库或 ERP 中没有相关信息。";
        }
        return "\n本次只允许使用这些编号：" + String.join("、", ids) + "。上下文里没有的编号禁止出现。";
    }

    public static List<String> parseMarkers(String answer) {
        List<String> ids = new ArrayList<>();
        if (answer == null || answer.isEmpty()) {
            return ids;
        }
        Matcher matcher = MARKER.matcher(answer);
        while (matcher.find()) {
            ids.add(matcher.group(1));
        }
        return ids;
    }

    public static String firstText(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = text(row.get(key));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static void appendErp(List<Map<String, Object>> sources, List<Map<String, Object>> rows) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String title = text(row.get("type"));
            sources.add(toSource(row, "supply_chain", title.isEmpty() ? "ERP单据" : title, erpExcerpt(row)));
        }
    }

    private static String erpExcerpt(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        sb.append(textOf(row.get("type"))).append('\n');
        sb.append(textOf(row.get("summary"))).append('\n');
        Object data = row.get("data");
        if (data instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                String value = entry.getValue().toString();
                if (value.length() > 300) {
                    value = value.substring(0, 300) + "...";
                }
                sb.append(entry.getKey()).append(": ").append(value).append('\n');
                if (sb.length() > 1500) {
                    break;
                }
            }
        }
        return sb.toString();
    }

    private static String textOf(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String firstIn(Map<?, ?> row, String[] keys) {
        for (String key : keys) {
            String value = text(row.get(key));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
            return "";
        }
        return s;
    }
}
