package com.imagemanager.imagesearch;

/**
 * 对用户返回的条数和向 Milvus 多取的条数分开。
 * 对话默认仍是 5 条，说「全部」最多 50 条；内部分组可以多取，上限停在这里。
 */
public final class VisualSearchLimits {

    public static final int MAX_INTERNAL = 250;

    private VisualSearchLimits() {
    }

    public static int capInternal(int value) {
        return Math.max(1, Math.min(value, MAX_INTERNAL));
    }
}
