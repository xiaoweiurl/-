package com.imagemanager.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

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
     * 优先处理 URL 编码的字符串
     * 支持以下格式：
     * 1. 直接的 URL 编码字符串：%CC%F9%C9%ED%B2%E3
     * 2. 完整的 URL：http://xxx?catName=%CC%F9%C9%ED#bd
     */
    public static String convertToUtf8(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 首先检查是否是完整的 URL，如果是则提取 catName 参数
        String extractedValue = extractUrlParamValue(input, "catName");
        if (extractedValue != null) {
            input = extractedValue;
            log.info("从URL中提取catName参数: {}", input);
        }

        // 尝试 URL 解码（处理 %CC%F9%C9%ED 格式的 URL 编码）
        String urlDecoded = tryUrlDecode(input);
        if (urlDecoded != null && !urlDecoded.equals(input)) {
            log.info("URL解码成功: {} -> {}", input, urlDecoded);
            input = urlDecoded;
        }

        // 检测是否为乱码
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
     * 从 URL 中提取指定参数的值
     * 例如：输入 "http://xxx?catName=%CC%F9&C&D#bd"，返回 "%CC%F9"
     */
    private static String extractUrlParamValue(String url, String paramName) {
        if (url == null || !url.contains(paramName + "=")) {
            return null;
        }

        try {
            // 查找参数名的位置
            int paramIndex = url.indexOf(paramName + "=");
            // 跳过参数名和等号
            int valueStart = paramIndex + paramName.length() + 1;
            
            // 找到参数的结束位置（& 或 # 或 URL 结尾）
            int valueEnd = url.length();
            for (int i = valueStart; i < url.length(); i++) {
                char c = url.charAt(i);
                if (c == '&' || c == '#' || c == '?') {
                    valueEnd = i;
                    break;
                }
            }
            
            String value = url.substring(valueStart, valueEnd);
            
            // 如果提取到的值包含 %（URL 编码），则返回
            if (value.contains("%")) {
                return value;
            }
        } catch (Exception e) {
            log.warn("提取URL参数失败: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * 尝试 URL 解码
     * 支持 GBK、GB2312、UTF-8 等编码的 URL 编码字符串
     */
    private static String tryUrlDecode(String input) {
        if (input == null || !input.contains("%")) {
            return input;
        }

        // 尝试用 UTF-8 解码
        try {
            String decoded = URLDecoder.decode(input, StandardCharsets.UTF_8.name());
            if (isValidChinese(decoded)) {
                return decoded;
            }
        } catch (Exception e) {
            // 忽略，继续尝试其他编码
        }

        // 尝试用 GBK 解码
        try {
            String decoded = URLDecoder.decode(input, "GBK");
            if (isValidChinese(decoded)) {
                return decoded;
            }
        } catch (Exception e) {
            // 忽略
        }

        // 尝试用 GB2312 解码
        try {
            String decoded = URLDecoder.decode(input, "GB2312");
            if (isValidChinese(decoded)) {
                return decoded;
            }
        } catch (Exception e) {
            // 忽略
        }

        return input;
    }

    /**
     * 手动解码 URL 编码的字符串（不依赖 Java 内置解码）
     * 适用于某些特殊编码情况
     */
    public static String manualUrlDecode(String input) {
        if (input == null || !input.contains("%")) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < input.length()) {
            char c = input.charAt(i);

            if (c == '%' && i + 2 < input.length()) {
                // 解析 %XX 格式的字节
                try {
                    String hex = input.substring(i + 1, i + 3);
                    int byteValue = Integer.parseInt(hex, 16);
                    byte[] bytes = new byte[]{(byte) byteValue};

                    // 尝试用中文编码解读
                    String decoded = decodeBytesWithChineseCharset(bytes);
                    if (decoded != null && isValidChinese(decoded)) {
                        result.append(decoded);
                        i += 3;
                        continue;
                    }
                } catch (NumberFormatException e) {
                    // 解析失败，当作普通字符处理
                }
            }

            // + 通常表示空格
            if (c == '+') {
                result.append(' ');
            } else {
                result.append(c);
            }
            i++;
        }

        return result.toString();
    }

    /**
     * 用中文编码尝试解码字节数组
     */
    private static String decodeBytesWithChineseCharset(byte[] bytes) {
        for (Charset charset : CHINESE_CHARSETS) {
            try {
                String decoded = new String(bytes, charset);
                if (isValidChinese(decoded)) {
                    return decoded;
                }
            } catch (Exception e) {
                // 忽略
            }
        }
        return null;
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
            // CJK 统一汉字范围
            if (c >= '\u4E00' && c <= '\u9FFF') {
                chineseCount++;
            }
            // CJK 统一汉字扩展范围
            if (c >= '\u3400' && c <= '\u4DBF') {
                chineseCount++;
            }
            // 全角中文标点也算中文
            if ((c >= '\u3000' && c <= '\u303F') || (c >= '\uFF00' && c <= '\uFFEF')) {
                chineseCount++;
            }
        }

        // 如果包含超过 20% 的汉字，认为是有效中文
        return totalCount > 0 && chineseCount >= totalCount * 0.2;
    }

    /**
     * 安全获取字符串的 UTF-8 字节
     */
    public static byte[] getUtf8Bytes(String text) {
        if (text == null) {
            return new byte[0];
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
