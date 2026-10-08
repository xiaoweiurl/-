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
 * 同款和相似素材分属两个策略；短文本先看整图集合的第一条再决定。
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
        VisualSearchClassifier.Intent intent = VisualSearchClassifier.classify(message);
        if (!intent.probe() && intent.scenario() == VisualSearchScenario.NON_SEARCH) {
            return Outcome.none();
        }
        if (!properties.isEnabled()) {
            log.warn("对话以图搜图未执行：image-search.enabled=false，继续原有识图");
            return Outcome.none();
        }
        int topK = clamp(properties.getChatTopK(), 1, 20);
        int maxResults = clamp(properties.getChatMaxResults(), topK, 50);
        double minScore = clampScore(properties.getChatMinScore());
        int limit = intent.all() ? maxResults : topK;
        boolean cropReady = queryService.cropCollectionReady();

        List<ImageSearchModels.ImageSearchHitView> merged = new ArrayList<>();
        int searched = 0;
        int seen = 0;
        VisualSearchScenario scenario = intent.probe() ? null : intent.scenario();
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
            String filename = "chat-query-" + seen + ".jpg";
            try {
                if (scenario == null) {
                    scenario = probeAndCollect(bytes, filename, company, limit, cropReady, merged);
                } else {
                    collect(scenario, bytes, filename, company, limit, cropReady, merged);
                }
                searched++;
            } catch (Exception ex) {
                log.warn("对话以图搜图失败，继续原有识图: {}", ex.getMessage());
            }
        }
        if (searched == 0 || scenario == null) {
            return Outcome.none();
        }
        VisualSearchStrategy strategy = VisualSearchStrategies.of(scenario);
        List<ImageSearchModels.ImageSearchHitView> kept = strategy.assemble(merged, minScore, limit, properties);
        for (ImageSearchModels.ImageSearchHitView hit : kept) {
            hit.setScenario(scenario.name());
        }
        return Outcome.ran(ChatVisualSearchRank.context(kept), ChatVisualSearchRank.toSources(kept, scenario));
    }

    /**
     * 短文本先用整图集合看第一条。决定后的场景如果也用整图，这次结果直接留下，不再查第二次。
     */
    private VisualSearchScenario probeAndCollect(byte[] bytes, String filename, String company, int limit,
                                                 boolean cropReady, List<ImageSearchModels.ImageSearchHitView> merged) {
        int probeFetch = VisualSearchLimits.capInternal(Math.max(limit * Math.max(properties.getInternalTopKFactor(), 1), 20));
        List<ImageSearchModels.ImageSearchHitView> probeHits = queryService.collect(
                bytes, filename, null, "all", probeFetch, company, EmbeddingVariant.FULL);
        VisualSearchScenario scenario = VisualSearchClassifier.fromProbe(probeHits, properties.getSameProductProbeMinScore());
        EmbeddingVariant variant = EmbeddingVariantSelector.select(scenario, properties, cropReady);
        if (variant == EmbeddingVariant.FULL) {
            merged.addAll(probeHits);
            return scenario;
        }
        collect(scenario, bytes, filename, company, limit, cropReady, merged);
        return scenario;
    }

    private void collect(VisualSearchScenario scenario, byte[] bytes, String filename, String company, int limit,
                         boolean cropReady, List<ImageSearchModels.ImageSearchHitView> merged) {
        VisualSearchStrategy strategy = VisualSearchStrategies.of(scenario);
        EmbeddingVariant variant = EmbeddingVariantSelector.select(scenario, properties, cropReady);
        merged.addAll(queryService.collect(
                bytes, filename, null, "all", strategy.internalTopK(limit, properties), company, variant));
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

    private static double clampScore(double minScore) {
        if (minScore < 0d) {
            return 0d;
        }
        if (minScore > 1d) {
            return 1d;
        }
        return minScore;
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
