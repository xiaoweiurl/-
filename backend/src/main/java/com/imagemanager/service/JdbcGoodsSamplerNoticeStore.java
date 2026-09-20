package com.imagemanager.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcGoodsSamplerNoticeStore implements GoodsSamplerNoticeStore {

    private static final String SELECT = "SELECT goods_id, ding_userid, sampler_name, task_id,"
            + " sent_folder_name, sent_goods_no, sent_product_name, sent_initiator, followup_task_id,"
            + " last_status, last_message, last_kind, persist_ok"
            + " FROM goods_sampler_notice WHERE goods_id = ?";

    private static final RowMapper<GoodsSamplerNoticeRecord> MAPPER = (rs, rowNum) -> {
        long followup = rs.getLong("followup_task_id");
        Long followupTaskId = rs.wasNull() || followup <= 0 ? null : followup;
        boolean persistOk = rs.getBoolean("persist_ok");
        if (rs.wasNull()) {
            persistOk = true;
        }
        String lastStatus = rs.getString("last_status");
        if (lastStatus == null || lastStatus.isBlank()) {
            lastStatus = SamplerNoticeResult.Status.SENT.name();
        }
        String lastMessage = rs.getString("last_message");
        String lastKind = rs.getString("last_kind");
        if (lastKind == null || lastKind.isBlank()) {
            lastKind = SamplerNoticeResult.KIND_ASSIGNMENT;
        }
        return new GoodsSamplerNoticeRecord(
                rs.getLong("goods_id"),
                rs.getString("ding_userid"),
                rs.getString("sampler_name"),
                rs.getLong("task_id"),
                rs.getString("sent_folder_name"),
                rs.getString("sent_goods_no"),
                rs.getString("sent_product_name"),
                rs.getString("sent_initiator"),
                followupTaskId,
                lastStatus,
                lastMessage == null ? "" : lastMessage,
                lastKind,
                persistOk);
    };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;

    public JdbcGoodsSamplerNoticeStore(JdbcTemplate jdbcTemplate,
                                       PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(java.util.Objects.requireNonNull(transactionManager));
    }

    @Override
    public void saveAssignment(GoodsSamplerNoticeRecord record) {
        if (record == null) {
            return;
        }
        txTemplate.executeWithoutResult(s -> jdbcTemplate.update(
                "INSERT INTO goods_sampler_notice ("
                        + " goods_id, ding_userid, sampler_name, task_id,"
                        + " sent_folder_name, sent_goods_no, sent_product_name, sent_initiator,"
                        + " sent_at, followup_task_id, followup_sent_at, updated_at,"
                        + " last_status, last_message, last_kind, persist_ok)"
                        + " VALUES (?,?,?,?,?,?,?,?, NOW(), NULL, NULL, NOW(), ?,?,?,?)"
                        + " ON CONFLICT (goods_id) DO UPDATE SET"
                        + " ding_userid = EXCLUDED.ding_userid,"
                        + " sampler_name = EXCLUDED.sampler_name,"
                        + " task_id = EXCLUDED.task_id,"
                        + " sent_folder_name = EXCLUDED.sent_folder_name,"
                        + " sent_goods_no = EXCLUDED.sent_goods_no,"
                        + " sent_product_name = EXCLUDED.sent_product_name,"
                        + " sent_initiator = EXCLUDED.sent_initiator,"
                        + " sent_at = NOW(),"
                        + " followup_task_id = NULL,"
                        + " followup_sent_at = NULL,"
                        + " last_status = EXCLUDED.last_status,"
                        + " last_message = EXCLUDED.last_message,"
                        + " last_kind = EXCLUDED.last_kind,"
                        + " persist_ok = EXCLUDED.persist_ok,"
                        + " updated_at = NOW()",
                record.getGoodsId(),
                nz(record.getDingUserId()),
                nz(record.getSamplerName()),
                record.getTaskId(),
                nz(record.getSentFolderName()),
                nz(record.getSentGoodsNo()),
                nz(record.getSentProductName()),
                nz(record.getSentInitiator()),
                nz(record.getLastStatus()),
                truncate(record.getLastMessage(), 500),
                nz(record.getLastKind()),
                record.isPersistOk()));
    }

    @Override
    public void saveLastResult(long goodsId, String samplerName, String status, String message,
                               String kind, boolean persistOk) {
        txTemplate.executeWithoutResult(s -> jdbcTemplate.update(
                "INSERT INTO goods_sampler_notice ("
                        + " goods_id, ding_userid, sampler_name, task_id,"
                        + " sent_folder_name, sent_goods_no, sent_product_name, sent_initiator,"
                        + " sent_at, followup_task_id, followup_sent_at, updated_at,"
                        + " last_status, last_message, last_kind, persist_ok)"
                        + " VALUES (?, '', ?, 0, '', '', '', '', NOW(), NULL, NULL, NOW(), ?,?,?,?)"
                        + " ON CONFLICT (goods_id) DO UPDATE SET"
                        + " sampler_name = COALESCE(NULLIF(EXCLUDED.sampler_name, ''), goods_sampler_notice.sampler_name),"
                        + " last_status = EXCLUDED.last_status,"
                        + " last_message = EXCLUDED.last_message,"
                        + " last_kind = EXCLUDED.last_kind,"
                        + " persist_ok = EXCLUDED.persist_ok,"
                        + " updated_at = NOW()",
                goodsId,
                nz(samplerName),
                nz(status),
                truncate(message, 500),
                nz(kind),
                persistOk));
    }

    @Override
    public Optional<GoodsSamplerNoticeRecord> findByGoodsId(long goodsId) {
        List<GoodsSamplerNoticeRecord> rows = jdbcTemplate.query(SELECT, MAPPER, goodsId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public void markFollowupSent(long goodsId, long assignmentTaskId, long followupTaskId) {
        txTemplate.executeWithoutResult(s -> jdbcTemplate.update(
                "UPDATE goods_sampler_notice SET followup_task_id = ?, followup_sent_at = NOW(),"
                        + " last_status = ?, last_message = ?, last_kind = ?, persist_ok = TRUE,"
                        + " updated_at = NOW() WHERE goods_id = ? AND task_id = ?",
                followupTaskId,
                SamplerNoticeResult.Status.SENT.name(),
                "ok",
                SamplerNoticeResult.KIND_FOLLOWUP,
                goodsId,
                assignmentTaskId));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int max) {
        String text = nz(value);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }
}
