package com.imagemanager.dingtalk;

import com.imagemanager.config.DingTalkProperties;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DingTalkJsapiConfigServiceTest {

    @Test
    void unconfiguredReturnsFlagWithoutSignature() {
        DingTalkProperties properties = new DingTalkProperties();
        DingTalkClient client = mock(DingTalkClient.class);
        DingTalkJsapiConfigService service = new DingTalkJsapiConfigService(
                client, properties, "http://ai.bonasoma.com");
        Map<String, Object> data = service.build("http://ai.bonasoma.com/sampler/9");
        assertEquals(false, data.get("configured"));
        assertFalse(data.containsKey("signature"));
    }

    @Test
    void allowedFrontendUrlGetsSignature() {
        DingTalkProperties properties = new DingTalkProperties();
        properties.setAppKey("k");
        properties.setAppSecret("s");
        properties.setCorpId("dingcorp");
        properties.setAgentId("123");
        DingTalkClient client = mock(DingTalkClient.class);
        when(client.getJsapiTicket()).thenReturn("ticket-x");
        DingTalkJsapiConfigService service = new DingTalkJsapiConfigService(
                client, properties, "http://ai.bonasoma.com");
        Map<String, Object> data = service.build("http://ai.bonasoma.com/sampler/9#hash");
        assertEquals(true, data.get("configured"));
        assertEquals("dingcorp", data.get("corpId"));
        assertEquals("123", data.get("agentId"));
        assertTrue(data.get("signature") instanceof String);
        assertEquals("http://ai.bonasoma.com/sampler/9", data.get("url"));
    }

    @Test
    void foreignHostDoesNotGetSignature() {
        DingTalkProperties properties = new DingTalkProperties();
        properties.setAppKey("k");
        properties.setAppSecret("s");
        properties.setCorpId("dingcorp");
        DingTalkClient client = mock(DingTalkClient.class);
        DingTalkJsapiConfigService service = new DingTalkJsapiConfigService(
                client, properties, "http://ai.bonasoma.com");
        Map<String, Object> data = service.build("https://evil.example/phish");
        assertEquals("dingcorp", data.get("corpId"));
        assertFalse(data.containsKey("signature"));
    }

    @Test
    void configuredWithoutCorpIdLogsWarnAndReturnsEmptyCorpId() {
        DingTalkProperties properties = new DingTalkProperties();
        properties.setAppKey("k");
        properties.setAppSecret("s");
        properties.setAgentId("123");
        DingTalkClient client = mock(DingTalkClient.class);
        DingTalkJsapiConfigService service = new DingTalkJsapiConfigService(
                client, properties, "http://ai.bonasoma.com");

        Logger logger = (Logger) LoggerFactory.getLogger(DingTalkJsapiConfigService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Map<String, Object> data = service.build("http://ai.bonasoma.com/sampler/9");
            assertEquals(true, data.get("configured"));
            assertEquals("", data.get("corpId"));
            assertEquals("123", data.get("agentId"));
            assertTrue(appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("DINGTALK_CORP_ID")));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
