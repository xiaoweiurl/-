package com.imagemanager.imagesearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 给检索结果补上已有打样单和商品详情的路径。
 * 记录不存在、或不属于当前公司时留空，前端不渲染链接。
 * 打样作用域会话只保留自己那一张商品，不能借结果打开商品库里的其他款。
 */
@Slf4j
@Component
public class ImageSearchRecordLinks {

    private static final int MAX_PRODUCT_ID = 64;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;

    public ImageSearchRecordLinks(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public void attach(List<ImageSearchModels.ImageSearchHitView> hits, String viewerCompany) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        apply(hits, viewerCompany, load(hits));
    }

    /**
     * 打样会话只能打开自己的打样单和自己的商品详情。其他货号的路径全部去掉。
     */
    public static void retainSampler(List<ImageSearchModels.ImageSearchHitView> hits, String ownGoodsId) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        String own = ownGoodsId == null ? "" : ownGoodsId.trim();
        boolean numeric = own.matches("[1-9]\\d*");
        String sample = numeric ? "/sampler/" + own : "";
        String detail = numeric ? "/goods-library/" + own : "";
        for (ImageSearchModels.ImageSearchHitView hit : hits) {
            if (hit == null) {
                continue;
            }
            if (!sample.equals(hit.getSampleOrderPath())) {
                hit.setSampleOrderPath(null);
            }
            if (!detail.equals(hit.getProductDetailPath())) {
                hit.setProductDetailPath(null);
            }
        }
    }

    static void apply(List<ImageSearchModels.ImageSearchHitView> hits, String viewerCompany, Targets targets) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        Targets known = targets == null ? Targets.empty() : targets;
        for (ImageSearchModels.ImageSearchHitView hit : hits) {
            if (hit == null) {
                continue;
            }
            hit.setSampleOrderPath(null);
            hit.setProductDetailPath(null);
            ImageSearchModels.GoodsBrief goods = displayedGoods(hit);
            if (goods != null && known.goodsCompanies.containsKey(goods.getId())
                    && sameCompany(known.goodsCompanies.get(goods.getId()), viewerCompany)) {
                hit.setSampleOrderPath("/sampler/" + goods.getId());
                hit.setProductDetailPath("/goods-library/" + goods.getId());
            }
            if (hit.getProductDetailPath() == null) {
                String productId = safeProductId(hit.getProductId());
                if (productId != null && known.productCompanies.containsKey(productId)
                        && sameCompany(known.productCompanies.get(productId), viewerCompany)) {
                    hit.setProductDetailPath("/products/" + productId);
                }
            }
        }
    }

    /**
     * 卡片上展示的那一条商品。货号文件夹优先；素材只挂了多条时用第一条，和卡片文案一致。
     */
    static ImageSearchModels.GoodsBrief displayedGoods(ImageSearchModels.ImageSearchHitView hit) {
        if (hit == null) {
            return null;
        }
        if (hit.getGoods() != null && hit.getGoods().getId() > 0) {
            return hit.getGoods();
        }
        List<ImageSearchModels.GoodsBrief> related = hit.getRelatedGoods();
        if (related == null || related.isEmpty() || related.get(0) == null || related.get(0).getId() <= 0) {
            return null;
        }
        return related.get(0);
    }

    /**
     * 没有公司的商品库记录沿用默认公司，和会话缺省公司一致。
     * 写明了其他公司的记录不能打开。
     */
    /**
     * 有归属人但查不到公司时不猜测。没有归属人的记录仍按默认公司。
     * 返回 null 表示这条不能打开。
     */
    static String ownerCompany(String userId, String company) {
        String owner = company == null ? "" : company.trim();
        String user = userId == null ? "" : userId.trim();
        if (!user.isEmpty() && owner.isEmpty()) {
            return null;
        }
        return owner;
    }

    static boolean sameCompany(String recordCompany, String viewerCompany) {
        String viewer = ImageSearchFilters.normalizeCompany(viewerCompany, ImageSearchFilters.DEFAULT_COMPANY);
        String record = recordCompany == null ? "" : recordCompany.trim();
        if (record.isEmpty()) {
            return ImageSearchFilters.DEFAULT_COMPANY.equals(viewer);
        }
        return record.equals(viewer);
    }

    static String safeProductId(String productId) {
        if (productId == null) {
            return null;
        }
        String value = productId.trim();
        if (value.isEmpty() || value.length() > MAX_PRODUCT_ID) {
            return null;
        }
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
            return null;
        }
        return value;
    }

    private Targets load(List<ImageSearchModels.ImageSearchHitView> hits) {
        Set<Long> goodsIds = new LinkedHashSet<>();
        Set<String> productIds = new LinkedHashSet<>();
        for (ImageSearchModels.ImageSearchHitView hit : hits) {
            ImageSearchModels.GoodsBrief goods = displayedGoods(hit);
            if (goods != null) {
                goodsIds.add(goods.getId());
            }
            String productId = safeProductId(hit == null ? null : hit.getProductId());
            if (productId != null) {
                productIds.add(productId);
            }
        }
        Map<Long, String> goodsCompanies = Map.of();
        Map<String, String> productCompanies = Map.of();
        try {
            goodsCompanies = loadGoods(goodsIds);
        } catch (RuntimeException e) {
            log.warn("打样单链接未核对: {}", e.getMessage());
        }
        try {
            productCompanies = loadProducts(productIds);
        } catch (RuntimeException e) {
            log.warn("商品详情链接未核对: {}", e.getMessage());
        }
        return new Targets(goodsCompanies, productCompanies);
    }

    private Map<Long, String> loadGoods(Set<Long> ids) {
        Map<Long, String> companies = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return companies;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT g.id, COALESCE(g.user_id, '') AS user_id, "
                        + "COALESCE(NULLIF(btrim(u.company), ''), '') AS owner_company "
                        + "FROM goods_library g LEFT JOIN users u ON u.id::text = g.user_id::text "
                        + "WHERE g.id IN (" + placeholders + ")",
                ids.toArray()));
        if (rows == null) {
            return companies;
        }
        for (Map<String, Object> row : rows) {
            long id = longId(row.get("id"));
            String owner = ownerCompany(text(row.get("user_id")), text(row.get("owner_company")));
            if (id > 0 && owner != null) {
                companies.put(id, owner);
            }
        }
        return companies;
    }

    private Map<String, String> loadProducts(Set<String> ids) {
        Map<String, String> companies = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return companies;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT id, COALESCE(NULLIF(btrim(company), ''), '') AS owner_company "
                        + "FROM products WHERE id IN (" + placeholders + ")",
                ids.toArray()));
        if (rows == null) {
            return companies;
        }
        for (Map<String, Object> row : rows) {
            String id = safeProductId(text(row.get("id")));
            if (id != null) {
                companies.put(id, text(row.get("owner_company")));
            }
        }
        return companies;
    }

    private static long longId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return -1L;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    static final class Targets {
        private final Map<Long, String> goodsCompanies;
        private final Map<String, String> productCompanies;

        Targets(Map<Long, String> goodsCompanies, Map<String, String> productCompanies) {
            this.goodsCompanies = goodsCompanies == null ? Map.of() : goodsCompanies;
            this.productCompanies = productCompanies == null ? Map.of() : productCompanies;
        }

        static Targets empty() {
            return new Targets(Map.of(), Map.of());
        }
    }
}
