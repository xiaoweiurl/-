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

    /**
     * BM25 查询文本。问句里有货号/型号/面料编号时，只保留这些编号的 token。
     *
     * <p>若把「型号」「货号」这类常见词一起送进稀疏检索，大量无关切片会同时出现在
     * 稠密和稀疏的前排，RRF 会把只靠编号命中、稠密向量又排不进前 48 的切片挤出最终结果。
     * 没有编号时仍用完整词袋，中文问题的行为不变。
     */
    public static String sparseQuery(String text) {
        List<String> codes = codeQueryTokens(text);
        if (codes.isEmpty()) {
            return lexicalText(text);
        }
        return String.join(" ", codes);
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

    private static final Set<String> SURFACE_SKIP = Set.of(
            "utf8", "utf16", "pdf", "docx", "xlsx", "pptx", "jpg", "jpeg", "png", "gif", "webp",
            "mp4", "mp3", "html", "css", "xml", "json", "csv", "txt", "zip", "rar");

    /** 数量、重量、功率一类后缀，不拿来出评测题。 */
    private static final Set<String> UNIT_SUFFIX = Set.of(
            "kgs", "kg", "g", "mg", "gsm", "cm", "mm", "m", "yd", "w", "kw", "v", "a",
            "pcs", "pc", "oz", "lb", "ml", "l", "tex", "dtex", "rpm", "nm");

    /** Excel 单元格，如 I413、K294、T15、AA10。1–3 个字母后只跟行号。 */
    private static final Pattern EXCEL_CELL = Pattern.compile("(?i)^[a-z]{1,3}\\d{1,7}$");

    /** 3D/12F 这种旦尼尔/根数，不是面料编号。 */
    private static final Pattern FIBER_COUNT = Pattern.compile("(?i)^\\d+(?:\\.\\d+)?d/\\d+(?:\\.\\d+)?f$");

    private static final Pattern QUANTITY = Pattern.compile("(?i)^\\d+(?:\\.\\d+)?[a-z]{1,4}$");

    /** 货号，如 25YK00022：至少两位数字、字母、再至少两位数字。 */
    private static final Pattern HUOHAO = Pattern.compile("(?i)\\d{2,}[a-z]{1,12}\\d{2,}");

    /**
     * 正文里出现的货号/面料编号/型号，保留原文大小写，供评测出题。
     * 同一编号只保留第一次出现。文件扩展名这类噪声不收。
     */
    public static List<String> surfaceCodes(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = CODE.matcher(text);
        while (matcher.find()) {
            String raw = matcher.group();
            if (!acceptCode(raw) || SURFACE_SKIP.contains(raw.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (seen.add(raw.toLowerCase(Locale.ROOT))) {
                out.add(raw);
            }
        }
        return List.copyOf(out);
    }

    /**
     * 评测题里值得当成标准答案的编号。索引仍然保留更宽的 {@link #acceptCode}，
     * 这里只是不出「98g」「7865.00KGS」「3D/12F」和 Excel 坐标这种题。
     */
    public static boolean isQuestionCode(String raw) {
        if (!acceptCode(raw) || SURFACE_SKIP.contains(raw.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (EXCEL_CELL.matcher(raw).matches() || FIBER_COUNT.matcher(raw).matches() || isQuantity(raw)) {
            return false;
        }
        return HUOHAO.matcher(raw).find() || isMaterialCode(raw) || isModelCode(raw);
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

    private static List<String> codeQueryTokens(String text) {
        List<String> ordered = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String raw : surfaceCodes(text)) {
            String full = raw.toLowerCase(Locale.ROOT);
            if (seen.add(full)) {
                ordered.add(full);
            }
            if (raw.indexOf('-') < 0 && raw.indexOf('/') < 0 && raw.indexOf('_') < 0 && raw.indexOf('.') < 0) {
                continue;
            }
            String squashed = full.replaceAll("[._/\\-]", "");
            if (squashed.length() >= 3 && seen.add(squashed)) {
                ordered.add(squashed);
            }
        }
        return ordered;
    }

    private static boolean isQuantity(String raw) {
        if (QUANTITY.matcher(raw).matches()) {
            Matcher suffix = Pattern.compile("(?i)[a-z]+$").matcher(raw);
            return suffix.find() && UNIT_SUFFIX.contains(suffix.group().toLowerCase(Locale.ROOT));
        }
        String[] parts = raw.split("[._/\\-]+");
        boolean sawUnit = false;
        for (String part : parts) {
            String letters = part.replaceAll("\\d", "");
            if (letters.isEmpty()) {
                continue;
            }
            if (!UNIT_SUFFIX.contains(letters.toLowerCase(Locale.ROOT))) {
                return false;
            }
            sawUnit = true;
        }
        return sawUnit;
    }

    private static boolean isMaterialCode(String raw) {
        if (raw.indexOf('-') < 0 && raw.indexOf('/') < 0 && raw.indexOf('_') < 0) {
            return false;
        }
        int letters = 0;
        int digits = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
            } else if (Character.isDigit(c)) {
                digits++;
            }
        }
        return letters >= 2 && digits >= 2;
    }

    private static boolean isModelCode(String raw) {
        if (raw.length() < 5 || EXCEL_CELL.matcher(raw).matches()) {
            return false;
        }
        int letters = 0;
        int digits = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isLetter(c)) {
                letters++;
            } else if (Character.isDigit(c)) {
                digits++;
            }
        }
        return letters >= 2 && digits >= 1;
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
