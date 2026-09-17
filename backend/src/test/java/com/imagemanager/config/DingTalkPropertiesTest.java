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

    @Test
    void hasCorpIdIsFalseWhenBlank() {
        DingTalkProperties properties = new DingTalkProperties();
        assertFalse(properties.hasCorpId());
        properties.setCorpId("  ");
        assertFalse(properties.hasCorpId());
        properties.setCorpId("dingabc");
        assertTrue(properties.hasCorpId());
    }

    @Test
    void protocolLinkWrapDefaultsTrueButStillNeedsCorpId() {
        DingTalkProperties properties = new DingTalkProperties();
        assertTrue(properties.isWorkNoticeProtocolLinks());
        assertFalse(properties.shouldWrapWorkNoticeProtocolLinks());

        properties.setCorpId("dingabc");
        assertTrue(properties.shouldWrapWorkNoticeProtocolLinks());

        properties.setWorkNoticeProtocolLinks(false);
        assertFalse(properties.shouldWrapWorkNoticeProtocolLinks());
    }

    @Test
    void samplerTicketTtlDefaultsToSevenDays() {
        DingTalkProperties properties = new DingTalkProperties();
        assertEquals(7, properties.getSamplerTicketTtlDays());
        assertEquals(7, properties.resolveSamplerTicketTtlDays());
        properties.setSamplerTicketTtlDays(0);
        assertEquals(7, properties.resolveSamplerTicketTtlDays());
        properties.setSamplerTicketTtlDays(14);
        assertEquals(14, properties.resolveSamplerTicketTtlDays());
    }
}
