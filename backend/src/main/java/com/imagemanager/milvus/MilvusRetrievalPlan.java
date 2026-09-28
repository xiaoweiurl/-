package com.imagemanager.milvus;

/**
 * 混合检索开关。默认关闭时走原来的稠密 HNSW，和回滚后的行为一致。
 */
public final class MilvusRetrievalPlan {

    public enum Mode {
        DENSE,
        HYBRID
    }

    private MilvusRetrievalPlan() {
    }

    public static Mode resolve(boolean hybridEnabled, boolean hybridReady, boolean rebuilding) {
        if (hybridEnabled && hybridReady && !rebuilding) {
            return Mode.HYBRID;
        }
        return Mode.DENSE;
    }
}
