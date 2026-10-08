package com.imagemanager.imagesearch;

/**
 * 整图向量和主体裁剪向量分属两个 Milvus 集合，互不覆盖。
 */
public enum EmbeddingVariant {
    /** 整图，现有集合，例如 image_vectors_vitl。 */
    FULL,
    /** 主体裁剪后再编码，新集合，例如 image_vectors_vitl_crop。 */
    CROP;

    public static EmbeddingVariant parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return CROP;
        }
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "crop", "cropped" -> CROP;
            case "full", "whole" -> FULL;
            default -> throw new IllegalArgumentException("向量变体只能是 crop 或 full");
        };
    }
}
