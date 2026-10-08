package com.imagemanager.imagesearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 对话上传图片后的以图搜图。素材和商品库一起查。
 * 开关关闭、向量服务不可用、或打样会话时不搜，对话继续走原来的识图。
 * 同款和相似素材分属两个策略；短文本先看整图集合的第一条再决定。
 * 图片附带的文字条件会收窄结果，并写进给模型的摘要。
 */
@Slf4j
@Service
public class ChatVisualSearchService {

    private static final int MAX_QUERY_IMAGES = 5;

    private final ImageSearchProperties properties;
    private final ImageSearchQueryService queryService;
    private final ImageSearchConditionApplier conditions;
    private final ImageSearchRecordLinks recordLinks;

    public ChatVisualSearchService(ImageSearchProperties properties, ImageSearchQueryService queryService) {
        this(properties, queryService, null);
    }

    public ChatVisualSearchService(ImageSearchProperties properties,
                                   ImageSearchQueryService queryService,
                                   ImageSearchConditionApplier conditions) {
        this(properties, queryService, conditions, null);
    }

    /**
     * Spring 注入构造器。必须标 {@code @Autowired}：同类还有给测试用的短构造器，
     * 不标明时 Spring 会退回不存在的无参构造。
     */
    @Autowired
    public ChatVisualSearchService(ImageSearchProperties properties,
                                   ImageSearchQueryService queryService,
                                   ImageSearchConditionApplier conditions,
                                   ImageSearchRecordLinks recordLinks) {
        this.properties = properties;
        this.queryService = queryService;
        this.conditions = conditions;
        this.recordLinks = recordLinks;
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
        ImageSearchCondition.Parsed parsed = conditions == null
                ? ImageSearchCondition.Parsed.empty()
                : conditions.parse(message, null);
        String scope = parsed.scope() != null ? parsed.scope() : "all";

        Gathered gathered = gather(intent, imageBase64, company, limit, cropReady, scope, parsed);
        if (gathered.searched() == 0 || gathered.scenario() == null) {
            return Outcome.none();
        }
        ImageSearchConditionRank.Outcome filtered = apply(gathered.hits(), parsed, gathered.variant());
        if (filtered.hits().isEmpty() && parsed.scope() != null) {
            Gathered wider = gather(intent, imageBase64, company, limit, cropReady, "all", parsed.withoutScope());
            if (wider.searched() > 0 && wider.scenario() != null) {
                ImageSearchConditionRank.Outcome relaxed = apply(wider.hits(), parsed.withoutScope(), wider.variant());
                if (!relaxed.hits().isEmpty()) {
                    filtered = ImageSearchConditionRank.relaxScope(relaxed, scopeChip(parsed));
                    gathered = wider;
                }
            }
        }
        VisualSearchStrategy strategy = VisualSearchStrategies.of(gathered.scenario());
        List<ImageSearchModels.ImageSearchHitView> kept = strategy.assemble(filtered.hits(), minScore, limit, properties);
        for (ImageSearchModels.ImageSearchHitView hit : kept) {
            hit.setScenario(gathered.scenario().name());
        }
        if (recordLinks != null) {
            recordLinks.attach(kept, company);
        }
        String summary = ImageSearchConditionRank.summary(filtered.filters(), filtered.notice());
        String context = ChatVisualSearchRank.context(kept);
        if (!summary.isBlank()) {
            context = context + "筛选说明：" + summary + "\n回答时要提到这些条件，不要编造条件里没有的限制。\n";
        }
        List<Map<String, Object>> sources = ChatVisualSearchRank.toSources(kept, gathered.scenario());
        ImageSearchConditionRank.attachSources(sources, filtered);
        return Outcome.ran(context, sources);
    }

    private Gathered gather(VisualSearchClassifier.Intent intent, List<String> imageBase64,
                            String company, int limit, boolean cropReady, String scope,
                            ImageSearchCondition.Parsed parsed) {
        List<ImageSearchModels.ImageSearchHitView> merged = new ArrayList<>();
        int searched = 0;
        int seen = 0;
        VisualSearchScenario scenario = intent.probe() ? null : intent.scenario();
        EmbeddingVariant variant = EmbeddingVariant.FULL;
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
                    Probed probed = probeAndCollect(bytes, filename, company, limit, cropReady, scope, parsed);
                    scenario = probed.scenario();
                    variant = probed.variant();
                    merged.addAll(probed.hits());
                } else {
                    variant = EmbeddingVariantSelector.select(scenario, properties, cropReady);
                    merged.addAll(collect(scenario, bytes, filename, company, limit, cropReady, scope, parsed));
                }
                searched++;
            } catch (Exception ex) {
                log.warn("对话以图搜图失败，继续原有识图: {}", ex.getMessage());
            }
        }
        return new Gathered(merged, searched, scenario, variant);
    }

    /**
     * 短文本先用整图集合看第一条。决定后的场景如果也用整图，这次结果直接留下，不再查第二次。
     */
    private Probed probeAndCollect(byte[] bytes, String filename, String company, int limit,
                                   boolean cropReady, String scope, ImageSearchCondition.Parsed parsed) {
        int factor = Math.max(properties.getInternalTopKFactor(), 1);
        int probeFetch = VisualSearchLimits.capInternal(Math.max(limit * factor, 20));
        probeFetch = widen(probeFetch, parsed);
        List<ImageSearchModels.ImageSearchHitView> probeHits = queryService.collect(
                bytes, filename, null, scope, probeFetch, company, EmbeddingVariant.FULL);
        VisualSearchScenario scenario = VisualSearchClassifier.fromProbe(probeHits, properties.getSameProductProbeMinScore());
        EmbeddingVariant variant = EmbeddingVariantSelector.select(scenario, properties, cropReady);
        if (variant == EmbeddingVariant.FULL) {
            return new Probed(scenario, variant, probeHits);
        }
        return new Probed(scenario, variant, collect(scenario, bytes, filename, company, limit, cropReady, scope, parsed));
    }

    private List<ImageSearchModels.ImageSearchHitView> collect(VisualSearchScenario scenario, byte[] bytes,
                                                               String filename, String company, int limit,
                                                               boolean cropReady, String scope,
                                                               ImageSearchCondition.Parsed parsed) {
        VisualSearchStrategy strategy = VisualSearchStrategies.of(scenario);
        EmbeddingVariant variant = EmbeddingVariantSelector.select(scenario, properties, cropReady);
        int fetch = widen(strategy.internalTopK(limit, properties), parsed);
        return queryService.collect(bytes, filename, null, scope, fetch, company, variant);
    }

    private ImageSearchConditionRank.Outcome apply(List<ImageSearchModels.ImageSearchHitView> hits,
                                                   ImageSearchCondition.Parsed parsed,
                                                   EmbeddingVariant variant) {
        if (conditions == null || parsed == null || !parsed.active()) {
            return ImageSearchConditionRank.Outcome.unchanged(hits);
        }
        return conditions.apply(hits, parsed, variant);
    }

    private int widen(int fetch, ImageSearchCondition.Parsed parsed) {
        if (conditions == null) {
            return fetch;
        }
        return conditions.widen(fetch, parsed);
    }

    private static ImageSearchCondition.Chip scopeChip(ImageSearchCondition.Parsed parsed) {
        if (parsed == null || parsed.chips() == null) {
            return null;
        }
        for (ImageSearchCondition.Chip chip : parsed.chips()) {
            if ("scope".equals(chip.id())) {
                return chip;
            }
        }
        return null;
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

    private record Gathered(List<ImageSearchModels.ImageSearchHitView> hits, int searched,
                            VisualSearchScenario scenario, EmbeddingVariant variant) {
    }

    private record Probed(VisualSearchScenario scenario, EmbeddingVariant variant,
                          List<ImageSearchModels.ImageSearchHitView> hits) {
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
