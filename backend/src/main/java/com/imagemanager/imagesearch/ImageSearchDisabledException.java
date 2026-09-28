package com.imagemanager.imagesearch;

/**
 * 功能关闭时的查询失败。上传路径不会抛出它。
 */
public class ImageSearchDisabledException extends RuntimeException {

    public ImageSearchDisabledException() {
        super("以图搜图未开启");
    }
}
