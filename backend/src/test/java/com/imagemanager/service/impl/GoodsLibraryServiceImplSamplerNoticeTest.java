package com.imagemanager.service.impl;

import com.imagemanager.service.FileStorageService;
import com.imagemanager.service.GoodsSamplerNotice;
import com.imagemanager.service.GoodsSamplerNoticeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsLibraryServiceImplSamplerNoticeTest {

    private JdbcTemplate jdbcTemplate;
    private GoodsSamplerNoticeService samplerNoticeService;
    private GoodsLibraryServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        samplerNoticeService = mock(GoodsSamplerNoticeService.class);
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
        service = new GoodsLibraryServiceImpl(
                jdbcTemplate, mock(FileStorageService.class), tm, samplerNoticeService);
    }

    @Test
    void updateWithUnchangedSamplerTriggersFormFilledBackfill() {
        Map<String, Object> row = existingRow("肖伟", "", "");
        when(jdbcTemplate.queryForList(contains("FROM goods_library WHERE id=?"), eq(77L)))
                .thenReturn(List.of(row));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        Map<String, String> body = new HashMap<>();
        body.put("goods_no", "A1");
        body.put("product_name", "吊带");
        service.updateGoods(77L, body);

        ArgumentCaptor<GoodsSamplerNotice> assigned = ArgumentCaptor.forClass(GoodsSamplerNotice.class);
        verify(samplerNoticeService).notifySamplerAssignedAsync(eq("肖伟"), eq("肖伟"), assigned.capture());
        ArgumentCaptor<GoodsSamplerNotice> filled = ArgumentCaptor.forClass(GoodsSamplerNotice.class);
        verify(samplerNoticeService).notifySamplerFormFilledAsync(filled.capture(), eq("肖伟"));
        assertEquals("A1", filled.getValue().getGoodsNo());
        assertEquals("吊带", filled.getValue().getProductName());
        assertEquals("肖伟", filled.getValue().getInitiator());
        assertTrue(filled.getValue().getFolderName().contains("A1"));
    }

    @Test
    void updateWithChangedSamplerDoesNotTriggerFormFilled() {
        Map<String, Object> row = existingRow("李四", "BN-001", "真丝吊带");
        when(jdbcTemplate.queryForList(contains("FROM goods_library WHERE id=?"), eq(9L)))
                .thenReturn(List.of(row));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        Map<String, String> body = new HashMap<>();
        body.put("sampler", "王五");
        service.updateGoods(9L, body);

        verify(samplerNoticeService).notifySamplerAssignedAsync(eq("李四"), eq("王五"), any());
        verify(samplerNoticeService, never()).notifySamplerFormFilledAsync(any(), any());
    }

    @Test
    void createStillOnlyNotifiesAssignment() {
        Map<String, Object> created = existingRow("肖伟", "A1", "吊带");
        created.put("id", 3L);
        when(jdbcTemplate.queryForMap(contains("INSERT INTO goods_library"),
                any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(created);
        when(jdbcTemplate.queryForList(contains("FROM goods_library WHERE id=?"), eq(3L)))
                .thenReturn(List.of(created));

        Map<String, String> fields = new HashMap<>();
        fields.put("initiator", "肖伟");
        fields.put("sampler", "肖伟");
        fields.put("goods_no", "A1");
        fields.put("product_name", "吊带");
        service.createGoods(fields, Map.of(), "user-1");

        verify(samplerNoticeService).notifySamplerAssignedAsync(eq(null), eq("肖伟"), any());
        verify(samplerNoticeService, never()).notifySamplerFormFilledAsync(any(), any());
    }

    @Test
    void resendUsesCurrentSamplerAndDoesNotTreatAsFormFilled() {
        Map<String, Object> row = existingRow("肖伟", "A1", "吊带");
        row.put("id", 77L);
        when(jdbcTemplate.queryForList(contains("FROM goods_library WHERE id=?"), eq(77L)))
                .thenReturn(List.of(row));

        service.resendSamplerNotice(77L);

        verify(samplerNoticeService).resendAssignment(any(), eq("肖伟"));
        verify(samplerNoticeService, never()).notifySamplerAssignedAsync(any(), any(), any());
        verify(samplerNoticeService, never()).notifySamplerFormFilledAsync(any(), any());
    }

    private static Map<String, Object> existingRow(String sampler, String goodsNo, String productName) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 77L);
        row.put("folder_name", (goodsNo + productName).isEmpty() ? "未命名商品" : goodsNo + productName);
        row.put("initiator", "肖伟");
        row.put("sampler", sampler);
        row.put("product_name", productName);
        row.put("goods_no", goodsNo);
        row.put("customer", "");
        row.put("order_no", "");
        row.put("remark", "");
        return row;
    }
}
