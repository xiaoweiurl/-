package com.imagemanager.dingtalk;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * 打样工作通知免登 ticket：HMAC-SHA256 绑定钉钉 userid + goodsId + 过期时间。
 * <p>
 * 格式 {@code v1.{base64url(payload)}.{base64url(mac)}}，
 * payload 为 {@code userid\\ngoodsId\\nexpEpochSeconds}。
 * 密钥只用于签名，绝不写入 URL。
 */
public final class DingTalkSamplerTicket {

    public static final String VERSION = "v1";
    public static final int MAX_TICKET_LENGTH = 2048;
    public static final int MAX_USERID_LENGTH = 128;

    private static final String HMAC_ALG = "HmacSHA256";

    private DingTalkSamplerTicket() {
    }

    public record Payload(String userid, long goodsId, long expiresAtEpochSeconds) {
        public boolean isExpired(Instant now) {
            if (now == null) {
                now = Instant.now();
            }
            return now.getEpochSecond() >= expiresAtEpochSeconds;
        }
    }

    public static final class InvalidTicketException extends RuntimeException {
        public enum Reason { MALFORMED, BAD_SIGNATURE, EXPIRED }

        private final Reason reason;

        public InvalidTicketException(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }

    public static String mint(String userid, long goodsId, Instant expiresAt, String secret) {
        String uid = requireUserid(userid);
        if (goodsId <= 0) {
            throw new IllegalArgumentException("goodsId 无效");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("缺少过期时间");
        }
        String key = requireSecret(secret);
        long exp = expiresAt.getEpochSecond();
        String payloadB64 = encodeUtf8(uid + "\n" + goodsId + "\n" + exp);
        String macB64 = encodeBytes(hmac(key, VERSION + "." + payloadB64));
        return VERSION + "." + payloadB64 + "." + macB64;
    }

    public static Payload verify(String ticket, String secret, Instant now) {
        String raw = ticket == null ? "" : ticket.trim();
        if (raw.isEmpty() || raw.length() > MAX_TICKET_LENGTH) {
            throw new InvalidTicketException(InvalidTicketException.Reason.MALFORMED);
        }
        String key = requireSecret(secret);
        String[] parts = raw.split("\\.", 3);
        if (parts.length != 3 || !VERSION.equals(parts[0]) || parts[1].isEmpty() || parts[2].isEmpty()) {
            throw new InvalidTicketException(InvalidTicketException.Reason.MALFORMED);
        }
        byte[] expected = hmac(key, VERSION + "." + parts[1]);
        byte[] actual;
        try {
            actual = decodeBytes(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new InvalidTicketException(InvalidTicketException.Reason.MALFORMED);
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidTicketException(InvalidTicketException.Reason.BAD_SIGNATURE);
        }
        Payload payload = parsePayload(parts[1]);
        Instant clock = now == null ? Instant.now() : now;
        if (payload.isExpired(clock)) {
            throw new InvalidTicketException(InvalidTicketException.Reason.EXPIRED);
        }
        return payload;
    }

    /**
     * 日志用短前缀：版本 + payload 前 8 位，不含签名、不含完整 ticket。
     */
    public static String logSafePrefix(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return "";
        }
        String raw = ticket.trim();
        String[] parts = raw.split("\\.", 3);
        if (parts.length < 2 || parts[1].isEmpty()) {
            return VERSION + ".?";
        }
        String payload = parts[1];
        int n = Math.min(8, payload.length());
        return VERSION + "." + payload.substring(0, n);
    }

    private static Payload parsePayload(String payloadB64) {
        String plain;
        try {
            plain = new String(decodeBytes(payloadB64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new InvalidTicketException(InvalidTicketException.Reason.MALFORMED);
        }
        String[] fields = plain.split("\n", -1);
        if (fields.length != 3) {
            throw new InvalidTicketException(InvalidTicketException.Reason.MALFORMED);
        }
        String userid;
        try {
            userid = requireUserid(fields[0]);
        } catch (IllegalArgumentException e) {
            throw new InvalidTicketException(InvalidTicketException.Reason.MALFORMED);
        }
        long goodsId;
        long exp;
        try {
            goodsId = Long.parseLong(fields[1]);
            exp = Long.parseLong(fields[2]);
        } catch (NumberFormatException e) {
            throw new InvalidTicketException(InvalidTicketException.Reason.MALFORMED);
        }
        if (goodsId <= 0 || exp <= 0) {
            throw new InvalidTicketException(InvalidTicketException.Reason.MALFORMED);
        }
        return new Payload(userid, goodsId, exp);
    }

    private static String requireUserid(String userid) {
        if (userid == null || userid.isBlank()) {
            throw new IllegalArgumentException("缺少钉钉 userid");
        }
        String uid = userid.trim();
        if (uid.length() > MAX_USERID_LENGTH || uid.indexOf('\n') >= 0 || uid.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("钉钉 userid 无效");
        }
        return uid;
    }

    private static String requireSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("缺少 ticket 签名密钥");
        }
        return secret;
    }

    private static byte[] hmac(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    private static String encodeUtf8(String value) {
        return encodeBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String encodeBytes(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static byte[] decodeBytes(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
