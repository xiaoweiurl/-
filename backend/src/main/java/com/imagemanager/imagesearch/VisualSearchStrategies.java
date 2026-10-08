package com.imagemanager.imagesearch;

import java.util.EnumMap;
import java.util.Map;

/**
 * 场景到策略的唯一入口。
 */
public final class VisualSearchStrategies {

    private static final Map<VisualSearchScenario, VisualSearchStrategy> STRATEGIES = new EnumMap<>(VisualSearchScenario.class);

    static {
        register(new SameProductStrategy());
        register(new SimilarReferenceStrategy());
        register(new MixedCatalogStrategy());
    }

    private VisualSearchStrategies() {
    }

    private static void register(VisualSearchStrategy strategy) {
        STRATEGIES.put(strategy.scenario(), strategy);
    }

    public static VisualSearchStrategy of(VisualSearchScenario scenario) {
        VisualSearchStrategy strategy = STRATEGIES.get(scenario);
        if (strategy == null) {
            throw new IllegalArgumentException("场景 " + scenario + " 没有检索策略");
        }
        return strategy;
    }
}
