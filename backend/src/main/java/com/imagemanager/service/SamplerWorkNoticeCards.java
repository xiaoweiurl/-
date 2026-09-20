package com.imagemanager.service;

/**
 * 打样工作通知 ActionCard 文案。钉钉工作通知 markdown 仅稳定支持标题、加粗、列表、链接；
 * 不用引用/分隔线/HTML，避免客户端显示原文符号。
 */
public final class SamplerWorkNoticeCards {

    public static final String HEAD_ASSIGN = "打样任务";
    public static final String HEAD_FOLLOWUP = "打样信息已更新";
    public static final String HEAD_CANCEL = "打样任务已改派";
    public static final String CTA_ASSIGN = "立即填写打样表单";
    public static final String CTA_FOLLOWUP = "查看打样表单";
    public static final String PENDING_VALUE = "待填写";
    public static final String UNNAMED_HEADLINE = "待完善货号与品名";
    public static final String UNNAMED_FOLDER = "未命名商品";
    private static final int TITLE_MAX = 64;

    private SamplerWorkNoticeCards() {
    }

    public static String assignmentSessionTitle(GoodsSamplerNotice goods) {
        return truncate(HEAD_ASSIGN + " · " + productHeadline(goods), TITLE_MAX);
    }

    public static String followupSessionTitle(GoodsSamplerNotice goods) {
        return truncate(HEAD_FOLLOWUP + " · " + productHeadline(goods), TITLE_MAX);
    }

    public static String cancelSessionTitle(GoodsSamplerNotice goods) {
        return truncate(HEAD_CANCEL + " · " + productHeadline(goods), TITLE_MAX);
    }

    public static String assignmentMarkdown(String samplerName, GoodsSamplerNotice goods, String formHttp) {
        String headline = productHeadline(goods);
        String lead = hasIdentity(goods)
                ? "您被指定为该商品的打样员。"
                : "您被指定为打样员。请打开表单补全货号和品名。";
        return cardMarkdown(HEAD_ASSIGN, headline, lead, samplerName, goods, formHttp, CTA_ASSIGN,
                "请及时完善打样信息。");
    }

    public static String followupMarkdown(String samplerName, GoodsSamplerNotice goods, String formHttp) {
        String headline = productHeadline(goods);
        return cardMarkdown(HEAD_FOLLOWUP, headline, "货号、品名等已填写，可打开表单核对或继续补充。",
                samplerName, goods, formHttp, CTA_FOLLOWUP, "如需修改请再次打开表单。");
    }

    public static String cancelMarkdown(String previousSampler, String nextSampler, GoodsSamplerNotice goods) {
        String next = fieldValue(nextSampler);
        String lead = "该任务已改派给「" + next + "」，您无需继续填写。";
        return cardMarkdown(HEAD_CANCEL, productHeadline(goods), lead,
                previousSampler, goods, null, null, "如有疑问请联系发起人。");
    }

    public static String toPlainText(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return markdown
                .replace("**", "")
                .replace("### ", "")
                .replace("#### ", "")
                .trim();
    }

    public static String productHeadline(GoodsSamplerNotice goods) {
        if (goods == null) {
            return UNNAMED_HEADLINE;
        }
        String no = oneLine(goods.getGoodsNo());
        String name = oneLine(goods.getProductName());
        if (!no.isEmpty() && !name.isEmpty()) {
            return sanitize(no + " " + name);
        }
        if (!no.isEmpty()) {
            return sanitize(no);
        }
        if (!name.isEmpty()) {
            return sanitize(name);
        }
        String folder = oneLine(goods.getFolderName());
        if (!folder.isEmpty() && !UNNAMED_FOLDER.equals(folder)) {
            return sanitize(folder);
        }
        return UNNAMED_HEADLINE;
    }

    public static boolean hasIdentity(GoodsSamplerNotice goods) {
        if (goods == null) {
            return false;
        }
        return !oneLine(goods.getGoodsNo()).isEmpty() || !oneLine(goods.getProductName()).isEmpty();
    }

    /**
     * 当前商品相对指派当时的卡片是否补全了货号/品名/发起人（原通知会显示「待填写」/未命名）。
     */
    public static boolean needsBackfill(GoodsSamplerNotice sent, GoodsSamplerNotice current) {
        if (current == null) {
            return false;
        }
        if (!hasIdentity(current) && !filled(current.getInitiator())) {
            return false;
        }
        if (sent == null) {
            return hasIdentity(current);
        }
        return newlyFilled(sent.getGoodsNo(), current.getGoodsNo())
                || newlyFilled(sent.getProductName(), current.getProductName())
                || newlyFilled(sent.getInitiator(), current.getInitiator())
                || (!hasIdentity(sent) && hasIdentity(current));
    }

    public static String fieldValue(String raw) {
        String value = oneLine(raw);
        return value.isEmpty() ? PENDING_VALUE : sanitize(value);
    }

    static boolean newlyFilled(String previous, String current) {
        return !filled(previous) && filled(current);
    }

    static boolean filled(String raw) {
        String value = oneLine(raw);
        return !value.isEmpty() && !UNNAMED_FOLDER.equals(value) && !PENDING_VALUE.equals(value);
    }

    private static String cardMarkdown(String head, String headline, String lead,
                                      String samplerName, GoodsSamplerNotice goods,
                                      String formHttp, String cta, String closing) {
        StringBuilder md = new StringBuilder();
        md.append("### ").append(head).append("\n\n");
        md.append("**").append(headline).append("**\n\n");
        md.append(lead).append("\n\n");
        md.append("- **货号：** ").append(fieldValue(goods == null ? null : goods.getGoodsNo())).append("\n");
        md.append("- **品名：** ").append(fieldValue(goods == null ? null : goods.getProductName())).append("\n");
        md.append("- **发起人：** ").append(fieldValue(goods == null ? null : goods.getInitiator())).append("\n");
        md.append("- **打样员：** ").append(fieldValue(samplerName));
        appendOptional(md, "客户", goods == null ? null : goods.getCustomer());
        appendOptional(md, "订单号", goods == null ? null : goods.getOrderNo());
        md.append("\n\n").append(closing);
        if (formHttp != null && !formHttp.isBlank()) {
            md.append("\n\n[").append(cta).append("](").append(formHttp.trim()).append(")");
        }
        return md.toString();
    }

    private static void appendOptional(StringBuilder md, String label, String raw) {
        String value = oneLine(raw);
        if (value.isEmpty()) {
            return;
        }
        md.append("\n- **").append(label).append("：** ").append(sanitize(value));
    }

    static String oneLine(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace('\\', '＼')
                .replace('*', '＊')
                .replace('[', '［')
                .replace(']', '］')
                .replace('`', '\'');
    }

    static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        if (max <= 1) {
            return text.substring(0, Math.max(0, max));
        }
        return text.substring(0, max - 1) + "…";
    }
}
