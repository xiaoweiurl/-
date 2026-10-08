package com.imagemanager.imagesearch;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 把对话里的问题分成同款、相似素材，或继续原来的识图。
 * 很短或没有文字时先不拍板，等整图集合里的第一条命中再决定。
 * 「全部 / 所有」只放宽条数，两个检索场景都生效。
 */
public final class VisualSearchClassifier {

    /** 去掉空白和标点后不超过这个长度，视为“只发了一张图”。 */
    static final int SHORT_LIMIT = 8;

    private static final Pattern NOISE = Pattern.compile(
            "[\\s\\p{Punct}，。！？、；：\"'“”‘’（）()\\[\\]【】…·~～]+");

    /** 问的是这款货本身：货号、打样、同款。 */
    private static final String[] PRODUCT = {
            "做过吗", "做过没有", "我们做过", "有没有做过", "做过这",
            "历史打样", "打样记录", "打过样", "以前打过", "找打样",
            "什么货号", "哪个货号", "货号是", "是什么货", "什么款号", "哪个款",
            "搜同款", "找同款", "同款", "这款我们", "像这款", "像这个"
    };

    /** 问的是素材或风格，不按货号合并。 */
    private static final String[] REFERENCE = {
            "找素材", "素材图", "素材", "参考图", "参考素材", "参考",
            "类似风格", "风格", "找图", "搜图", "以图搜图", "像这张"
    };

    /** 只说相似、没有点明货号或素材时，按相似素材。 */
    private static final String[] SIMILAR = {
            "相似", "类似", "近似", "相近"
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

    private VisualSearchClassifier() {
    }

    /**
     * @param scenario 已经能确定时的场景。probe 为 true 时忽略，先去看第一条命中。
     * @param all      用户要求全部结果
     * @param probe    没有足够文字，等检索第一条再决定
     */
    public record Intent(VisualSearchScenario scenario, boolean all, boolean probe) {
        public static Intent search(VisualSearchScenario scenario, boolean all) {
            return new Intent(scenario, all, false);
        }

        public static Intent probe(boolean all) {
            return new Intent(VisualSearchScenario.NON_SEARCH, all, true);
        }

        public static Intent skip() {
            return new Intent(VisualSearchScenario.NON_SEARCH, false, false);
        }
    }

    public static boolean wantsAll(String message) {
        String folded = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        return containsAny(folded, ALL_PHRASES);
    }

    public static Intent classify(String message) {
        String text = message == null ? "" : message.trim();
        String folded = text.toLowerCase(Locale.ROOT);
        boolean product = containsAny(folded, PRODUCT);
        boolean reference = containsAny(folded, REFERENCE);
        boolean similar = containsAny(folded, SIMILAR);
        boolean nonVisual = containsAny(folded, NON_VISUAL);
        boolean all = containsAny(folded, ALL_PHRASES);
        if (product) {
            return Intent.search(VisualSearchScenario.SAME_PRODUCT, all);
        }
        if (reference || similar) {
            return Intent.search(VisualSearchScenario.SIMILAR_REFERENCE, all);
        }
        if (nonVisual) {
            return Intent.skip();
        }
        if (isShort(text)) {
            return Intent.probe(all);
        }
        return Intent.skip();
    }

    /**
     * 短文本的默认：整图集合里相似度最高的一条是商品，并且不低于阈值，就当同款；否则当相似素材。
     * 探测只用整图集合。裁剪集合可能还没回填，不能拿它决定场景。
     */
    public static VisualSearchScenario fromProbe(java.util.List<ImageSearchModels.ImageSearchHitView> hits, double minScore) {
        if (hits == null || hits.isEmpty()) {
            return VisualSearchScenario.SIMILAR_REFERENCE;
        }
        ImageSearchModels.ImageSearchHitView top = hits.get(0);
        if (top != null
                && ImageSearchFilters.SOURCE_GOODS.equals(top.getSource())
                && !Float.isNaN(top.getScore())
                && top.getScore() >= (float) minScore) {
            return VisualSearchScenario.SAME_PRODUCT;
        }
        return VisualSearchScenario.SIMILAR_REFERENCE;
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
