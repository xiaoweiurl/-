package com.imagemanager.dingtalk;

/**
 * 钉钉 API / 配置错误。未配置凭证时应用仍可启动，仅在调用同步时抛出。
 */
public class DingTalkException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public DingTalkException(String message) {
        super(message);
    }

    public DingTalkException(String message, Throwable cause) {
        super(message, cause);
    }

    public static DingTalkException notConfigured() {
        return new DingTalkException(
                "钉钉未配置：请设置环境变量 DINGTALK_APP_KEY 与 DINGTALK_APP_SECRET 后重试");
    }
}
