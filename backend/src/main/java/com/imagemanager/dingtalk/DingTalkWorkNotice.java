package com.imagemanager.dingtalk;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钉钉工作通知消息体（企业内部应用 asyncsend_v2 的 {@code msg} 字段）。
 * 有落地链接时使用 action_card，否则退化为 text。
 * <p>
 * 发出后无法按 task_id 更新 markdown 字段；回填请另发一封工作通知。
 */
public final class DingTalkWorkNotice {

    private final String title;
    private final String body;
    private final String singleTitle;
    private final String singleUrl;

    private DingTalkWorkNotice(String title, String body, String singleTitle, String singleUrl) {
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.singleTitle = singleTitle;
        this.singleUrl = singleUrl;
    }

    public static DingTalkWorkNotice text(String title, String body) {
        return new DingTalkWorkNotice(title, body, null, null);
    }

    public static DingTalkWorkNotice actionCard(String title, String markdown,
                                                String singleTitle, String singleUrl) {
        return new DingTalkWorkNotice(title, markdown, singleTitle, singleUrl);
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public String getSingleTitle() {
        return singleTitle;
    }

    public String getSingleUrl() {
        return singleUrl;
    }

    public boolean hasLink() {
        return singleUrl != null && !singleUrl.isBlank();
    }

    public Map<String, Object> toMsgMap() {
        Map<String, Object> msg = new LinkedHashMap<>();
        if (hasLink()) {
            msg.put("msgtype", "action_card");
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("title", title);
            card.put("markdown", body);
            card.put("single_title",
                    (singleTitle == null || singleTitle.isBlank()) ? "查看详情" : singleTitle);
            card.put("single_url", singleUrl);
            msg.put("action_card", card);
        } else {
            msg.put("msgtype", "text");
            Map<String, Object> text = new LinkedHashMap<>();
            String content = title.isBlank() ? body : title + "\n" + body;
            text.put("content", content);
            msg.put("text", text);
        }
        return msg;
    }
}
