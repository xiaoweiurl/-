package com.imagemanager.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mozilla.universalchardet.UniversalDetector;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 字符编码检测与转换工具类
 * 支持 GB2312、GBK、GB18030 等中文编码的自动检测和转换
 */
public class CharsetUtil {

    private static final Logger log = LoggerFactory.getLogger(CharsetUtil.class);

    // 常见中文编码列表，按优先级排序
    private static final Charset[] CHINESE_CHARSETS = {
            StandardCharsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB2312"),
            Charset.forName("GB18030"),
            StandardCharsets.ISO_8859_1
    };

    /**
     * 检测字符串的真实编码并转换为 UTF-8
     */
    public static String convertToUtf8(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 首先检测是否为乱码
        if (looksLikeGarbled(input)) {
            return fixGarbledString(input);
        }

        // 使用 juniversalchardet 检测编码
        String detectedCharset = detectCharset(input);
        if (detectedCharset != null && !detectedCharset.equalsIgnoreCase("UTF-8")) {
            try {
                Charset charset = Charset.forName(detectedCharset);
                byte[] bytes = input.getBytes(StandardCharsets.ISO_8859_1);
                String converted = new String(bytes, charset);
                if (isValidChinese(converted)) {
                    log.debug("编码转换: {} -> UTF-8", detectedCharset);
                    return converted;
                }
            } catch (Exception e) {
                log.warn("编码转换失败: {} -> {}", detectedCharset, e.getMessage());
            }
        }

        return input;
    }

    /**
     * 检测字节数组的真实编码并转换为 UTF-8
     */
    public static String convertBytesToUtf8(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        String detectedCharset = detectCharsetFromBytes(bytes);
        try {
            Charset charset = (detectedCharset != null) ? Charset.forName(detectedCharset) : StandardCharsets.UTF_8;
            String result = new String(bytes, charset);
            if (!"UTF-8".equalsIgnoreCase(detectedCharset) && isValidChinese(result)) {
                return result;
            }
            return result;
        } catch (Exception e) {
            log.warn("字节数组编码转换失败: {}", e.getMessage());
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    /**
     * 使用 juniversalchardet 检测字符串的编码
     */
    public static String detectCharset(String text) {
        if (text == null || text.isEmpty()) {
            return "UTF-8";
        }

        return detectCharsetFromBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    /**
     * 使用 juniversalchardet 检测字节数组的编码
     */
    public static String detectCharsetFromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "UTF-8";
        }

        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(bytes, 0, bytes.length);
        detector.dataEnd();

        String encoding = detector.getDetectedCharset();
        detector.reset();

        return encoding != null ? encoding : "UTF-8";
    }

    /**
     * 判断字符串是否看起来像乱码
     */
    private static boolean looksLikeGarbled(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        int garbledCount = 0;
        for (char c : text.toCharArray()) {
            if (c == '?' || c == '\uFFFD' ||
                    (c >= '\u0080' && c <= '\u00A0') ||
                    (c >= '\u2000' && c <= '\u206F') ||
                    (c >= '\u2500' && c <= '\u257F')) {
                garbledCount++;
            }
        }

        return garbledCount > text.length() * 0.1;
    }

    /**
     * 修复乱码字符串
     */
    private static String fixGarbledString(String garbledText) {
        if (garbledText == null) {
            return null;
        }

        byte[] bytes = garbledText.getBytes(StandardCharsets.ISO_8859_1);

        for (Charset charset : CHINESE_CHARSETS) {
            try {
                String converted = new String(bytes, charset);
                if (isValidChinese(converted)) {
                    log.debug("乱码修复成功: 使用 {}", charset.name());
                    return converted;
                }
            } catch (Exception e) {
                // 继续尝试下一个编码
            }
        }

        return garbledText;
    }

    /**
     * 判断字符串是否为有效的中文内容
     */
    public static boolean isValidChinese(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        int chineseCount = 0;
        int totalCount = 0;

        for (char c : text.toCharArray()) {
            totalCount++;
            if (c >= '\u4E00' && c <= '\u9FFF') {
                chineseCount++;
            }
            if (c >= '\u3400' && c <= '\u4DBF') {
                chineseCount++;
            }
        }

        return totalCount > 0 && chineseCount >= totalCount * 0.2;
    }

    /**
     * 安全获取字符串的UTF-8字节
     */
    public static byte[] getUtf8Bytes(String text) {
        if (text == null) {
            return new byte[0];
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
