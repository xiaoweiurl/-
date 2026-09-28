package com.imagemanager.imagesearch;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从素材表和商品库读出待索引图片。商品库没有公司字段，公司只写默认值供审计，检索时不过滤。
 */
@Component
public class ImageSearchCatalog {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;
    private final ImageSearchProperties properties;

    public ImageSearchCatalog(JdbcTemplate jdbcTemplate,
                              PlatformTransactionManager transactionManager,
                              ImageSearchProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.properties = properties;
    }

    public List<ImageVectorRecord> libraryPage(String afterId, int limit) {
        String cursor = afterId == null ? "" : afterId;
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT id, COALESCE(title, name, '') AS title, file_key, file_path, url, "
                        + "COALESCE(NULLIF(btrim(company), ''), ?) AS company, COALESCE(product_id, '') AS product_id "
                        + "FROM images WHERE COALESCE(deleted, false) = false AND id > ? "
                        + "ORDER BY id LIMIT ?",
                properties.getDefaultCompany(), cursor, limit));
        List<ImageVectorRecord> records = new ArrayList<>();
        if (rows == null) {
            return records;
        }
        for (Map<String, Object> row : rows) {
            String id = text(row.get("id"));
            if (id.isBlank()) {
                continue;
            }
            String key = ImageSearchFilters.resolveStorageKey(
                    text(row.get("file_key")), text(row.get("file_path")), text(row.get("url")));
            records.add(ImageVectorRecord.library(
                    id, text(row.get("company")), key, text(row.get("title")), text(row.get("product_id")),
                    properties.getDefaultCompany()));
        }
        return records;
    }

    public ImageVectorRecord findLibrary(String imageId) {
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT id, COALESCE(title, name, '') AS title, file_key, file_path, url, "
                        + "COALESCE(NULLIF(btrim(company), ''), ?) AS company, COALESCE(product_id, '') AS product_id, "
                        + "COALESCE(deleted, false) AS deleted FROM images WHERE id = ?",
                properties.getDefaultCompany(), imageId));
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        if (Boolean.TRUE.equals(row.get("deleted")) || "t".equalsIgnoreCase(text(row.get("deleted")))
                || "true".equalsIgnoreCase(text(row.get("deleted")))) {
            return null;
        }
        String id = text(row.get("id"));
        String key = ImageSearchFilters.resolveStorageKey(
                text(row.get("file_key")), text(row.get("file_path")), text(row.get("url")));
        return ImageVectorRecord.library(
                id, text(row.get("company")), key, text(row.get("title")), text(row.get("product_id")),
                properties.getDefaultCompany());
    }

    public record GoodsChunk(List<ImageVectorRecord> records, long lastId, boolean done) {
    }

    /**
     * 没有图片的商品行也会推进 lastId，避免整页都没图时回填提前结束。
     */
    public GoodsChunk goodsChunk(long afterId, int limit) {
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT id, COALESCE(folder_name, '') AS folder_name, COALESCE(goods_no, '') AS goods_no, "
                        + "COALESCE(product_name, '') AS product_name, main_image_key, side_image_key, "
                        + "detail_image_key, product_image_key FROM goods_library WHERE id > ? ORDER BY id LIMIT ?",
                afterId, limit));
        if (rows == null || rows.isEmpty()) {
            return new GoodsChunk(List.of(), afterId, true);
        }
        List<ImageVectorRecord> records = new ArrayList<>();
        long lastId = afterId;
        for (Map<String, Object> row : rows) {
            long id = ((Number) row.get("id")).longValue();
            lastId = id;
            String title = titleOf(text(row.get("folder_name")), text(row.get("goods_no")), text(row.get("product_name")));
            addSlot(records, id, "main", text(row.get("main_image_key")), text(row.get("goods_no")), title);
            addSlot(records, id, "side", text(row.get("side_image_key")), text(row.get("goods_no")), title);
            addSlot(records, id, "detail", text(row.get("detail_image_key")), text(row.get("goods_no")), title);
            addSlot(records, id, "product", text(row.get("product_image_key")), text(row.get("goods_no")), title);
        }
        return new GoodsChunk(records, lastId, false);
    }

    public ImageVectorRecord findGoodsSlot(long goodsId, String slot) {
        String column = slotColumn(slot);
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT id, COALESCE(folder_name, '') AS folder_name, COALESCE(goods_no, '') AS goods_no, "
                        + "COALESCE(product_name, '') AS product_name, " + column + " AS image_key "
                        + "FROM goods_library WHERE id = ?",
                goodsId));
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        String key = text(row.get("image_key"));
        if (key.isBlank()) {
            return null;
        }
        String title = titleOf(text(row.get("folder_name")), text(row.get("goods_no")), text(row.get("product_name")));
        return ImageVectorRecord.goods(goodsId, slot, properties.getDefaultCompany(), key,
                text(row.get("goods_no")), title, properties.getDefaultCompany());
    }

    private void addSlot(List<ImageVectorRecord> records, long id, String slot, String key,
                         String goodsNo, String title) {
        if (key == null || key.isBlank()) {
            return;
        }
        records.add(ImageVectorRecord.goods(id, slot, properties.getDefaultCompany(), key, goodsNo, title,
                properties.getDefaultCompany()));
    }

    static String slotColumn(String slot) {
        return switch (ImageSearchFilters.normalizeSlot(slot)) {
            case "main" -> "main_image_key";
            case "side" -> "side_image_key";
            case "detail" -> "detail_image_key";
            case "product" -> "product_image_key";
            default -> throw new IllegalArgumentException("未知图片槽位");
        };
    }

    static String titleOf(String folderName, String goodsNo, String productName) {
        if (folderName != null && !folderName.isBlank() && !"未命名商品".equals(folderName)) {
            return folderName;
        }
        String combined = ((goodsNo == null ? "" : goodsNo) + " " + (productName == null ? "" : productName)).trim();
        return combined.isBlank() ? "未命名商品" : combined;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }
}
