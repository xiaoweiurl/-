package com.imagemanager.milvus;

import com.imagemanager.util.KeywordExtractor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 混合检索的查询/文档共用分词。
 *
 * <p>货号、型号、面料编号（{@code 25YK00022}、{@code C100-40S}、{@code 40S}）整段保留，
 * 不交给会按标点切开的分词器。中文用行业词典正向最大匹配，再补重叠二字，避免 jieba
 * 把字母数字串切碎，也不依赖服务器是否带 jieba 词典。
 *
 * <p>输出写成空格分隔、已小写的 {@code lexical_text}，Milvus BM25 只用 whitespace 分词。
 */
public final class HybridLexicalTokenizer {

    /** 写入 VARCHAR 的上限。货号 token 排在前面，截断时仍留得住。 */
    public static final int MAX_LEXICAL_CHARS = 12000;

    private static final int MAX_TOKENS = 2048;
    private static final int MAX_CODE_LENGTH = 64;

    /**
     * 连续的字母数字，中间可以有 . _ / -，但首尾必须是字母或数字。
     * 是否当成货号/面料编号，还要同时含字母和数字，且长度至少 3。
     */
    private static final Pattern CODE = Pattern.compile(
            "[A-Za-z0-9]+(?:[._/\\-][A-Za-z0-9]+)*");

    private static final Pattern CJK_RUN = Pattern.compile("[\\u4e00-\\u9fff]+");
    private static final Pattern LATIN_RUN = Pattern.compile("[A-Za-z]{2,}");

    private HybridLexicalTokenizer() {
    }

    public static List<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        Matcher matcher = CODE.matcher(text);
        StringBuilder rest = new StringBuilder();
        while (matcher.find()) {
            String raw = matcher.group();
            if (acceptCode(raw)) {
                codes.add(raw);
                matcher.appendReplacement(rest, " ");
            }
        }
        matcher.appendTail(rest);

        List<String> ordered = new ArrayList<>();
        Set<String> seenCodes = new LinkedHashSet<>();
        for (String raw : codes) {
            addCodeForms(raw, ordered, seenCodes);
        }

        String remainder = rest.toString();
        for (String word : KeywordExtractor.segmentBusinessText(remainder)) {
            if (word == null) {
                continue;
            }
            String trimmed = word.trim();
            if (trimmed.length() < 2 || trimmed.length() > 12) {
                continue;
            }
            if (KeywordExtractor.isIndexStopWord(trimmed)) {
                continue;
            }
            if (trimmed.chars().allMatch(HybridLexicalTokenizer::isCjk)) {
                add(ordered, trimmed);
            }
        }

        Matcher cjk = CJK_RUN.matcher(remainder);
        while (cjk.find()) {
            String run = cjk.group();
            for (int i = 0; i + 1 < run.length(); i++) {
                String bigram = run.substring(i, i + 2);
                if (isStopChar(bigram.charAt(0)) || isStopChar(bigram.charAt(1))) {
                    continue;
                }
                add(ordered, bigram);
            }
        }

        Matcher latin = LATIN_RUN.matcher(remainder);
        while (latin.find()) {
            String word = latin.group().toLowerCase(Locale.ROOT);
            if (KeywordExtractor.isIndexStopWord(word)) {
                continue;
            }
            add(ordered, word);
        }

        if (ordered.size() > MAX_TOKENS) {
            return List.copyOf(ordered.subList(0, MAX_TOKENS));
        }
        return List.copyOf(ordered);
    }

    /** 空格分隔的索引文本。没有 token 时返回 {@code _blank}，避免 BM25 字段为空。 */
    public static String lexicalText(String text) {
        List<String> tokens = tokens(text);
        if (tokens.isEmpty()) {
            return "_blank";
        }
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            int extra = sb.length() == 0 ? token.length() : token.length() + 1;
            if (sb.length() + extra > MAX_LEXICAL_CHARS) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(token);
        }
        return sb.length() == 0 ? "_blank" : sb.toString();
    }

    private static void addCodeForms(String raw, List<String> ordered, Set<String> seenCodes) {
        String full = raw.toLowerCase(Locale.ROOT);
        if (seenCodes.add(full)) {
            ordered.add(full);
        }
        if (raw.indexOf('-') >= 0 || raw.indexOf('/') >= 0 || raw.indexOf('_') >= 0 || raw.indexOf('.') >= 0) {
            String squashed = full.replaceAll("[._/\\-]", "");
            if (squashed.length() >= 3 && !squashed.equals(full) && seenCodes.add(squashed)) {
                ordered.add(squashed);
            }
            for (String part : raw.split("[._/\\-]+")) {
                if (!acceptCode(part)) {
                    continue;
                }
                String piece = part.toLowerCase(Locale.ROOT);
                if (seenCodes.add(piece)) {
                    ordered.add(piece);
                }
            }
        }
    }

    public static boolean acceptCode(String raw) {
        if (raw == null || raw.length() < 3 || raw.length() > MAX_CODE_LENGTH) {
            return false;
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
        }
        return letter && digit;
    }

    private static void add(List<String> ordered, String token) {
        if (ordered.size() >= MAX_TOKENS || token == null || token.isBlank()) {
            return;
        }
        ordered.add(token);
    }

    private static boolean isCjk(int codePoint) {
        return codePoint >= 0x4E00 && codePoint <= 0x9FFF;
    }

    private static boolean isStopChar(char c) {
        return KeywordExtractor.isIndexStopWord(String.valueOf(c));
    }
}
