package com.imagemanager.imagesearch;

/**
 * 弹层上的范围页签。打样是同款，素材是相似参考，全部是两种卡片叠在一起。
 */
public final class ScopeScenario {

    private ScopeScenario() {
    }

    public static VisualSearchScenario fromScope(String scope) {
        String normalized = ImageSearchFilters.normalizeScope(scope);
        return switch (normalized) {
            case ImageSearchFilters.SOURCE_GOODS -> VisualSearchScenario.SAME_PRODUCT;
            case ImageSearchFilters.SOURCE_LIBRARY -> VisualSearchScenario.SIMILAR_REFERENCE;
            default -> VisualSearchScenario.MIXED;
        };
    }
}
