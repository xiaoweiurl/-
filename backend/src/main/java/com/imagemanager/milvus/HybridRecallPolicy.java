package com.imagemanager.milvus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * 混合检索验证完成前，货号精确匹配仍是兜底。
 * 问句里有货号时，混合结果必须正文含该货号；否则视为未命中，调用方回退 ILIKE / 原检索。
 */
public final class HybridRecallPolicy {

    private HybridRecallPolicy() {
    }

    public static <T> List<T> accept(List<T> hits, String productCode, Function<T, String> content) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        if (productCode == null || productCode.isBlank()) {
            return List.copyOf(hits);
        }
        List<T> matched = new ArrayList<>();
        for (T hit : hits) {
            if (hit == null || content == null) {
                continue;
            }
            if (containsCode(content.apply(hit), productCode)) {
                matched.add(hit);
            }
        }
        return matched;
    }

    public static boolean containsCode(String content, String code) {
        if (content == null || code == null || code.isBlank()) {
            return false;
        }
        return content.toLowerCase(Locale.ROOT).contains(code.toLowerCase(Locale.ROOT));
    }
}
