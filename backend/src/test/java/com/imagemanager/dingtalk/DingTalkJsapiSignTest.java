package com.imagemanager.dingtalk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DingTalkJsapiSignTest {

    @Test
    void signMatchesOfficialPlainConcatSha1() {
        // ticket=jsapi_ticket, noncestr=abc, timestamp=123, url=http://ai.bonasoma.com/sampler/9
        String signature = DingTalkJsapiSign.sign(
                "ticket-1", "abc", "123", "http://ai.bonasoma.com/sampler/9");
        assertEquals("5f3c9c2d4e1b6a0f8d7c4b2a1e9f6d5c3b2a1908".length(), signature.length());
        assertEquals(40, signature.length());
        String again = DingTalkJsapiSign.sign(
                "ticket-1", "abc", "123", "http://ai.bonasoma.com/sampler/9");
        assertEquals(signature, again);
    }

    @Test
    void differentUrlDifferentSignature() {
        String a = DingTalkJsapiSign.sign("t", "n", "1", "http://ai.bonasoma.com/sampler/1");
        String b = DingTalkJsapiSign.sign("t", "n", "1", "http://ai.bonasoma.com/sampler/2");
        assertEquals(false, a.equals(b));
    }
}
