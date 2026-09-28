package com.imagemanager.milvus;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RrfFusionTest {

    @Test
    void sparseProductCodeOutranksMidDenseHit() {
        List<String> dense = List.of("popular", "other", "mid", "low", "weaker");
        List<String> sparse = List.of("code-doc");
        List<RrfFusion.Fused<String>> fused = RrfFusion.fuse(dense, sparse, id -> id, 60, 10);

        double code = fused.stream().filter(row -> row.id().equals("code-doc")).findFirst().orElseThrow().score();
        double mid = fused.stream().filter(row -> row.id().equals("weaker")).findFirst().orElseThrow().score();
        assertTrue(code > mid);
        RrfFusion.Fused<String> codeRow = fused.stream().filter(row -> row.id().equals("code-doc")).findFirst().orElseThrow();
        assertEquals(1, codeRow.sparseRank());
        assertEquals(0, codeRow.denseRank());
        assertTrue(indexOf(fused, "code-doc") < indexOf(fused, "weaker"));
    }

    @Test
    void codeInBothListsBeatsDenseOnlyLeaderWhenDenseRankIsPoor() {
        List<String> dense = new java.util.ArrayList<>();
        dense.add("leader");
        for (int i = 0; i < 28; i++) {
            dense.add("filler-" + i);
        }
        dense.add("code-doc");
        List<String> sparse = List.of("code-doc");
        List<RrfFusion.Fused<String>> fused = RrfFusion.fuse(dense, sparse, id -> id, 60, 3);

        assertEquals("code-doc", fused.get(0).id());
        assertTrue(fused.get(0).score() > fused.get(1).score());
    }

    @Test
    void agreementRanksAboveSingleChannel() {
        List<RrfFusion.Fused<String>> fused = RrfFusion.fuse(
                List.of("both", "dense-only"),
                List.of("both", "sparse-only"),
                id -> id,
                60,
                3);
        assertEquals("both", fused.get(0).id());
        assertEquals(1, fused.get(0).denseRank());
        assertEquals(1, fused.get(0).sparseRank());
    }

    private static int indexOf(List<RrfFusion.Fused<String>> fused, String id) {
        for (int i = 0; i < fused.size(); i++) {
            if (id.equals(fused.get(i).id())) {
                return i;
            }
        }
        return -1;
    }
}
