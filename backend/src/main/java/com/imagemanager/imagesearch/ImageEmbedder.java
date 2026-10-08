package com.imagemanager.imagesearch;

/**
 * 图片/文本向量。正式实现调用本机 image-embed-service。
 */
public interface ImageEmbedder {

    float[] embedImage(byte[] data, String filename);

    /**
     * crop 为 true 时请向量服务先做主体裁剪。服务没开裁剪或没检出主体时，返回整图向量。
     */
    default float[] embedImage(byte[] data, String filename, boolean crop) {
        return embedImage(data, filename);
    }

    float[] embedText(String text);

    /** 向量服务 /health 里裁剪检测器已加载。连不上就返回 false。 */
    default boolean cropDetectorReady() {
        return false;
    }
}
