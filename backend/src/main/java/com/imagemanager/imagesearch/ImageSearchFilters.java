package com.imagemanager.imagesearch;

import java.util.Locale;
import java.util.Set;

/**
 * 向量 ID、公司过滤和集合名约束。
 * 素材库沿用 images.company；商品库/打样沿用现有规则（已登录可见，不按公司裁剪）。
 * 打样会话由 SamplerSessionGuard 拦截 /image-search，到不了这里。
 */
public final class ImageSearchFilters {

    public static final String DEFAULT_COMPANY = "宝娜斯集团";
    public static final String SOURCE_LIBRARY = "library";
    public static final String SOURCE_GOODS = "goods";

    private static final Set<String> SLOTS = Set.of("main", "side", "detail", "product");
    private static final Set<String> FORBIDDEN_COLLECTIONS = Set.of(
            "salesperson_docs",
            "salesperson_docs_hybrid",
            "salesperson_chunks");

    private ImageSearchFilters() {
    }

    public static void assertCollectionName(String name) {
        if (name == null || !name.matches("image_[a-z0-9_]{1,48}")) {
            throw new IllegalArgumentException(
                    "图片向量集合名必须以 image_ 开头，且只含小写字母、数字和下划线");
        }
        if (FORBIDDEN_COLLECTIONS.contains(name) || name.contains("salesperson")) {
            throw new IllegalArgumentException("禁止操作系统已有的业务员资料集合: " + name);
        }
    }

    public static String libraryVectorId(String imageId) {
        String id = requireToken(imageId, "图片 ID");
        return "lib:" + id;
    }

    public static String goodsVectorId(long goodsId, String slot) {
        if (goodsId <= 0) {
            throw new IllegalArgumentException("商品 ID 无效");
        }
        return "goods:" + goodsId + ":" + normalizeSlot(slot);
    }

    public static String normalizeSlot(String slot) {
        if (slot == null || slot.isBlank()) {
            return "";
        }
        String normalized = slot.trim().toLowerCase(Locale.ROOT);
        if (!SLOTS.contains(normalized)) {
            throw new IllegalArgumentException("未知图片槽位");
        }
        return normalized;
    }

    public static String slotLabel(String slot) {
        return switch (normalizeSlot(slot)) {
            case "main" -> "主图";
            case "side" -> "侧面";
            case "detail" -> "细节";
            case "product" -> "产品图";
            default -> "素材";
        };
    }

    public static String normalizeScope(String scope) {
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return "all";
        }
        String normalized = scope.trim().toLowerCase(Locale.ROOT);
        if (!SOURCE_LIBRARY.equals(normalized) && !SOURCE_GOODS.equals(normalized)) {
            throw new IllegalArgumentException("scope 只能是 all、library 或 goods");
        }
        return normalized;
    }

    public static String normalizeCompany(String company, String fallback) {
        String value = company == null ? "" : company.trim();
        if (value.isEmpty()) {
            value = fallback == null || fallback.isBlank() ? DEFAULT_COMPANY : fallback.trim();
        }
        return value;
    }

    /**
     * 写入向量库和过滤时用同一截断，避免公司名过长时对不上。
     * 权限判断仍用数据库里的完整公司名。
     */
    public static String companyKey(String company, String fallback) {
        return cut(normalizeCompany(company, fallback), 20);
    }

    public static String toMilvus(String scope, String company) {
        String quotedCompany = milvusQuote(companyKey(company, DEFAULT_COMPANY));
        return switch (normalizeScope(scope)) {
            case SOURCE_LIBRARY -> "source == \"library\" and company == \"" + quotedCompany + "\"";
            case SOURCE_GOODS -> "source == \"goods\"";
            default -> "(source == \"goods\") or (source == \"library\" and company == \"" + quotedCompany + "\")";
        };
    }

    /**
     * 和 toMilvus 同一套规则，查询结果再滤一遍，避免向量库过滤表达式写错时串数据。
     */
    public static boolean matches(ImageVectorRecord record, String scope, String company) {
        if (record == null) {
            return false;
        }
        String normalizedScope = normalizeScope(scope);
        String expectedCompany = companyKey(company, DEFAULT_COMPANY);
        if (SOURCE_GOODS.equals(record.source())) {
            return !"library".equals(normalizedScope);
        }
        if (!SOURCE_LIBRARY.equals(record.source())) {
            return false;
        }
        if (SOURCE_GOODS.equals(normalizedScope)) {
            return false;
        }
        return expectedCompany.equals(companyKey(record.company(), DEFAULT_COMPANY));
    }

    public static String milvusQuote(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String fileNameOf(String key) {
        if (key == null || key.isBlank()) {
            return "image.jpg";
        }
        String path = key;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.isBlank()) {
            return "image.jpg";
        }
        return name.length() > 180 ? name.substring(name.length() - 180) : name;
    }

    public static String resolveStorageKey(String fileKey, String filePath, String url) {
        if (notBlank(fileKey)) {
            return fileKey.trim();
        }
        if (notBlank(filePath)) {
            return filePath.trim();
        }
        if (notBlank(url)) {
            return url.trim();
        }
        return "";
    }

    public static String cut(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars);
    }

    /**
     * Milvus VarChar 的 max_length 是字节数。中文按字符截断后仍可能超限，导致整条插入失败。
     */
    public static String cutUtf8(String value, int maxBytes) {
        if (value == null || maxBytes <= 0) {
            return "";
        }
        String trimmed = value.trim();
        int used = 0;
        int index = 0;
        while (index < trimmed.length()) {
            int codePoint = trimmed.codePointAt(index);
            int size = utf8Size(codePoint);
            if (used + size > maxBytes) {
                break;
            }
            used += size;
            index += Character.charCount(codePoint);
        }
        return trimmed.substring(0, index);
    }

    private static int utf8Size(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }

    private static String requireToken(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "为空");
        }
        String trimmed = value.trim();
        if (trimmed.length() > 64 || trimmed.indexOf('"') >= 0 || trimmed.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(label + "无效");
        }
        return trimmed;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
