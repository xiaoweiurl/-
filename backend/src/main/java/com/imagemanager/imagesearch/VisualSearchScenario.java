package com.imagemanager.imagesearch;

/**
 * 以图搜图的场景。每种会检索的场景对应一个策略类，调用处不散落 if/else。
 * NON_SEARCH 不检索，沿用原来的识图。MIXED 只给弹层的「全部」页：同款卡片在前，参考图在后。
 */
public enum VisualSearchScenario {
    /** 这款做过吗、货号、同款、历史打样，或打样范围。按商品合并。 */
    SAME_PRODUCT,
    /** 找相似素材、参考图、类似风格、找图，或素材范围。一张图一条。 */
    SIMILAR_REFERENCE,
    /** 读字、瑕疵、面料等，不做以图搜图。 */
    NON_SEARCH,
    /** 弹层「全部」：先同款，再参考图。对话不会选出这个场景。 */
    MIXED
}
