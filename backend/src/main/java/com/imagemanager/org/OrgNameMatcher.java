package com.imagemanager.org;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 钉钉通讯录姓名匹配：去首尾空白、去掉中间空格后精确比较（忽略英文大小写）。
 * 不使用模糊匹配，避免同名以外的人被自动注册。
 */
public final class OrgNameMatcher {

    private OrgNameMatcher() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().replaceAll("[\\s\\u3000]+", "");
    }

    public static boolean namesEqual(String left, String right) {
        String a = normalize(left);
        String b = normalize(right);
        return !a.isEmpty() && a.equalsIgnoreCase(b);
    }

    /**
     * 与注册/开户相同：username 或 nickname 任一命中即视为同一人。
     */
    public static boolean matchesUserDisplayName(String name, String username, String nickname) {
        return namesEqual(name, username) || namesEqual(name, nickname);
    }

    /**
     * 按原样 + 去空白两种键查找本地用户（与钉钉免登姓名匹配一致）。
     */
    public static List<String> lookupKeys(String name) {
        if (isBlank(name) || normalize(name).isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        String trimmed = name.trim();
        keys.add(trimmed);
        String normalized = normalize(name);
        if (!normalized.equals(trimmed)) {
            keys.add(normalized);
        }
        return keys;
    }

    public static <T> List<T> matchExact(String name, List<T> items, Function<T, String> nameFn) {
        if (items == null || items.isEmpty() || normalize(name).isEmpty()) {
            return List.of();
        }
        List<T> matched = new ArrayList<>();
        for (T item : items) {
            if (item == null) {
                continue;
            }
            if (namesEqual(name, nameFn.apply(item))) {
                matched.add(item);
            }
        }
        return matched;
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    public static String requireName(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("请输入姓名");
        }
        return Objects.requireNonNull(name).trim();
    }
}
