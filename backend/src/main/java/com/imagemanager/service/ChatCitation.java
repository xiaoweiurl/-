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
