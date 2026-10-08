package com.imagemanager.imagesearch;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把中文条件收成可执行的筛选。时间、范围、打样员、相册走硬过滤；
 * 颜色、材质、款式没有对应列，只留给中文向量重排。
 */
public final class ImageSearchConditionParser {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final Pattern YEAR_MONTH = Pattern.compile("(?<![0-9])(20\\d{2})年(?:\\s*(\\d{1,2})月)?");

    /** 长词在前，避免「红色」吃掉「酒红色」里的一段。 */
    private static final String[] ATTRIBUTES = {
            "酒红色", "藏青色", "卡其色", "莫代尔", "冲锋衣", "滑雪服",
            "针织", "梭织", "蕾丝", "雪纺", "牛仔", "真丝", "纯棉", "棉麻", "氨纶", "涤纶", "锦纶", "网纱", "罗纹", "毛呢", "皮革",
            "连帽", "短款", "长款", "修身", "宽松", "高领", "圆领", "拉链", "印花", "条纹", "格纹", "碎花", "纯色", "开衫", "卫衣",
            "米色", "粉色", "紫色", "棕色", "灰色", "橙色", "黄色", "绿色", "蓝色", "黑色", "白色", "红色", "金色", "银色", "彩色", "棉"
    };

    private ImageSearchConditionParser() {
    }

    public static ImageSearchCondition.Parsed parse(String text, ImageSearchCondition.Lexicon lexicon, Clock clock) {
        return parse(text, lexicon, clock, Set.of(), ImageSearchCondition.Hint.none());
    }

    public static ImageSearchCondition.Parsed parse(String text,
                                                    ImageSearchCondition.Lexicon lexicon,
                                                    Clock clock,
                                                    String exclude) {
        return parse(text, lexicon, clock, excludedIds(exclude), ImageSearchCondition.Hint.none());
    }

    public static ImageSearchCondition.Parsed parse(String text,
                                                    ImageSearchCondition.Lexicon lexicon,
                                                    Clock clock,
                                                    Set<String> exclude,
                                                    ImageSearchCondition.Hint hint) {
        String raw = text == null ? "" : text.trim();
        ImageSearchCondition.Lexicon words = lexicon == null ? ImageSearchCondition.Lexicon.EMPTY : lexicon;
        Set<String> skipped = exclude == null ? Set.of() : exclude;
        ImageSearchCondition.Hint extra = hint == null ? ImageSearchCondition.Hint.none() : hint;
        if (raw.isEmpty() && extra == ImageSearchCondition.Hint.none()) {
            return ImageSearchCondition.Parsed.empty();
        }

        String scope = skipped.contains("scope") ? null : matchScope(raw);
        ImageSearchCondition.Window time = skipped.contains("time") ? null : matchTime(raw, clock);
        String sampler = skipped.contains("sampler") ? null : matchSampler(raw, words.samplerNames());
        String album = skipped.contains("album") ? null : matchAlbum(raw, words.albumNames());
        List<String> attributes = new ArrayList<>(matchAttributes(raw));

        if (scope == null && !skipped.contains("scope")) {
            scope = normalizeScope(extra.scope());
        }
        if (sampler == null && !skipped.contains("sampler")) {
            sampler = exactName(extra.sampler(), words.samplerNames());
        }
        if (album == null && !skipped.contains("album")) {
            album = exactName(extra.album(), words.albumNames());
        }
        String samplerName = sampler;
        String albumName = album;
        attributes.removeIf(term -> term.equals(samplerName) || term.equals(albumName) || skipped.contains("attr:" + term));
        for (String term : extra.attributes()) {
            if (term == null || skipped.contains("attr:" + term)) {
                continue;
            }
            if (isAttribute(term) && !attributes.contains(term) && !term.equals(sampler) && !term.equals(album)) {
                attributes.add(term);
            }
        }

        List<ImageSearchCondition.Chip> chips = new ArrayList<>();
        if (time != null) {
            chips.add(chip("time", "time", time.label(), time.label()));
        }
        if (scope != null) {
            String label = ImageSearchFilters.SOURCE_LIBRARY.equals(scope) ? "只看素材" : "只看打样";
            chips.add(chip("scope", "scope", label, scope));
        }
        if (sampler != null) {
            chips.add(chip("sampler", "sampler", "打样员 " + sampler, sampler));
        }
        if (album != null) {
            chips.add(chip("album", "album", albumLabel(album), album));
        }
        for (String term : attributes) {
            chips.add(chip("attr:" + term, "attribute", term, term));
        }
        return new ImageSearchCondition.Parsed(scope, time, sampler, album, attributes, chips);
    }

    public static Set<String> excludedIds(String raw) {
        Set<String> ids = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return ids;
        }
        for (String part : raw.split(",")) {
            String id = part.trim();
            if (id.isEmpty() || id.length() > 40) {
                continue;
            }
            if ("time".equals(id) || "scope".equals(id) || "sampler".equals(id) || "album".equals(id)
                    || id.startsWith("attr:")) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static ImageSearchCondition.Chip chip(String id, String kind, String label, String value) {
        return new ImageSearchCondition.Chip(id, kind, label, value, true, false);
    }

    private static String matchScope(String text) {
        int libraryAt = earliest(text, "只看素材", "只要素材", "仅看素材", "仅要素材", "只看素材库");
        int goodsAt = earliest(text, "只看打样", "只看商品", "只看产品", "仅看打样", "只要打样", "只看商品库", "只看打样库");
        if (libraryAt < 0 && goodsAt < 0) {
            return null;
        }
        if (libraryAt >= 0 && (goodsAt < 0 || libraryAt <= goodsAt)) {
            return ImageSearchFilters.SOURCE_LIBRARY;
        }
        return ImageSearchFilters.SOURCE_GOODS;
    }

    private static int earliest(String text, String... phrases) {
        int found = -1;
        for (String phrase : phrases) {
            int at = text.indexOf(phrase);
            if (at >= 0 && (found < 0 || at < found)) {
                found = at;
            }
        }
        return found;
    }

    private static ImageSearchCondition.Window matchTime(String text, Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock == null ? Clock.system(ZONE) : clock.withZone(ZONE));
        if (containsAny(text, "最近半年", "近半年", "半年内", "半年以来")) {
            return relative(now.minusMonths(6), now, "最近半年");
        }
        if (containsAny(text, "最近三个月", "近三个月", "最近3个月", "近3个月", "三个月内")) {
            return relative(now.minusMonths(3), now, "最近三个月");
        }
        if (containsAny(text, "最近一个月", "近一个月", "最近1个月", "近1个月", "一个月内", "近一月")) {
            return relative(now.minusMonths(1), now, "最近一个月");
        }
        if (containsAny(text, "最近一周", "近一周", "最近7天", "一周内")) {
            return relative(now.minusDays(7), now, "最近一周");
        }
        if (containsAny(text, "最近一年", "近一年", "一年内")) {
            return relative(now.minusYears(1), now, "最近一年");
        }
        if (text.contains("今年")) {
            ZonedDateTime start = now.withDayOfYear(1).toLocalDate().atStartOfDay(ZONE);
            return relative(start, now, "今年");
        }
        if (text.contains("去年")) {
            ZonedDateTime start = now.withDayOfYear(1).minusYears(1).toLocalDate().atStartOfDay(ZONE);
            ZonedDateTime end = now.withDayOfYear(1).toLocalDate().atStartOfDay(ZONE);
            return between(start, end, "去年");
        }
        if (text.contains("本月")) {
            ZonedDateTime start = now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE);
            return relative(start, now, "本月");
        }
        if (text.contains("上个月") || text.contains("上月")) {
            ZonedDateTime start = now.withDayOfMonth(1).minusMonths(1).toLocalDate().atStartOfDay(ZONE);
            ZonedDateTime end = now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZONE);
            return between(start, end, "上个月");
        }
        Matcher matcher = YEAR_MONTH.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(1));
        String monthText = matcher.group(2);
        if (monthText != null) {
            int month = Integer.parseInt(monthText);
            if (month >= 1 && month <= 12) {
                ZonedDateTime start = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZONE);
                return between(start, start.plusMonths(1), year + "年" + month + "月");
            }
        }
        ZonedDateTime start = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZONE);
        return between(start, start.plusYears(1), year + "年");
    }

    private static ImageSearchCondition.Window relative(ZonedDateTime from, ZonedDateTime now, String label) {
        long end = now.toInstant().toEpochMilli() + 1;
        return new ImageSearchCondition.Window(from.toInstant().toEpochMilli(), end, label);
    }

    private static ImageSearchCondition.Window between(ZonedDateTime from, ZonedDateTime to, String label) {
        return new ImageSearchCondition.Window(from.toInstant().toEpochMilli(), to.toInstant().toEpochMilli(), label);
    }

    private static String matchSampler(String text, List<String> names) {
        for (String name : byLength(names)) {
            if (name.length() < 2) {
                continue;
            }
            if (!text.contains(name)) {
                continue;
            }
            if (text.contains(name + "打")
                    || text.contains("打样员" + name)
                    || text.contains("打样员是" + name)
                    || text.contains("打样员：" + name)
                    || text.contains("打样员:" + name)
                    || text.contains(name + "的样")) {
                return name;
            }
        }
        return null;
    }

    private static String matchAlbum(String text, List<String> names) {
        for (String name : byLength(names)) {
            if (name.length() < 2 || !text.contains(name)) {
                continue;
            }
            if (text.contains(name + "相册") || text.contains(name + "分类")
                    || text.contains("相册" + name) || text.contains("分类" + name)
                    || (name.endsWith("相册") && text.contains("相册"))
                    || (name.endsWith("分类") && text.contains("分类"))) {
                return name;
            }
        }
        return null;
    }

    private static List<String> matchAttributes(String text) {
        if (text.isEmpty()) {
            return List.of();
        }
        boolean[] used = new boolean[text.length()];
        List<String> found = new ArrayList<>();
        for (String term : ATTRIBUTES) {
            int from = 0;
            while (from < text.length()) {
                int at = text.indexOf(term, from);
                if (at < 0) {
                    break;
                }
                int end = at + term.length();
                if (!overlap(used, at, end)) {
                    mark(used, at, end);
                    if (!found.contains(term)) {
                        found.add(term);
                    }
                }
                from = end;
            }
        }
        return found;
    }

    private static boolean isAttribute(String term) {
        for (String known : ATTRIBUTES) {
            if (known.equals(term)) {
                return true;
            }
        }
        return false;
    }

    private static String albumLabel(String album) {
        if (album.endsWith("相册") || album.endsWith("分类")) {
            return album;
        }
        return album + "相册";
    }

    private static String exactName(String wanted, List<String> names) {
        if (wanted == null || wanted.isBlank() || names == null) {
            return null;
        }
        String trimmed = wanted.trim();
        for (String name : names) {
            if (name != null && trimmed.equals(name.trim())) {
                return name.trim();
            }
        }
        return null;
    }

    private static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        String folded = scope.trim().toLowerCase(Locale.ROOT);
        if (ImageSearchFilters.SOURCE_LIBRARY.equals(folded) || "素材".equals(scope.trim())) {
            return ImageSearchFilters.SOURCE_LIBRARY;
        }
        if (ImageSearchFilters.SOURCE_GOODS.equals(folded) || "打样".equals(scope.trim()) || "商品".equals(scope.trim())) {
            return ImageSearchFilters.SOURCE_GOODS;
        }
        return null;
    }

    private static List<String> byLength(List<String> names) {
        List<String> ordered = new ArrayList<>();
        if (names == null) {
            return ordered;
        }
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                ordered.add(name.trim());
            }
        }
        ordered.sort((left, right) -> Integer.compare(right.length(), left.length()));
        return ordered;
    }

    private static boolean containsAny(String text, String... phrases) {
        for (String phrase : phrases) {
            if (text.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean overlap(boolean[] used, int from, int to) {
        for (int i = from; i < to; i++) {
            if (used[i]) {
                return true;
            }
        }
        return false;
    }

    private static void mark(boolean[] used, int from, int to) {
        for (int i = from; i < to; i++) {
            used[i] = true;
        }
    }
}
