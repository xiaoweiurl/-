package com.imagemanager.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能对话 SSE 结束帧。Spring {@code SseEmitter.event().name("message").data(json)}
 * 的线上格式是 {@code event:message\ndata:<json>\n\n}。
 * sources 同时放在结束帧里，前端即使丢掉前面的 sources 事件也能对上 [[E1]]。
 */
public final class ChatSseFrames {

    private ChatSseFrames() {
    }

    public static Map<String, Object> sourcesEvent(List<Map<String, Object>> sources) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "sources");
        event.put("sources", sources == null ? List.of() : sources);
        return event;
    }

    public static Map<String, Object> done(String historyId, String conversationId, List<Map<String, Object>> sources) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "done");
        event.put("sources", sources == null ? List.of() : sources);
        if (historyId != null && !historyId.isBlank()) {
            event.put("historyId", historyId);
        }
        if (conversationId != null && !conversationId.isBlank()) {
            event.put("conversationId", conversationId);
        }
        return event;
    }

    /** 与 SseEmitter.event().name(name).data(json) 相同的分帧。 */
    public static String wire(String eventName, String json) {
        String body = json == null ? "" : json;
        return "event:" + eventName + "\ndata:" + body + "\n\n";
    }
}
