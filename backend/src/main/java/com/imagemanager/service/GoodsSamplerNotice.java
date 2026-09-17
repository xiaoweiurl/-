package com.imagemanager.service;

/**
 * 商品库打样员工作通知上下文（货号/品名/文件夹名）。
 */
public final class GoodsSamplerNotice {

    private final long goodsId;
    private final String folderName;
    private final String goodsNo;
    private final String productName;
    private final String initiator;

    public GoodsSamplerNotice(long goodsId, String folderName, String goodsNo,
                              String productName, String initiator) {
        this.goodsId = goodsId;
        this.folderName = folderName;
        this.goodsNo = goodsNo;
        this.productName = productName;
        this.initiator = initiator;
    }

    public long getGoodsId() {
        return goodsId;
    }

    public String getFolderName() {
        return folderName;
    }

    public String getGoodsNo() {
        return goodsNo;
    }

    public String getProductName() {
        return productName;
    }

    public String getInitiator() {
        return initiator;
    }

    /** 展示名：优先文件夹名（货号+品名），否则拼货号/品名。 */
    public String displayName() {
        if (folderName != null && !folderName.isBlank()) {
            return folderName.trim();
        }
        String no = goodsNo == null ? "" : goodsNo.trim();
        String name = productName == null ? "" : productName.trim();
        String combined = no + name;
        return combined.isEmpty() ? "未命名商品" : combined;
    }
}
