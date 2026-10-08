package com.imagemanager.imagesearch;

import java.util.List;
import java.util.Map;

/**
 * 图片向量集合。实现只能操作 image_ 前缀的新集合。
 */
public interface ImageVectorIndex {

    boolean isReady();

    void upsert(ImageVectorRecord record, float[] embedding);

    void deleteById(String vectorId);

    List<ImageSearchModels.RawHit> search(float[] embedding, String scope, String company, int topK);

    /** 裁剪集合没建好时为 false。测试桩默认只有整图。 */
    default boolean supports(EmbeddingVariant variant) {
        return variant == EmbeddingVariant.FULL && isReady();
    }

    default void upsert(EmbeddingVariant variant, ImageVectorRecord record, float[] embedding) {
        if (variant != EmbeddingVariant.FULL) {
            throw new IllegalStateException("裁剪向量集合未启用");
        }
        upsert(record, embedding);
    }

    default void deleteById(EmbeddingVariant variant, String vectorId) {
        if (variant == EmbeddingVariant.FULL) {
            deleteById(vectorId);
        }
    }

    default List<ImageSearchModels.RawHit> search(EmbeddingVariant variant, float[] embedding,
                                                  String scope, String company, int topK) {
        if (variant == EmbeddingVariant.CROP) {
            throw new IllegalStateException("裁剪向量集合未启用");
        }
        return search(embedding, scope, company, topK);
    }

    /**
     * 按主键取回已入库的图片向量，给中文条件做重排。测试桩默认没有向量，重排会保留图片分。
     */
    default Map<String, float[]> embeddings(EmbeddingVariant variant, List<String> vectorIds) {
        return Map.of();
    }
}
