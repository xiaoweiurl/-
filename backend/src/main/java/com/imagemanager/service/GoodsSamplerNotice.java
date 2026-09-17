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
    private final String customer;
    private final String orderNo;

    public GoodsSamplerNotice(long goodsId, String folderName, String goodsNo,
                              String productName, String initiator) {
        this(goodsId, folderName, goodsNo, productName, initiator, "", "");
    }

    public GoodsSamplerNotice(long goodsId, String folderName, String goodsNo,
                              String productName, String initiator,
                              String customer, String orderNo) {
        this.goodsId = goodsId;
        this.folderName = folderName;
        this.goodsNo = goodsNo;
        this.productName = productName;
        this.initiator = initiator;
        this.customer = customer;
        this.orderNo = orderNo;
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

    public String getCustomer() {
        return customer;
    }

    public String getOrderNo() {
        return orderNo;
    }

    /** 展示名：优先货号+品名，其次文件夹名；都空时不使用「未命名商品」占位（通知文案另有待完善标题）。 */
    public String displayName() {
        return SamplerWorkNoticeCards.productHeadline(this);
    }
}
