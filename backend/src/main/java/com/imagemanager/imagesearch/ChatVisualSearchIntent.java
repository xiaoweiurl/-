package com.imagemanager.imagesearch;

/**
 * 对话里要不要拿上传图做以图搜图。
 * 具体分成同款还是相似素材，见 {@link VisualSearchClassifier}。
 * 读字、看瑕疵、分析面料这类问题留给原来的识图。
 */
public final class ChatVisualSearchIntent {

    public enum Decision {
        /** 不搜，继续原来的识图。 */
        SKIP,
        /** 只返回相似度最高的几条。 */
        TOP,
        /** 用户明确要全部结果，放到上限为止。 */
        ALL
    }

    /** 去掉空白和标点后不超过这个长度，视为“只发了一张图”。 */
    static final int SHORT_LIMIT = VisualSearchClassifier.SHORT_LIMIT;

    private ChatVisualSearchIntent() {
    }

    public static Decision decide(String message) {
        VisualSearchClassifier.Intent intent = VisualSearchClassifier.classify(message);
        if (intent.probe()) {
            return intent.all() ? Decision.ALL : Decision.TOP;
        }
        if (intent.scenario() == VisualSearchScenario.NON_SEARCH) {
            return Decision.SKIP;
        }
        return intent.all() ? Decision.ALL : Decision.TOP;
    }

    public static boolean wantsAll(String message) {
        return VisualSearchClassifier.wantsAll(message);
    }

    static boolean isShort(String message) {
        return VisualSearchClassifier.isShort(message);
    }
}
