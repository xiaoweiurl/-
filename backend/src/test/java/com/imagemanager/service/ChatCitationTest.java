package com.imagemanager.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatCitationTest {

    @Test
    void sourceKeepsRealRecordIdSeparateFromCiteToken() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceDocId", "doc-9f3a");
        row.put("chunkId", "chunk-1");
        row.put("content", "EXAMPLE 工艺说明");
        row.put("score", 0.82);

        ChatCitation.stamp(List.of(row), "K", 1);
        Map<String, Object> source = ChatCitation.toSource(row, "knowledge", "知识库文档", "EXAMPLE 工艺说明");

        assertEquals("K1", source.get("id"));
        assertEquals("doc-9f3a", source.get("recordId"));
        assertEquals("chunk-1", source.get("chunkId"));
        assertFalse(source.get("id").equals(source.get("recordId")));
        assertTrue(ChatCitation.mark(row).contains("doc-9f3a"));
    }

    @Test
    void erpPrimaryKeyBeatsSequence() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("订单号", "XS-EXAMPLE-1");
        data.put("客户名", "EXAMPLE客户");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "销售订单");
        row.put("data", data);

        ChatCitation.stamp(List.of(row), "E", 2);
        Map<String, Object> source = ChatCitation.toSource(row, "supply_chain", "销售订单", "摘要");

        assertEquals("E2", source.get("id"));
        assertEquals("XS-EXAMPLE-1", source.get("recordId"));
    }

    @Test
    void historyQaUsesConversationId() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recordId", "conv-history-1");
        row.put("content", "【用户问题】EXAMPLE");
        ChatCitation.stamp(List.of(row), "H", 1);
        Map<String, Object> source = ChatCitation.toSource(row, "chat_history", "历史问答", "【用户问题】EXAMPLE");
        assertEquals("H1", source.get("id"));
        assertEquals("conv-history-1", source.get("recordId"));
        assertEquals("chat_history", source.get("source"));
    }

    @Test
    void parseMarkersReadsOnlyBracketIds() {
        List<String> ids = ChatCitation.parseMarkers("克重是 120[[E1]]。工艺见文档 [[K2]][[H1]]，不要把 [K3] 算进去。");
        assertEquals(List.of("E1", "K2", "H1"), ids);
    }

    @Test
    void ruleRequiresCitationRefusalAndErpPriority() {
        assertTrue(ChatCitation.RULE.contains("每个事实句必须"));
        assertTrue(ChatCitation.RULE.contains("拒答"));
        assertTrue(ChatCitation.RULE.contains("ERP"));
        assertTrue(ChatCitation.RULE.indexOf("ERP") < ChatCitation.RULE.indexOf("知识库"));
        assertTrue(ChatCitation.RULE.indexOf("知识库") < ChatCitation.RULE.indexOf("历史问答"));
    }
}
