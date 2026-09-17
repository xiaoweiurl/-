package com.imagemanager.dingtalk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkLinksTest {

    @Test
    void blankHttpReturnsEmpty() {
        assertEquals("", DingTalkLinks.workNoticeUrl(null, "corp", 1L));
        assertEquals("", DingTalkLinks.workNoticeUrl("  ", "corp", 1L));
    }

    @Test
    void withoutCorpIdUsesPageLinkWithEncodedFormUrl() {
        String url = DingTalkLinks.workNoticeUrl("http://ai.bonasoma.com/sampler/42", "", 123456L);
        assertTrue(url.startsWith("dingtalk://dingtalkclient/page/link?url="));
        assertTrue(url.contains("pc_slide=true"));
        assertTrue(url.contains("http%3A%2F%2Fai.bonasoma.com%2Fsampler%2F42"));
        assertFalse(url.contains("openapp"));
    }

    @Test
    void withCorpIdAndAgentUsesOpenAppRedirect() {
        String url = DingTalkLinks.workNoticeUrl(
                "http://ai.bonasoma.com/sampler/9", "dingabc", 123456L);
        assertTrue(url.startsWith("dingtalk://dingtalkclient/action/openapp?"));
        assertTrue(url.contains("corpid=dingabc"));
        assertTrue(url.contains("app_id=0_123456"));
        assertTrue(url.contains("redirect_type=jump"));
        assertTrue(url.contains("redirect_url=http%3A%2F%2Fai.bonasoma.com%2Fsampler%2F9"));
        assertTrue(url.contains("container_type=work_platform"));
    }

    @Test
    void missingAgentFallsBackToPageLinkEvenIfCorpIdSet() {
        String url = DingTalkLinks.workNoticeUrl("http://localhost:5000/sampler/1", "dingabc", null);
        assertTrue(url.startsWith("dingtalk://dingtalkclient/page/link?"));
        assertTrue(url.contains("sampler%2F1"));
    }
}
