package com.imagemanager.dingtalk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkLinksTest {

    private static final String FORM = "http://ai.bonasoma.com/sampler/42";

    @Test
    void blankHttpReturnsEmpty() {
        assertEquals("", DingTalkLinks.workNoticeUrl(null, "corp", 1L, true));
        assertEquals("", DingTalkLinks.workNoticeUrl("  ", "corp", 1L, true));
    }

    @Test
    void protocolLinksFalseIsPlainHttpEvenWithCorpIdAndAgent() {
        String url = DingTalkLinks.workNoticeUrl(FORM, "dingabc", 123456L, false);
        assertEquals(FORM, url);
        assertFalse(url.startsWith("dingtalk://"));
    }

    @Test
    void protocolLinksWithoutCorpIdStaysPlainHttp() {
        String url = DingTalkLinks.workNoticeUrl(FORM, "", 123456L, true);
        assertEquals(FORM, url);
        url = DingTalkLinks.workNoticeUrl(FORM, "  ", 123456L, true);
        assertEquals(FORM, url);
    }

    @Test
    void protocolLinksWithCorpIdAndAgentUsesOpenAppRedirect() {
        String url = DingTalkLinks.workNoticeUrl(
                "http://ai.bonasoma.com/sampler/9", "dingabc", 123456L, true);
        assertTrue(url.startsWith("dingtalk://dingtalkclient/action/openapp?"));
        assertTrue(url.contains("corpid=dingabc"));
        assertTrue(url.contains("app_id=0_123456"));
        assertTrue(url.contains("redirect_type=jump"));
        assertTrue(url.contains("redirect_url=http%3A%2F%2Fai.bonasoma.com%2Fsampler%2F9"));
        assertTrue(url.contains("container_type=work_platform"));
    }

    @Test
    void protocolLinksWithCorpIdButNoAgentUsesPageLink() {
        String url = DingTalkLinks.workNoticeUrl(
                "http://localhost:5000/sampler/1", "dingabc", null, true);
        assertTrue(url.startsWith("dingtalk://dingtalkclient/page/link?"));
        assertTrue(url.contains("pc_slide=true"));
        assertTrue(url.contains("http%3A%2F%2Flocalhost%3A5000%2Fsampler%2F1"));
        assertFalse(url.contains("openapp"));
    }

    @Test
    void logSafePrefixStripsQueryAndKeepsHttpPath() {
        assertEquals("", DingTalkLinks.logSafePrefix(null));
        assertEquals("", DingTalkLinks.logSafePrefix("  "));
        assertEquals("http://ai.bonasoma.com/sampler/42", DingTalkLinks.logSafePrefix(FORM));
        String wrapped = DingTalkLinks.workNoticeUrl(FORM, "dingabc", 123456L, true);
        assertEquals("dingtalk://dingtalkclient/action/openapp", DingTalkLinks.logSafePrefix(wrapped));
        assertFalse(DingTalkLinks.logSafePrefix(wrapped).contains("corpid"));
        assertFalse(DingTalkLinks.logSafePrefix(wrapped).contains("app_id"));
    }
}
