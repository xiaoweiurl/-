package com.imagemanager.dingtalk;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkSamplerTicketTest {

    private static final String SECRET = "unit-test-ticket-secret";
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00Z");
    private static final Instant EXP = Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void mintAndVerifyRoundTrip() {
        String ticket = DingTalkSamplerTicket.mint("u-li", 9L, EXP, SECRET);
        DingTalkSamplerTicket.Payload payload = DingTalkSamplerTicket.verify(ticket, SECRET, NOW);
        assertEquals("u-li", payload.userid());
        assertEquals(9L, payload.goodsId());
        assertEquals(EXP.getEpochSecond(), payload.expiresAtEpochSeconds());
        assertTrue(ticket.startsWith("v1."));
        assertFalse(ticket.contains(SECRET));
        assertFalse(ticket.contains("\n"));
    }

    @Test
    void rejectTamperedPayload() {
        String ticket = DingTalkSamplerTicket.mint("u-li", 9L, EXP, SECRET);
        String[] parts = ticket.split("\\.", 3);
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "xx." + parts[2];
        DingTalkSamplerTicket.InvalidTicketException ex = assertThrows(
                DingTalkSamplerTicket.InvalidTicketException.class,
                () -> DingTalkSamplerTicket.verify(tampered, SECRET, NOW));
        assertEquals(DingTalkSamplerTicket.InvalidTicketException.Reason.BAD_SIGNATURE, ex.getReason());
    }

    @Test
    void rejectWrongSecret() {
        String ticket = DingTalkSamplerTicket.mint("u-li", 9L, EXP, SECRET);
        DingTalkSamplerTicket.InvalidTicketException ex = assertThrows(
                DingTalkSamplerTicket.InvalidTicketException.class,
                () -> DingTalkSamplerTicket.verify(ticket, "other-secret", NOW));
        assertEquals(DingTalkSamplerTicket.InvalidTicketException.Reason.BAD_SIGNATURE, ex.getReason());
    }

    @Test
    void rejectExpired() {
        String ticket = DingTalkSamplerTicket.mint("u-li", 9L, EXP, SECRET);
        DingTalkSamplerTicket.InvalidTicketException ex = assertThrows(
                DingTalkSamplerTicket.InvalidTicketException.class,
                () -> DingTalkSamplerTicket.verify(ticket, SECRET, EXP));
        assertEquals(DingTalkSamplerTicket.InvalidTicketException.Reason.EXPIRED, ex.getReason());
    }

    @Test
    void rejectMalformedAndBlank() {
        assertThrows(DingTalkSamplerTicket.InvalidTicketException.class,
                () -> DingTalkSamplerTicket.verify("", SECRET, NOW));
        assertThrows(DingTalkSamplerTicket.InvalidTicketException.class,
                () -> DingTalkSamplerTicket.verify("not-a-ticket", SECRET, NOW));
        assertThrows(DingTalkSamplerTicket.InvalidTicketException.class,
                () -> DingTalkSamplerTicket.verify("v1.onlyone", SECRET, NOW));
        String longTicket = "v1." + "a".repeat(DingTalkSamplerTicket.MAX_TICKET_LENGTH);
        assertThrows(DingTalkSamplerTicket.InvalidTicketException.class,
                () -> DingTalkSamplerTicket.verify(longTicket, SECRET, NOW));
    }

    @Test
    void mintRejectsBlankUseridAndNonPositiveGoodsId() {
        assertThrows(IllegalArgumentException.class,
                () -> DingTalkSamplerTicket.mint("  ", 9L, EXP, SECRET));
        assertThrows(IllegalArgumentException.class,
                () -> DingTalkSamplerTicket.mint("u-li", 0L, EXP, SECRET));
        assertThrows(IllegalArgumentException.class,
                () -> DingTalkSamplerTicket.mint("u-li\nhack", 9L, EXP, SECRET));
    }

    @Test
    void logSafePrefixOmitsSignature() {
        String ticket = DingTalkSamplerTicket.mint("u-li", 9L, EXP, SECRET);
        String prefix = DingTalkSamplerTicket.logSafePrefix(ticket);
        assertTrue(prefix.startsWith("v1."));
        assertTrue(prefix.length() < ticket.length());
        assertFalse(ticket.endsWith(prefix));
        String sig = ticket.substring(ticket.lastIndexOf('.') + 1);
        assertFalse(prefix.contains(sig));
        assertEquals("", DingTalkSamplerTicket.logSafePrefix(null));
        assertEquals("", DingTalkSamplerTicket.logSafePrefix("  "));
    }
}
