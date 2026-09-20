package com.imagemanager.service;

/**
 * 打样员工作通知投递结果，便于单测断言 send-or-skip。
 */
public final class SamplerNoticeResult {

    public enum Status {
        SENT,
        PENDING,
        SKIPPED_BLANK,
        SKIPPED_UNCHANGED,
        SKIPPED_DISABLED,
        SKIPPED_NO_USERID,
        SKIPPED_AMBIGUOUS,
        SKIPPED_NO_ASSIGNMENT,
        SKIPPED_ALREADY_FOLLOWED_UP,
        SKIPPED_NO_BACKFILL,
        FAILED
    }

    public static final String KIND_ASSIGNMENT = "ASSIGNMENT";
    public static final String KIND_FOLLOWUP = "FOLLOWUP";
    public static final String KIND_CANCEL = "CANCEL";

    private final Status status;
    private final String dingUserId;
    private final Long taskId;
    private final String message;

    private SamplerNoticeResult(Status status, String dingUserId, Long taskId, String message) {
        this.status = status;
        this.dingUserId = dingUserId;
        this.taskId = taskId;
        this.message = message;
    }

    public static SamplerNoticeResult sent(String dingUserId, long taskId) {
        return new SamplerNoticeResult(Status.SENT, dingUserId, taskId, "ok");
    }

    public static SamplerNoticeResult pending(String message) {
        return new SamplerNoticeResult(Status.PENDING, null, null, message);
    }

    public static SamplerNoticeResult skipped(Status status, String message) {
        return new SamplerNoticeResult(status, null, null, message);
    }

    public static SamplerNoticeResult failed(String dingUserId, String message) {
        return new SamplerNoticeResult(Status.FAILED, dingUserId, null, message);
    }

    public Status getStatus() {
        return status;
    }

    public String getDingUserId() {
        return dingUserId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public String getMessage() {
        return message;
    }
}
