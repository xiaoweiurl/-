package com.imagemanager.imagesearch;

import java.util.List;

/**
 * 图片向量集合。实现只能操作 image_ 前缀的新集合。
 */
public interface ImageVectorIndex {

    boolean isReady();

    void upsert(ImageVectorRecord record, float[] embedding);

    void deleteById(String vectorId);

    List<ImageSearchModels.RawHit> search(float[] embedding, String scope, String company, int topK);
}
