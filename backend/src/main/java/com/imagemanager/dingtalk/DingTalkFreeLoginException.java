package com.imagemanager.dingtalk;

/**
 * 钉钉免登业务错误（匹配失败、缺少商品编号等）。
 */
public class DingTalkFreeLoginException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final int httpStatus;

    public DingTalkFreeLoginException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public static DingTalkFreeLoginException unmatched() {
        return new DingTalkFreeLoginException(401,
                "未能匹配到中台账号。请在钉钉内打开本链接，或先完成姓名注册。");
    }

    public static DingTalkFreeLoginException goodsRequired() {
        return new DingTalkFreeLoginException(400, "打样免登需要商品编号");
    }
}
