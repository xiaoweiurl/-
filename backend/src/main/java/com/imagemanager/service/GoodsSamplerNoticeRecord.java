package com.imagemanager.service;

/**
 * 最近一次打样指派工作通知（用于表单保存后判断是否需要补发回填卡，以及商品页展示投递状态）。
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
    private final String lastStatus;
    private final String lastMessage;
    private final String lastKind;
    private final boolean persistOk;

    public GoodsSamplerNoticeRecord(long goodsId, String dingUserId, String samplerName, long taskId,
                                    String sentFolderName, String sentGoodsNo, String sentProductName,
                                    String sentInitiator, Long followupTaskId) {
        this(goodsId, dingUserId, samplerName, taskId, sentFolderName, sentGoodsNo, sentProductName,
                sentInitiator, followupTaskId, SamplerNoticeResult.Status.SENT.name(), "ok",
                SamplerNoticeResult.KIND_ASSIGNMENT, true);
    }

    public GoodsSamplerNoticeRecord(long goodsId, String dingUserId, String samplerName, long taskId,
                                    String sentFolderName, String sentGoodsNo, String sentProductName,
                                    String sentInitiator, Long followupTaskId,
                                    String lastStatus, String lastMessage, String lastKind, boolean persistOk) {
        this.goodsId = goodsId;
        this.dingUserId = dingUserId == null ? "" : dingUserId;
        this.samplerName = samplerName;
        this.taskId = taskId;
        this.sentFolderName = sentFolderName;
        this.sentGoodsNo = sentGoodsNo;
        this.sentProductName = sentProductName;
        this.sentInitiator = sentInitiator;
        this.followupTaskId = followupTaskId;
        this.lastStatus = lastStatus == null || lastStatus.isBlank()
                ? SamplerNoticeResult.Status.SENT.name() : lastStatus;
        this.lastMessage = lastMessage == null ? "" : lastMessage;
        this.lastKind = lastKind == null || lastKind.isBlank()
                ? SamplerNoticeResult.KIND_ASSIGNMENT : lastKind;
        this.persistOk = persistOk;
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

    public String getLastStatus() {
        return lastStatus;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public String getLastKind() {
        return lastKind;
    }

    public boolean isPersistOk() {
        return persistOk;
    }

    public boolean hasFollowup() {
        return followupTaskId != null && followupTaskId > 0;
    }

    public boolean hasAssignment() {
        return taskId > 0 && dingUserId != null && !dingUserId.isBlank();
    }

    public GoodsSamplerNotice toSentGoods() {
        return new GoodsSamplerNotice(goodsId, sentFolderName, sentGoodsNo, sentProductName, sentInitiator);
    }

    public GoodsSamplerNoticeRecord withFollowup(long nextFollowupTaskId) {
        return new GoodsSamplerNoticeRecord(
                goodsId, dingUserId, samplerName, taskId,
                sentFolderName, sentGoodsNo, sentProductName, sentInitiator,
                nextFollowupTaskId,
                SamplerNoticeResult.Status.SENT.name(), "ok",
                SamplerNoticeResult.KIND_FOLLOWUP, true);
    }
}
