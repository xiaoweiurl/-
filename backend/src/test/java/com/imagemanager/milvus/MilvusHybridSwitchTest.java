package com.imagemanager.milvus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilvusHybridSwitchTest {

    @Test
    void switchOffKeepsDenseMode() {
        assertEquals(MilvusRetrievalPlan.Mode.DENSE, MilvusRetrievalPlan.resolve(false, true, false));
        assertEquals(MilvusRetrievalPlan.Mode.DENSE, MilvusRetrievalPlan.resolve(false, false, false));
        assertEquals(MilvusRetrievalPlan.Mode.DENSE, MilvusRetrievalPlan.resolve(true, true, true));
        assertEquals(MilvusRetrievalPlan.Mode.HYBRID, MilvusRetrievalPlan.resolve(true, true, false));
    }

    @Test
    void cosineThresholdStillDropsWeakDenseHitWhenSwitchOff() {
        assertFalse(MilvusHitFilter.keep(0.20f, "cosine", 0.35));
        assertFalse(MilvusHitFilter.keep(0.20f, null, 0.35));
        assertTrue(MilvusHitFilter.keep(0.40f, "cosine", 0.35));
        assertTrue(MilvusHitFilter.keep(0.016f, "rrf", 0.35));
        assertEquals("相关度: 40.0%", RetrievalScoreLabel.format(0.40, "cosine"));
        assertEquals("相关度: 35.0%", RetrievalScoreLabel.format(0.35, null));
    }

    @Test
    void productCodeMissFallsBack() {
        List<String> hits = HybridRecallPolicy.accept(
                List.of("工艺说明没有目标货号", "另一条"),
                "25YK00022",
                text -> text);
        assertTrue(hits.isEmpty());

        List<String> kept = HybridRecallPolicy.accept(
                List.of("货号 25yk00022 克重 120", "无关"),
                "25YK00022",
                text -> text);
        assertEquals(List.of("货号 25yk00022 克重 120"), kept);

        List<String> all = HybridRecallPolicy.accept(List.of("甲", "乙"), null, text -> text);
        assertEquals(List.of("甲", "乙"), all);
    }
}
