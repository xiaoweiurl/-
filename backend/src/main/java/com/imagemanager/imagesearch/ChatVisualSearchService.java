package com.imagemanager.imagesearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 对话上传图片后的以图搜图。素材和商品库一起查。
 * 开关关闭、向量服务不可用、或打样会话时不搜，对话继续走原来的识图。
 */
@Slf4j
@Service
public class ChatVisualSearchService {

    private static final int MAX_QUERY_IMAGES = 5;

    private final ImageSearchProperties properties;
    private final ImageSearchQueryService queryService;

    public ChatVisualSearchService(ImageSearchProperties properties, ImageSearchQueryService queryService) {
        this.properties = properties;
        this.queryService = queryService;
    }

    public Outcome search(String message, List<String> imageBase64, String company, boolean allowed) {
        if (!allowed) {
            log.debug("打样会话不执行以图搜图");
            return Outcome.none();
        }
        if (imageBase64 == null || imageBase64.isEmpty()) {
            return Outcome.none();
        }
        ChatVisualSearchIntent.Decision decision = ChatVisualSearchIntent.decide(message);
        if (decision == ChatVisualSearchIntent.Decision.SKIP) {
            return Outcome.none();
        }
        if (!properties.isEnabled()) {
            log.warn("对话以图搜图未执行：image-search.enabled=false，继续原有识图");
            return Outcome.none();
        }
        int topK = clamp(properties.getChatTopK(), 1, 20);
        int maxResults = clamp(properties.getChatMaxResults(), topK, 50);
        double minScore = properties.getChatMinScore();
        if (minScore < 0d) {
            minScore = 0d;
        } else if (minScore > 1d) {
            minScore = 1d;
        }
        int limit = decision == ChatVisualSearchIntent.Decision.ALL ? maxResults : topK;
        int fetch = Math.min(50, decision == ChatVisualSearchIntent.Decision.ALL
                ? maxResults
                : Math.max(topK * 4, 20));

        List<ImageSearchModels.ImageSearchHitView> merged = new ArrayList<>();
        int searched = 0;
        int seen = 0;
        for (String raw : imageBase64) {
            if (seen >= MAX_QUERY_IMAGES) {
                break;
            }
            byte[] bytes = decode(raw);
            if (bytes == null) {
                log.warn("对话以图搜图跳过无法解析的图片，继续原有识图");
                continue;
            }
            seen++;
            try {
                ImageSearchModels.ImageSearchResponse response = queryService.search(
                        bytes, "chat-query-" + seen + ".jpg", null, "all", fetch, company);
                searched++;
                if (response.getResults() != null) {
                    merged.addAll(response.getResults());
                }
            } catch (Exception ex) {
                log.warn("对话以图搜图失败，继续原有识图: {}", ex.getMessage());
            }
        }
        if (searched == 0) {
            return Outcome.none();
        }
        List<ImageSearchModels.ImageSearchHitView> kept = ChatVisualSearchRank.select(merged, minScore, limit);
        return Outcome.ran(ChatVisualSearchRank.context(kept), ChatVisualSearchRank.toSources(kept));
    }

    static byte[] decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String data = raw.trim();
        int comma = data.indexOf(',');
        if (data.startsWith("data:") && comma >= 0) {
            data = data.substring(comma + 1);
        }
        data = data.replaceAll("\\s", "");
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            if (bytes.length < 32) {
                return null;
            }
            return bytes;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public record Outcome(boolean ran, String context, List<Map<String, Object>> sources) {
        public static Outcome none() {
            return new Outcome(false, "", List.of());
        }

        public static Outcome ran(String context, List<Map<String, Object>> sources) {
            return new Outcome(true, context == null ? "" : context,
                    sources == null ? List.of() : List.copyOf(sources));
        }
    }
}
