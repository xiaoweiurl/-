package com.imagemanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcGoodsSamplerNoticeStoreTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcGoodsSamplerNoticeStore store;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager tm = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        store = new JdbcGoodsSamplerNoticeStore(jdbcTemplate, tm);
    }

    @Test
    void saveAssignmentUpsertsAndClearsPreviousFollowup() {
        GoodsSamplerNoticeRecord record = new GoodsSamplerNoticeRecord(
                77L, "u-xiao", "肖伟", 11L, "未命名商品", "", "", "肖伟", null);
        store.saveAssignment(record);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(),
                eq(77L), eq("u-xiao"), eq("肖伟"), eq(11L),
                eq("未命名商品"), eq(""), eq(""), eq("肖伟"));
        String statement = sql.getValue();
        assertTrue(statement.contains("ON CONFLICT (goods_id) DO UPDATE"));
        assertTrue(statement.contains("followup_task_id = NULL"));
    }

    @Test
    void findByGoodsIdMapsRow() {
        GoodsSamplerNoticeRecord stored = new GoodsSamplerNoticeRecord(
                9L, "u-li", "李四", 42L, "BN-001真丝吊带", "BN-001", "真丝吊带", "张三", null);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(9L)))
                .thenReturn(List.of(stored));

        Optional<GoodsSamplerNoticeRecord> found = store.findByGoodsId(9L);
        assertTrue(found.isPresent());
        assertEquals(42L, found.get().getTaskId());
        assertEquals("BN-001", found.get().getSentGoodsNo());
    }

    @Test
    void markFollowupSentUpdatesMatchingAssignment() {
        store.markFollowupSent(77L, 11L, 22L);
        verify(jdbcTemplate).update(anyString(), eq(22L), eq(77L), eq(11L));
    }
}
