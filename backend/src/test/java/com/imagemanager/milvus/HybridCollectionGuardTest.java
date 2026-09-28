package com.imagemanager.milvus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HybridCollectionGuardTest {

    @Test
    void refusesToUseLiveCollectionsAsShadow() {
        assertThrows(IllegalArgumentException.class,
                () -> HybridCollectionGuard.assertShadowTarget("salesperson_docs", "salesperson_docs"));
        assertThrows(IllegalArgumentException.class,
                () -> HybridCollectionGuard.assertShadowTarget("salesperson_docs", "salesperson_chunks"));
        assertThrows(IllegalArgumentException.class,
                () -> HybridCollectionGuard.assertSmokeName("salesperson_docs"));
    }

    @Test
    void allowsConfiguredShadowName() {
        assertDoesNotThrow(() -> HybridCollectionGuard.assertShadowTarget(
                "salesperson_docs", "salesperson_docs_hybrid"));
        assertDoesNotThrow(() -> HybridCollectionGuard.assertSmokeName("hybrid_smoke_src"));
    }
}
