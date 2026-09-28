package com.imagemanager.milvus.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/** 解析 --key=value、--dry-run、--demo、--help。 */
public final class ToolArgs {

    private ToolArgs() {
    }

    public static Map<String, String> parse(String[] args) {
        Map<String, String> map = new LinkedHashMap<>();
        if (args == null) {
            return map;
        }
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if ("--help".equals(arg) || "-h".equals(arg)) {
                map.put("help", "true");
                continue;
            }
            if ("--dry-run".equals(arg)) {
                map.put("dry-run", "true");
                continue;
            }
            if ("--demo".equals(arg)) {
                map.put("demo", "true");
                continue;
            }
            if (arg.startsWith("--") && arg.indexOf('=') > 2) {
                int eq = arg.indexOf('=');
                map.put(arg.substring(2, eq), arg.substring(eq + 1));
                continue;
            }
            throw new IllegalArgumentException("无法识别的参数: " + arg + "。请使用 --key=value");
        }
        return map;
    }

    public static String text(Map<String, String> args, String key, String fallback) {
        String value = args.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public static int integer(Map<String, String> args, String key, int fallback) {
        String value = args.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }

    public static boolean flag(Map<String, String> args, String key) {
        return "true".equalsIgnoreCase(args.get(key));
    }
}
