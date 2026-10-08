package com.imagemanager.imagesearch;

/**
 * 图片/文本向量。正式实现调用本机 image-embed-service。
 */
public interface ImageEmbedder {

    float[] embedImage(byte[] data, String filename);

    float[] embedText(String text);
}
