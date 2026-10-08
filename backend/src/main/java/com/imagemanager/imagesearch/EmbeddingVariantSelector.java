package com.imagemanager.imagesearch;

/**
 * 每个场景用哪个集合。裁剪集合没开或没建好时，一律退回整图集合。
 * 同款默认用裁剪：实图里背景和模特会把别的款抬到 0.86 以上，同款不同照片只有 0.53–0.67。
 * 云端没有 GPU，这个默认是按那个失败模式定的，本机评测可以改成 full。
 */
public final class EmbeddingVariantSelector {

    private EmbeddingVariantSelector() {
    }

    public static EmbeddingVariant select(VisualSearchScenario scenario,
                                          ImageSearchProperties properties,
                                          boolean cropReady) {
        EmbeddingVariant preferred = preferred(scenario, properties);
        if (preferred == EmbeddingVariant.CROP && !cropUsable(properties, cropReady)) {
            return EmbeddingVariant.FULL;
        }
        return preferred;
    }

    public static EmbeddingVariant preferred(VisualSearchScenario scenario, ImageSearchProperties properties) {
        String raw = switch (scenario) {
            case SAME_PRODUCT -> properties.getSameProductVariant();
            case SIMILAR_REFERENCE -> properties.getSimilarReferenceVariant();
            case MIXED -> properties.getSameProductVariant();
            case NON_SEARCH -> "full";
        };
        return EmbeddingVariant.parse(raw);
    }

    public static boolean cropUsable(ImageSearchProperties properties, boolean cropReady) {
        return properties != null && properties.isCropEnabled() && cropReady;
    }
}
