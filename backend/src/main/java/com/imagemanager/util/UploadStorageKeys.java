package com.imagemanager.util;

import java.net.URI;

/**
 * 将本地上传 URL / 路径归一成对象存储 key（如 {@code images/{uuid}.jpg}）。
 */
public final class UploadStorageKeys {

    private UploadStorageKeys() {
    }

    /**
     * 是否为本地上传代理路径（相对或带主机），需要走 /uploads 回源或刷新为 OSS 预签名。
     */
    public static boolean isLocalUploadUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String path = url;
        if (isHttp(url)) {
            try {
                String uriPath = URI.create(url).getPath();
                if (uriPath != null && !uriPath.isEmpty()) {
                    path = uriPath;
                }
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return path.startsWith("/api/uploads/") || path.startsWith("/uploads/");
    }

    /**
     * 从 fileKey / filePath / url 中解析存储 key。优先已是对象 key 的字段。
     */
    public static String resolveStorageKey(String fileKey, String filePath, String url) {
        String fromKey = fromUrlOrPath(fileKey);
        if (fromKey != null) {
            return fromKey;
        }
        String fromPath = fromUrlOrPath(filePath);
        if (fromPath != null) {
            return fromPath;
        }
        return fromUrlOrPath(url);
    }

    /**
     * 去掉 {@code /api/uploads/}、{@code /uploads/} 及 URL 包装，得到相对存储 key。
     * 绝对本地文件系统路径无法识别时返回 null。
     */
    public static String fromUrlOrPath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String path = value.trim();
        if (isHttp(path)) {
            try {
                String uriPath = URI.create(path).getPath();
                if (uriPath != null && !uriPath.isEmpty()) {
                    path = uriPath;
                }
            } catch (IllegalArgumentException ignored) {
                // keep original
            }
        }

        path = stripLocalUploadPrefix(path);
        if (path == null || path.isEmpty()) {
            return null;
        }
        if (path.contains("..") || path.startsWith("/") || path.contains("\\")
                || path.contains(":") || path.startsWith("./")) {
            return null;
        }
        return path;
    }

    /**
     * 去掉 {@code api/uploads/} 或 {@code uploads/} 前缀（可带或不带前导 /）。
     */
    public static String stripLocalUploadPrefix(String key) {
        if (key == null) {
            return "";
        }
        String k = key;
        if (k.startsWith("/")) {
            k = k.substring(1);
        }
        if (k.startsWith("api/uploads/")) {
            return k.substring("api/uploads/".length());
        }
        if (k.startsWith("uploads/")) {
            return k.substring("uploads/".length());
        }
        return k;
    }

    private static boolean isHttp(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }
}
