package com.imagemanager.imagesearch;

import com.imagemanager.service.FileStorageService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用数据库里的现况补全结果：删掉已删除素材，并挂上商品库/打样记录。
 */
@Component
public class JdbcImageSearchEnricher implements ImageSearchEnricher {

    private static final int PRESIGN_SECONDS = 3600;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;
    private final FileStorageService fileStorageService;

    public JdbcImageSearchEnricher(JdbcTemplate jdbcTemplate,
                                   PlatformTransactionManager transactionManager,
                                   FileStorageService fileStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.fileStorageService = fileStorageService;
    }

    @Override
    public List<ImageSearchModels.ImageSearchHitView> enrich(List<ImageSearchModels.RawHit> hits, String company) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        String expectedCompany = ImageSearchFilters.normalizeCompany(company, ImageSearchFilters.DEFAULT_COMPANY);
        List<String> libraryIds = new ArrayList<>();
        List<Long> goodsIds = new ArrayList<>();
        for (ImageSearchModels.RawHit hit : hits) {
            if (ImageSearchFilters.SOURCE_LIBRARY.equals(hit.record().source())) {
                libraryIds.add(hit.record().sourceId());
            } else if (ImageSearchFilters.SOURCE_GOODS.equals(hit.record().source())) {
                parseLong(hit.record().sourceId()).ifPresent(goodsIds::add);
            }
        }
        Map<String, Map<String, Object>> images = loadImages(libraryIds);
        Map<Long, Map<String, Object>> goods = loadGoods(goodsIds);
        Map<String, List<ImageSearchModels.GoodsBrief>> related = loadRelated(images.values(), expectedCompany);

        List<ImageSearchModels.ImageSearchHitView> views = new ArrayList<>();
        for (ImageSearchModels.RawHit hit : hits) {
            ImageVectorRecord record = hit.record();
            if (ImageSearchFilters.SOURCE_LIBRARY.equals(record.source())) {
                Map<String, Object> row = images.get(record.sourceId());
                if (row == null || deleted(row)) {
                    continue;
                }
                String rowCompany = ImageSearchFilters.normalizeCompany(text(row.get("company")), expectedCompany);
                if (!expectedCompany.equals(rowCompany)) {
                    continue;
                }
                String key = ImageSearchFilters.resolveStorageKey(
                        text(row.get("file_key")), text(row.get("file_path")), text(row.get("url")));
                ImageSearchModels.ImageSearchHitView view = ImageSearchModels.fromRecord(record, hit.score(), presign(key));
                String title = text(row.get("title"));
                if (title.isBlank()) {
                    title = text(row.get("name"));
                }
                if (!title.isBlank()) {
                    view.setTitle(title);
                }
                view.setAlbumName(text(row.get("album_name")));
                view.setCreatedAt(epochMillis(row.get("created_at")));
                view.setProductId(text(row.get("product_id")));
                view.setRelatedGoods(related.getOrDefault(text(row.get("product_id")), List.of()));
                views.add(view);
            } else if (ImageSearchFilters.SOURCE_GOODS.equals(record.source())) {
                Long id = parseLong(record.sourceId()).orElse(null);
                if (id == null) {
                    continue;
                }
                Map<String, Object> row = goods.get(id);
                if (row == null) {
                    continue;
                }
                String key;
                try {
                    key = text(row.get(ImageSearchCatalog.slotColumn(record.slot())));
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                ImageSearchModels.ImageSearchHitView view = ImageSearchModels.fromRecord(
                        record, hit.score(), presign(key.isBlank() ? record.ossKey() : key));
                ImageSearchModels.GoodsBrief brief = toBrief(row);
                view.setGoods(brief);
                view.setCreatedAt(epochMillis(row.get("created_at")));
                if (brief.getFolderName() != null && !brief.getFolderName().isBlank()) {
                    view.setTitle(brief.getFolderName());
                }
                view.setRelatedGoods(List.of(brief));
                views.add(view);
            }
        }
        return views;
    }

    private Map<String, List<ImageSearchModels.GoodsBrief>> loadRelated(Collection<Map<String, Object>> images, String company) {
        List<String> productIds = new ArrayList<>();
        for (Map<String, Object> image : images) {
            String productId = text(image.get("product_id"));
            if (!productId.isBlank() && !deleted(image)) {
                String rowCompany = ImageSearchFilters.normalizeCompany(text(image.get("company")), company);
                if (company.equals(rowCompany)) {
                    productIds.add(productId);
                }
            }
        }
        Map<String, List<ImageSearchModels.GoodsBrief>> grouped = new HashMap<>();
        if (productIds.isEmpty()) {
            return grouped;
        }
        String placeholders = placeholders(productIds.size());
        Object[] args = new Object[productIds.size() * 2];
        for (int i = 0; i < productIds.size(); i++) {
            args[i] = productIds.get(i);
            args[productIds.size() + i] = productIds.get(i);
        }
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT id, folder_name, goods_no, product_name, sampler, initiator, customer, order_no "
                        + "FROM goods_library WHERE goods_no IN (" + placeholders + ") "
                        + "OR CAST(id AS varchar) IN (" + placeholders + ") ORDER BY id",
                args));
        if (rows == null) {
            return grouped;
        }
        for (Map<String, Object> row : rows) {
            ImageSearchModels.GoodsBrief brief = toBrief(row);
            attach(grouped, brief.getGoodsNo(), brief);
            attach(grouped, Long.toString(brief.getId()), brief);
        }
        for (Map.Entry<String, List<ImageSearchModels.GoodsBrief>> entry : grouped.entrySet()) {
            if (entry.getValue().size() > 3) {
                entry.setValue(new ArrayList<>(entry.getValue().subList(0, 3)));
            }
        }
        return grouped;
    }

    private Map<String, Map<String, Object>> loadImages(List<String> ids) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        String placeholders = placeholders(ids.size());
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT id, title, name, album_name, product_id, company, file_key, file_path, url, created_at, "
                        + "COALESCE(deleted, false) AS deleted FROM images WHERE id IN (" + placeholders + ")",
                ids.toArray()));
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                map.put(text(row.get("id")), row);
            }
        }
        return map;
    }

    private Map<Long, Map<String, Object>> loadGoods(List<Long> ids) {
        Map<Long, Map<String, Object>> map = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        String placeholders = placeholders(ids.size());
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT id, folder_name, goods_no, product_name, sampler, initiator, customer, order_no, created_at, "
                        + "main_image_key, side_image_key, detail_image_key, product_image_key "
                        + "FROM goods_library WHERE id IN (" + placeholders + ")",
                ids.toArray()));
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                map.put(((Number) row.get("id")).longValue(), row);
            }
        }
        return map;
    }

    private String presign(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return fileStorageService.generatePresignedUrl(key, PRESIGN_SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private static ImageSearchModels.GoodsBrief toBrief(Map<String, Object> row) {
        ImageSearchModels.GoodsBrief brief = new ImageSearchModels.GoodsBrief();
        brief.setId(((Number) row.get("id")).longValue());
        brief.setFolderName(text(row.get("folder_name")));
        brief.setGoodsNo(text(row.get("goods_no")));
        brief.setProductName(text(row.get("product_name")));
        brief.setSampler(text(row.get("sampler")));
        brief.setInitiator(text(row.get("initiator")));
        brief.setCustomer(text(row.get("customer")));
        brief.setOrderNo(text(row.get("order_no")));
        return brief;
    }

    private static void attach(Map<String, List<ImageSearchModels.GoodsBrief>> grouped,
                               String key, ImageSearchModels.GoodsBrief brief) {
        if (key == null || key.isBlank()) {
            return;
        }
        grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(brief);
    }

    private static boolean deleted(Map<String, Object> row) {
        Object value = row.get("deleted");
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && ("t".equalsIgnoreCase(value.toString()) || "true".equalsIgnoreCase(value.toString()));
    }

    private static java.util.Optional<Long> parseLong(String value) {
        try {
            return java.util.Optional.of(Long.parseLong(value));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Long epochMillis(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().toEpochMilli();
        }
        if (value instanceof OffsetDateTime offset) {
            return offset.toInstant().toEpochMilli();
        }
        if (value instanceof LocalDateTime local) {
            return local.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        }
        if (value instanceof Date date) {
            return date.getTime();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
