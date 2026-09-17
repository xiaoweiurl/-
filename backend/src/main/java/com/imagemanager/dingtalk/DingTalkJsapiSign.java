package com.imagemanager.dingtalk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 钉钉 H5 JSAPI {@code dd.config} 签名（SHA-1）。
 * 明文：{@code jsapi_ticket=...&noncestr=...&timestamp=...&url=...}
 */
public final class DingTalkJsapiSign {

    private DingTalkJsapiSign() {
    }

    public static String sign(String jsapiTicket, String nonceStr, String timestamp, String url) {
        String plain = "jsapi_ticket=" + nullToEmpty(jsapiTicket)
                + "&noncestr=" + nullToEmpty(nonceStr)
                + "&timestamp=" + nullToEmpty(timestamp)
                + "&url=" + nullToEmpty(url);
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 不可用", e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
