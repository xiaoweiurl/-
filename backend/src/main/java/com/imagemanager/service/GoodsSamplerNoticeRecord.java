package com.imagemanager.service;

/**
 * 最近一次打样指派工作通知（用于表单保存后判断是否需要补发回填卡）。
 */
public final class GoodsSamplerNoticeRecord {

    private final long goodsId;
    private final String dingUserId;
    private final String samplerName;
    private final long taskId;
    private final String sentFolderName;
    private final String sentGoodsNo;
    private final String sentProductName;
    private final String sentInitiator;
    private final Long followupTaskId;

    public GoodsSamplerNoticeRecord(long goodsId, String dingUserId, String samplerName, long taskId,
                                    String sentFolderName, String sentGoodsNo, String sentProductName,
                                    String sentInitiator, Long followupTaskId) {
        this.goodsId = goodsId;
        this.dingUserId = dingUserId == null ? "" : dingUserId;
        this.samplerName = samplerName;
        this.taskId = taskId;
        this.sentFolderName = sentFolderName;
        this.sentGoodsNo = sentGoodsNo;
        this.sentProductName = sentProductName;
        this.sentInitiator = sentInitiator;
        this.followupTaskId = followupTaskId;
    }

    public long getGoodsId() {
        return goodsId;
    }

    public String getDingUserId() {
        return dingUserId;
    }

    public String getSamplerName() {
        return samplerName;
    }

    public long getTaskId() {
        return taskId;
    }

    public String getSentFolderName() {
        return sentFolderName;
    }

    public String getSentGoodsNo() {
        return sentGoodsNo;
    }

    public String getSentProductName() {
        return sentProductName;
    }

    public String getSentInitiator() {
        return sentInitiator;
    }

    public Long getFollowupTaskId() {
        return followupTaskId;
    }

    public boolean hasFollowup() {
        return followupTaskId != null && followupTaskId > 0;
    }

    public GoodsSamplerNotice toSentGoods() {
        return new GoodsSamplerNotice(goodsId, sentFolderName, sentGoodsNo, sentProductName, sentInitiator);
    }
}
