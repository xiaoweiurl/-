package com.imagemanager.milvus;

/**
 * 影子集合的名字保护。回填可以反复删掉影子集合，但不能碰到正在使用的稠密集合。
 */
public final class HybridCollectionGuard {

    /** 代码实际检索的稠密集合，线上约 7604 行。 */
    public static final String LIVE_COLLECTION = "salesperson_docs";

    /**
     * application.yml 里 milvus.collection-name 的默认值。没有任何 Java 字段读取它，线上是空集合。
     */
    public static final String UNUSED_COLLECTION = "salesperson_chunks";

    private HybridCollectionGuard() {
    }

    public static void assertShadowTarget(String source, String target) {
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            throw new IllegalArgumentException("原集合和影子集合名都不能为空");
        }
        String sourceName = source.trim();
        String targetName = target.trim();
        if (sourceName.equals(targetName)) {
            throw new IllegalArgumentException("影子集合不能和原集合同名: " + targetName);
        }
        if (LIVE_COLLECTION.equals(targetName) || UNUSED_COLLECTION.equals(targetName)) {
            throw new IllegalArgumentException("禁止写入或删除 " + targetName + "。混合检索只能写入影子集合。");
        }
    }

    /** 假数据演练只允许删除这个前缀的临时集合。 */
    public static void assertSmokeName(String name) {
        if (name == null || !name.startsWith("hybrid_smoke_")) {
            throw new IllegalArgumentException("临时集合必须以 hybrid_smoke_ 开头: " + name);
        }
        assertShadowTarget(LIVE_COLLECTION, name);
    }
}
