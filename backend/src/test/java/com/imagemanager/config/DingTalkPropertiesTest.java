package com.imagemanager.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkPropertiesTest {

    @Test
    void workNoticeDisabledWithoutAgentId() {
        DingTalkProperties properties = new DingTalkProperties();
        properties.setAppKey("k");
        properties.setAppSecret("s");
        properties.setAgentId("");
        assertTrue(properties.isConfigured());
        assertFalse(properties.isWorkNoticeEnabled());
        assertNull(properties.resolveAgentId());
    }

    @Test
    void workNoticeEnabledWithNumericAgentId() {
        DingTalkProperties properties = new DingTalkProperties();
        properties.setAppKey("k");
        properties.setAppSecret("s");
        properties.setAgentId(" 123456 ");
        assertTrue(properties.isWorkNoticeEnabled());
        assertEquals(123456L, properties.resolveAgentId());
    }

    @Test
    void nonNumericAgentIdDisablesWorkNotice() {
        DingTalkProperties properties = new DingTalkProperties();
        properties.setAppKey("k");
        properties.setAppSecret("s");
        properties.setAgentId("abc");
        assertFalse(properties.hasAgentId());
        assertFalse(properties.isWorkNoticeEnabled());
    }
}
