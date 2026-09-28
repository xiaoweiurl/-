package com.imagemanager.imagesearch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 回填断点。表要手动建，Flyway 是关的。
 */
@Component
public class ImageVectorProgressStore {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;

    public ImageVectorProgressStore(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public Optional<ImageSearchBackfillEngine.Progress> find(String vectorId) {
        try {
            List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                    "SELECT status, oss_key FROM image_vector_index WHERE vector_id = ?",
                    vectorId));
            if (rows == null || rows.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> row = rows.get(0);
            String state = row.get("status") == null ? "" : row.get("status").toString();
            String key = row.get("oss_key") == null ? "" : row.get("oss_key").toString();
            return Optional.of(new ImageSearchBackfillEngine.Progress(state, key));
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    public void save(ImageVectorRecord record, String status, String sha256, String error) {
        String message = error == null ? null : ImageSearchFilters.cut(error, 500);
        String indexedAtSql = "DONE".equals(status) ? "now()" : "NULL";
        String sql = "INSERT INTO image_vector_index "
                + "(vector_id, source, source_id, slot, company, oss_key, content_sha256, status, error_message, attempts, indexed_at, updated_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,1," + indexedAtSql + ",now()) "
                + "ON CONFLICT (vector_id) DO UPDATE SET "
                + "source = EXCLUDED.source, source_id = EXCLUDED.source_id, slot = EXCLUDED.slot, "
                + "company = EXCLUDED.company, oss_key = EXCLUDED.oss_key, content_sha256 = EXCLUDED.content_sha256, "
                + "status = EXCLUDED.status, error_message = EXCLUDED.error_message, "
                + "attempts = image_vector_index.attempts + 1, indexed_at = " + indexedAtSql + ", updated_at = now()";
        try {
            txTemplate.executeWithoutResult(tx -> jdbcTemplate.update(sql,
                    record.vectorId(),
                    record.source(),
                    record.sourceId(),
                    record.slot() == null ? "" : record.slot(),
                    record.company(),
                    record.ossKey(),
                    sha256,
                    status,
                    message));
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    static RuntimeException translate(RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        Throwable cursor = exception;
        while (cursor != null) {
            String text = cursor.getMessage() == null ? "" : cursor.getMessage();
            if (text.contains("image_vector_index")) {
                return new IllegalStateException(
                        "缺少表 image_vector_index。Flyway 已关闭，请先手动执行 "
                                + "backend/src/main/resources/db/migration/V64__image_vector_index.sql",
                        exception);
            }
            cursor = cursor.getCause();
        }
        if (message.contains("image_vector_index")) {
            return new IllegalStateException(
                    "缺少表 image_vector_index。Flyway 已关闭，请先手动执行 V64__image_vector_index.sql",
                    exception);
        }
        return exception;
    }
}
