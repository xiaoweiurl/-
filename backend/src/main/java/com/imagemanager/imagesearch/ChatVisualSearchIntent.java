package com.imagemanager.imagesearch;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 对话里要不要拿上传图做以图搜图。
 * 智能对话的意图步骤本身就是关键词（例如找图、供应链），这里沿用同一做法，不额外打一轮模型。
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
    static final int SHORT_LIMIT = 8;

    private static final Pattern NOISE = Pattern.compile(
            "[\\s\\p{Punct}，。！？、；：\"'“”‘’（）()\\[\\]【】…·~～]+");

    private static final String[] SIMILAR = {
            "相似", "类似", "近似", "同款", "相近",
            "做过吗", "做过没有", "我们做过", "有没有做过", "做过这",
            "历史打样", "打样记录", "打过样", "以前打过", "找打样",
            "什么货号", "哪个货号", "货号是", "是什么货", "什么款号", "哪个款",
            "以图搜图", "搜同款", "找同款", "找素材", "素材图", "找图", "搜图",
            "像这款", "像这个", "像这张", "这款我们"
    };

    private static final String[] NON_VISUAL = {
            "文字", "读字", "识别字", "ocr", "写了什么", "写着什么", "上面写", "图上写",
            "瑕疵", "缺陷", "破洞", "色差", "脏污", "质量问题", "次品",
            "面料", "布料", "成分", "克重", "纱支", "织法", "材质",
            "翻译", "描述一下", "描述这", "分析工艺", "分析一下"
    };

    private static final String[] ALL_PHRASES = {
            "全部", "所有", "都列出来", "不限数量", "有多少列多少", "不要限制"
    };

    private ChatVisualSearchIntent() {
    }

    public static Decision decide(String message) {
        String text = message == null ? "" : message.trim();
        String folded = text.toLowerCase(Locale.ROOT);
        boolean similar = containsAny(folded, SIMILAR);
        if (containsAny(folded, NON_VISUAL) && !similar) {
            return Decision.SKIP;
        }
        if (similar || isShort(text)) {
            return wantsAll(folded) ? Decision.ALL : Decision.TOP;
        }
        return Decision.SKIP;
    }

    public static boolean wantsAll(String message) {
        String folded = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        return containsAny(folded, ALL_PHRASES);
    }

    static boolean isShort(String message) {
        String text = message == null ? "" : message.trim();
        return NOISE.matcher(text).replaceAll("").length() <= SHORT_LIMIT;
    }

    private static boolean containsAny(String text, String[] phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
