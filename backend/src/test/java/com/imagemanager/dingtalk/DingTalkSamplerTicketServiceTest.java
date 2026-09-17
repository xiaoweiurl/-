package com.imagemanager.dingtalk;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dto.LoginResponse;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DingTalkSamplerTicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-17T06:00:00Z");

    private DingTalkProperties properties;
    private DingTalkFreeLoginService freeLogin;
    private Environment environment;
    private DingTalkSamplerTicketService service;

    @BeforeEach
    void setUp() {
        properties = new DingTalkProperties();
        properties.setSamplerTicketSecret("dedicated-secret");
        properties.setSamplerTicketTtlDays(7);
        properties.setAppSecret("app-secret-should-not-be-in-url");
        freeLogin = mock(DingTalkFreeLoginService.class);
        environment = mock(Environment.class);
        when(environment.acceptsProfiles(Profiles.of("local"))).thenReturn(false);
        service = new DingTalkSamplerTicketService(
                properties, freeLogin, environment, Clock.fixed(NOW, ZoneOffset.UTC));
        when(freeLogin.loginByUserid(anyString(), anyLong())).thenReturn(
                LoginResponse.builder().sessionId("sess-1").build());
    }

    @Test
    void mintAndRedeemIssuesSessionForBoundUseridAndGoods() {
        Optional<String> minted = service.mint("u-li", 9L);
        assertTrue(minted.isPresent());
        assertFalse(minted.get().contains("dedicated-secret"));
        assertFalse(minted.get().contains("app-secret"));

        LoginResponse response = service.redeem(minted.get(), 9L);
        assertEquals("sess-1", response.getSessionId());
        verify(freeLogin).loginByUserid("u-li", 9L);
    }

    @Test
    void redeemRejectsWrongGoodsIdWithoutCallingFreeLogin() {
        String ticket = service.mint("u-li", 9L).orElseThrow();
        DingTalkFreeLoginException ex = assertThrows(DingTalkFreeLoginException.class,
                () -> service.redeem(ticket, 77L));
        assertEquals(400, ex.getHttpStatus());
        verify(freeLogin, never()).loginByUserid(anyString(), anyLong());
    }

    @Test
    void redeemRejectsExpiredTicket() {
        properties.setSamplerTicketTtlDays(7);
        String ticket = service.mint("u-li", 9L).orElseThrow();
        DingTalkSamplerTicketService later = new DingTalkSamplerTicketService(
                properties, freeLogin, environment,
                Clock.fixed(NOW.plusSeconds(8L * 24 * 3600), ZoneOffset.UTC));
        DingTalkFreeLoginException ex = assertThrows(DingTalkFreeLoginException.class,
                () -> later.redeem(ticket, 9L));
        assertEquals(401, ex.getHttpStatus());
        verify(freeLogin, never()).loginByUserid(anyString(), anyLong());
    }

    @Test
    void redeemLogsFailureWithoutFullTicket() {
        String ticket = service.mint("u-li", 9L).orElseThrow();
        Logger logger = (Logger) LoggerFactory.getLogger(DingTalkSamplerTicketService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(DingTalkFreeLoginException.class, () -> service.redeem(ticket, 77L));
            assertTrue(appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("核销失败")
                            && e.getFormattedMessage().contains("goods-mismatch")
                            && e.getFormattedMessage().contains("prefix=v1.")
                            && !e.getFormattedMessage().contains(ticket)));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void redeemLogsSuccessWithoutFullTicket() {
        String ticket = service.mint("u-wang", 12L).orElseThrow();
        Logger logger = (Logger) LoggerFactory.getLogger(DingTalkSamplerTicketService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.redeem(ticket, 12L);
            assertTrue(appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.INFO
                            && e.getFormattedMessage().contains("核销成功")
                            && e.getFormattedMessage().contains("goodsId=12")
                            && e.getFormattedMessage().contains("userid=u-wa***")
                            && !e.getFormattedMessage().contains(ticket)));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void fallsBackToAppSecretWhenDedicatedBlank() {
        properties.setSamplerTicketSecret("  ");
        DingTalkSamplerTicketService withApp = new DingTalkSamplerTicketService(
                properties, freeLogin, environment, Clock.fixed(NOW, ZoneOffset.UTC));
        String ticket = withApp.mint("u-li", 9L).orElseThrow();
        withApp.redeem(ticket, 9L);
        verify(freeLogin).loginByUserid("u-li", 9L);
        assertEquals("app-secret-should-not-be-in-url", withApp.resolveSecret());
        assertFalse(ticket.contains("app-secret"));
    }

    @Test
    void localProfileUsesBuiltinSecretWhenNoneConfigured() {
        properties.setSamplerTicketSecret("");
        properties.setAppSecret("");
        when(environment.acceptsProfiles(Profiles.of("local"))).thenReturn(true);
        DingTalkSamplerTicketService local = new DingTalkSamplerTicketService(
                properties, freeLogin, environment, Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(DingTalkSamplerTicketService.LOCAL_DEFAULT_SECRET, local.resolveSecret());
        assertTrue(local.mint("u-li", 9L).isPresent());
    }

    @Test
    void nonLocalWithoutSecretDoesNotMint() {
        properties.setSamplerTicketSecret("");
        properties.setAppSecret("");
        when(environment.acceptsProfiles(Profiles.of("local"))).thenReturn(false);
        DingTalkSamplerTicketService prod = new DingTalkSamplerTicketService(
                properties, freeLogin, environment, Clock.fixed(NOW, ZoneOffset.UTC));
        assertTrue(prod.mint("u-li", 9L).isEmpty());
        DingTalkFreeLoginException ex = assertThrows(DingTalkFreeLoginException.class,
                () -> prod.redeem("v1.abc.def", 9L));
        assertEquals(401, ex.getHttpStatus());
        verify(freeLogin, never()).loginByUserid(anyString(), eq(9L));
    }

    @Test
    void redeemMissingGoodsId() {
        DingTalkFreeLoginException ex = assertThrows(DingTalkFreeLoginException.class,
                () -> service.redeem("v1.x.y", null));
        assertEquals(400, ex.getHttpStatus());
    }
}
