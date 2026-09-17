package com.imagemanager.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadStorageKeysTest {

    @Test
    void detectsRelativeAndHostedLocalUploadUrls() {
        assertTrue(UploadStorageKeys.isLocalUploadUrl("/api/uploads/images/a.jpg"));
        assertTrue(UploadStorageKeys.isLocalUploadUrl("/uploads/images/a.jpg"));
        assertTrue(UploadStorageKeys.isLocalUploadUrl("http://ai.bonasoma.com/api/uploads/images/a.jpg"));
        assertFalse(UploadStorageKeys.isLocalUploadUrl("https://bucket.oss-cn-hangzhou.aliyuncs.com/images/a.jpg"));
        assertFalse(UploadStorageKeys.isLocalUploadUrl(null));
    }

    @Test
    void resolvesKeyPreferringFileKey() {
        assertEquals("images/a.jpg",
                UploadStorageKeys.resolveStorageKey("images/a.jpg", "/uploads/other.jpg", "/api/uploads/x.jpg"));
        assertEquals("images/b.jpg",
                UploadStorageKeys.resolveStorageKey(null, "images/b.jpg", "/api/uploads/x.jpg"));
        assertEquals("images/c.jpg",
                UploadStorageKeys.resolveStorageKey(null, null, "/api/uploads/images/c.jpg"));
    }

    @Test
    void stripsUploadPrefixesAndRejectsTraversal() {
        assertEquals("images/a.jpg", UploadStorageKeys.fromUrlOrPath("/api/uploads/images/a.jpg"));
        assertEquals("images/a.jpg", UploadStorageKeys.fromUrlOrPath("/uploads/images/a.jpg"));
        assertEquals("images/a.jpg",
                UploadStorageKeys.fromUrlOrPath("http://ai.bonasoma.com/api/uploads/images/a.jpg"));
        assertNull(UploadStorageKeys.fromUrlOrPath("/uploads/../secret.jpg"));
        assertNull(UploadStorageKeys.fromUrlOrPath("./uploads/images/a.jpg"));
    }
}
