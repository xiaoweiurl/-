package com.imagemanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.eval.RagEvalCase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatFeedbackServiceTest {

    @Test
    void recordStoresQuestionAnswerSourcesUserCompanyCommentAndVerdict() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ChatFeedbackService service = new ChatFeedbackService(jdbc, new ObjectMapper());

        Map<String, Object> saved = service.record(
                "user-1",
                "EXAMPLE",
                "conv-1",
                "EXAMPLE 货号 EX0001 的克重？",
                "没有查到",
                "[{\"id\":\"E1\",\"recordId\":\"bh-1\",\"source\":\"supply_chain\"}]",
                "数字对不上",
                "wrong");

        assertEquals("pending", saved.get("status"));
        assertEquals("wrong", saved.get("verdict"));
        verify(jdbc).update(
                argThat(sql -> sql.contains("user_id")
                        && sql.contains("company")
                        && sql.contains("question")
                        && sql.contains("answer")
                        && sql.contains("sources_json")
                        && sql.contains("comment")
                        && sql.contains("verdict")),
                anyString(),
                eq("user-1"),
                eq("EXAMPLE"),
                eq("conv-1"),
                eq("EXAMPLE 货号 EX0001 的克重？"),
                eq("没有查到"),
                eq("[{\"id\":\"E1\",\"recordId\":\"bh-1\",\"source\":\"supply_chain\"}]"),
                eq("数字对不上"),
                eq("wrong"),
                eq("pending"));
    }

    @Test
    void blankCommentIsStoredAsNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ChatFeedbackService service = new ChatFeedbackService(jdbc, new ObjectMapper());
        service.record("user-1", "EXAMPLE", null, "问题", "答案", null, "  ", "useful");
        verify(jdbc).update(anyString(),
                anyString(), eq("user-1"), eq("EXAMPLE"), eq(""), eq("问题"), eq("答案"),
                isNull(), isNull(), eq("useful"), eq("recorded"));
    }

    @Test
    void listPendingIsScopedToCompany() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any())).thenReturn(List.of());
        ChatFeedbackService service = new ChatFeedbackService(jdbc, new ObjectMapper());
        service.listPending("EXAMPLE");
        verify(jdbc).query(
                argThat(sql -> sql.contains(ChatFeedbackService.COMPANY_EQ) && sql.contains("status = 'pending'")),
                any(RowMapper.class),
                eq("EXAMPLE"));
    }

    @Test
    void wrongAnswerExportsAsEvalJsonlFields() {
        ChatFeedbackService service = new ChatFeedbackService(mock(JdbcTemplate.class), new ObjectMapper());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("question", "EXAMPLE 货号 EX0001 的克重？");
        row.put("sourcesJson", "[{\"recordId\":\"bh-1\"},{\"id\":\"K1\"}]");
        row.put("comment", "数字对不上");
        row.put("company", "EXAMPLE");

        RagEvalCase evalCase = service.toEvalCase(row);
        assertEquals("EXAMPLE 货号 EX0001 的克重？", evalCase.question);
        assertTrue(evalCase.expectedFacts.isEmpty());
        assertEquals(List.of("bh-1"), evalCase.expectedSourceIds);
        assertEquals("EX0001", evalCase.huohao);
        assertFalse(evalCase.shouldRefuse);
        assertEquals("EXAMPLE", evalCase.company);
        assertEquals("数字对不上", evalCase.comment);
        assertNotNull(evalCase.keywords);
        assertFalse(evalCase.example);
    }
}
