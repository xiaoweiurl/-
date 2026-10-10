package com.imagemanager.imagesearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 图和文字一起出现时，文字按条件解释，不再替代图片向量。
 * 本地对话模型是整段生成，没有便宜的字段抽取，所以这里只跑确定性规则。
 */
@Slf4j
@Service
public class ImageSearchConditionApplier {

    private final ImageSearchProperties properties;
    private final ImageSearchCondition.Lexicon lexicon;
    private final ImageEmbedder embedder;
    private final ImageVectorIndex vectorIndex;
    private final Clock clock;

    /**
     * Spring 注入构造器。必须标 {@code @Autowired}：同包还有一个带 {@link Clock} 的测试构造器，
     * Spring 不会把这个 4 参构造器当成唯一构造器，否则会退回不存在的无参 {@code <init>()}。
     */
    @Autowired
    public ImageSearchConditionApplier(ImageSearchProperties properties,
                                       ImageSearchCondition.Lexicon lexicon,
                                       ImageEmbedder embedder,
                                       ImageVectorIndex vectorIndex) {
        this(properties, lexicon, embedder, vectorIndex, Clock.system(ImageSearchConditionParser.ZONE));
    }

    ImageSearchConditionApplier(ImageSearchProperties properties,
                                ImageSearchCondition.Lexicon lexicon,
                                ImageEmbedder embedder,
                                ImageVectorIndex vectorIndex,
                                Clock clock) {
        this.properties = properties;
        this.lexicon = lexicon == null ? ImageSearchCondition.Lexicon.EMPTY : lexicon;
        this.embedder = embedder;
        this.vectorIndex = vectorIndex;
        this.clock = clock;
    }

    public ImageSearchCondition.Parsed parse(String text, String exclude) {
        if (text == null || text.isBlank()) {
            return ImageSearchCondition.Parsed.empty();
        }
        try {
            return ImageSearchConditionParser.parse(text, lexicon, clock, exclude);
        } catch (Exception e) {
            log.warn("图片条件解析失败，按无条件检索: {}", e.getMessage());
            return ImageSearchCondition.Parsed.empty();
        }
    }

    public ImageSearchConditionRank.Outcome apply(List<ImageSearchModels.ImageSearchHitView> hits,
                                                  ImageSearchCondition.Parsed parsed,
                                                  EmbeddingVariant variant) {
        if (parsed == null || !parsed.active()) {
            return ImageSearchConditionRank.Outcome.unchanged(hits);
        }
        float[] textVector = null;
        Map<String, float[]> vectors = Map.of();
        if (parsed.hasAttributes()) {
            try {
                textVector = embedder.embedText(parsed.attributeText());
                vectors = vectorIndex.embeddings(variant == null ? EmbeddingVariant.FULL : variant, vectorIds(hits));
            } catch (Exception e) {
                log.warn("文字重排跳过，保留图片相似度: {}", e.getMessage());
                textVector = null;
            }
        }
        return ImageSearchConditionRank.apply(
                hits,
                parsed,
                vectors,
                textVector,
                properties.getTextRerankImageWeight(),
                properties.getTextRerankTextWeight(),
                properties.getTextScoreFloor(),
                properties.getTextScoreMargin());
    }

    public int widen(int fetch, ImageSearchCondition.Parsed parsed) {
        if (parsed == null || !parsed.needsPostFilter()) {
            return fetch;
        }
        int factor = properties.getConditionFetchFactor();
        if (factor < 1) {
            factor = 1;
        }
        return VisualSearchLimits.capInternal(Math.max(fetch, 1) * factor);
    }

    private static List<String> vectorIds(List<ImageSearchModels.ImageSearchHitView> hits) {
        List<String> ids = new ArrayList<>();
        if (hits == null) {
            return ids;
        }
        for (ImageSearchModels.ImageSearchHitView hit : hits) {
            if (hit != null && hit.getVectorId() != null && !hit.getVectorId().isBlank()) {
                ids.add(hit.getVectorId());
            }
        }
        return ids;
    }
}
