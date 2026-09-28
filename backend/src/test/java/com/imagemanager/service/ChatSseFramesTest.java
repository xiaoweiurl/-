package com.imagemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用 mock ERP 结果走和线上相同的编号与结束帧，不启动模型。
 */
class ChatSseFramesTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void erpHitStampsE1AndTerminalFrameCarriesSourcesAndHistoryId() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("订单号", "XS-EXAMPLE-22");
        data.put("货号", "EXAMPLE-HUO");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "销售订单");
        row.put("summary", "EXAMPLE 命中");
        row.put("data", data);

        List<Map<String, Object>> sources = ChatCitation.collectSources(
                List.of(), List.of(), List.of(row), List.of(), List.of(), List.of(), false);

        assertEquals("E1", sources.get(0).get("id"));
        assertEquals("XS-EXAMPLE-22", sources.get(0).get("recordId"));
        assertEquals("supply_chain", sources.get(0).get("source"));

        String wire = frameDone("hist-erp-1", "conv-1", sources);
        assertTrue(wire.startsWith("event:message\ndata:"));
        assertTrue(wire.endsWith("\n\n"));
        JsonNode done = objectMapper.readTree(payload(wire));
        assertEquals("done", done.get("type").asText());
        assertEquals("hist-erp-1", done.get("historyId").asText());
        assertEquals("E1", done.get("sources").get(0).get("id").asText());
        assertFalse(payload(wire).contains("\n"), "Jackson 结束帧必须是单行 data，避免前端按行解析时拆碎");
    }

    @Test
    void exactHuohaoMissStillEmitsToolAnalysisAsE1() throws Exception {
        Map<String, Object> tool = ChatCitation.toolAnalysisEntry("EXAMPLE 货号精准匹配未命中，工具返回排查说明");
        Map<String, Object> salesperson = new LinkedHashMap<>();
        salesperson.put("fileName", "EXAMPLE-业务员资料.pdf");
        salesperson.put("content", "EXAMPLE 委托模板");
        salesperson.put("recordId", "doc-example-b");
        Map<String, Object> knowledge = new LinkedHashMap<>();
        knowledge.put("title", "EXAMPLE 知识");
        knowledge.put("content", "EXAMPLE 切片");
        knowledge.put("sourceDocId", "doc-example-k");

        List<Map<String, Object>> sources = ChatCitation.collectSources(
                List.of(knowledge),
                List.of(salesperson),
                List.of(tool),
                List.of(),
                List.of(),
                List.of(),
                false);

        assertEquals(List.of("K1", "B1", "E1"), sources.stream().map(s -> s.get("id")).toList());
        assertEquals("供应链AI工具分析", sources.get(2).get("title"));
        assertEquals("erp-tool-analysis", sources.get(2).get("recordId"));
        assertTrue(ChatCitation.mark(tool).contains("E1"));

        String wire = frameDone("hist-miss-1", "conv-factory", sources);
        JsonNode done = objectMapper.readTree(payload(wire));
        assertEquals("hist-miss-1", done.get("historyId").asText());
        assertEquals("E1", done.get("sources").get(2).get("id").asText());
        assertTrue(wire.contains("\"id\":\"E1\""));
        assertTrue(ChatCitation.allowedCiteClause(sources).contains("K1、B1、E1"));
    }

    @Test
    void emptyRetrievalStillClosesWithSourcesArrayAndHistoryId() throws Exception {
        List<Map<String, Object>> sources = ChatCitation.collectSources(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), true);
        assertTrue(sources.isEmpty());
        String wire = frameDone("hist-empty-1", "conv-designer", sources);
        JsonNode done = objectMapper.readTree(payload(wire));
        assertEquals("done", done.get("type").asText());
        assertEquals("hist-empty-1", done.get("historyId").asText());
        assertTrue(done.get("sources").isArray());
        assertEquals(0, done.get("sources").size());
        assertTrue(ChatCitation.allowedCiteClause(sources).contains("不要输出"));
    }

    @Test
    void newlineInsideExcerptStaysEscapedInsideOneDataLine() throws Exception {
        Map<String, Object> tool = ChatCitation.toolAnalysisEntry("第一行\n第二行");
        List<Map<String, Object>> sources = ChatCitation.collectSources(
                new ArrayList<>(), new ArrayList<>(), List.of(tool), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false);
        String json = objectMapper.writeValueAsString(ChatSseFrames.sourcesEvent(sources));
        assertFalse(json.contains("\n"));
        assertTrue(json.contains("\\n"));
        String wire = ChatSseFrames.wire("message", json);
        assertEquals(1, wire.split("data:", -1).length - 1);
    }

    private String frameDone(String historyId, String conversationId, List<Map<String, Object>> sources) throws Exception {
        return ChatSseFrames.wire("message", objectMapper.writeValueAsString(
                ChatSseFrames.done(historyId, conversationId, sources)));
    }

    private static String payload(String wire) {
        int start = wire.indexOf("data:") + "data:".length();
        int end = wire.lastIndexOf("\n\n");
        return wire.substring(start, end);
    }
}
