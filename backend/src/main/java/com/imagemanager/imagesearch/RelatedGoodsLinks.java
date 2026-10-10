package com.imagemanager.imagesearch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 素材的 product_id 对商品库。先按货号（忽略大小写和空白），没有货号再按纯数字 id。
 * 货号能对上时，不再用同一个数字去撞另一行的主键，避免把别的款标到卡片上。
 */
final class RelatedGoodsLinks {

    private static final int MAX_LINKS = 3;

    private RelatedGoodsLinks() {
    }

    static List<ImageSearchModels.GoodsBrief> choose(String productId, List<ImageSearchModels.GoodsBrief> rows) {
        String raw = productId == null ? "" : productId.trim();
        if (raw.isEmpty() || rows == null || rows.isEmpty()) {
            return List.of();
        }
        String wanted = SameProductGrouping.canonicalGoodsNo(raw);
        List<ImageSearchModels.GoodsBrief> byNo = new ArrayList<>();
        ImageSearchModels.GoodsBrief byId = null;
        Long numeric = numericId(raw);
        for (ImageSearchModels.GoodsBrief row : rows) {
            if (row == null) {
                continue;
            }
            if (!wanted.isEmpty() && wanted.equals(SameProductGrouping.canonicalGoodsNo(row.getGoodsNo()))) {
                if (!containsId(byNo, row.getId())) {
                    byNo.add(row);
                }
                continue;
            }
            if (numeric != null && row.getId() == numeric && byId == null) {
                byId = row;
            }
        }
        List<ImageSearchModels.GoodsBrief> chosen = !byNo.isEmpty()
                ? byNo
                : (byId == null ? new ArrayList<>() : new ArrayList<>(List.of(byId)));
        chosen.sort(Comparator.comparingLong(ImageSearchModels.GoodsBrief::getId));
        if (chosen.size() > MAX_LINKS) {
            return List.copyOf(chosen.subList(0, MAX_LINKS));
        }
        return List.copyOf(chosen);
    }

    private static boolean containsId(List<ImageSearchModels.GoodsBrief> rows, long id) {
        for (ImageSearchModels.GoodsBrief row : rows) {
            if (row.getId() == id) {
                return true;
            }
        }
        return false;
    }

    private static Long numericId(String raw) {
        if (!raw.matches("[1-9]\\d*")) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
