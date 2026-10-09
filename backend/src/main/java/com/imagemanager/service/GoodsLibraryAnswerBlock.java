package com.imagemanager.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 货号命中本公司商品库后，回答里要带上站内链接，照片单独走 SSE。
 * 签名 URL 不写进回答正文，避免被截断、被 markdown 吃掉，或让前端以为图已经在正文里而藏起真图。
 */
public final class GoodsLibraryAnswerBlock {

    private static final String[][] SLOTS = {
            {"主图", "主图图片URL"},
            {"侧面图", "侧面图图片URL"},
            {"细节图", "细节图图片URL"},
            {"产品图", "产品图图片URL"}
    };

    private GoodsLibraryAnswerBlock() {
    }

    /** 追加到回答末尾的文字：货号、品名、打样员、打样单。不含图片地址。 */
    public static String render(List<Map<String, Object>> structuredResults) {
        if (structuredResults == null || structuredResults.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> entry : entries(structuredResults)) {
            String block = renderOne(entry);
            if (block.isEmpty()) {
                continue;
            }
            if (out.isEmpty()) {
                out.append("\n\n---\n\n");
            }
            out.append(block);
        }
        return out.toString();
    }

    /**
     * 发给前端的商品库照片。每条只含已核对的站内路径和真实 http(s) 槽位。
     */
    public static List<Map<String, Object>> entries(List<Map<String, Object>> structuredResults) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (structuredResults == null) {
            return out;
        }
        for (Map<String, Object> entry : structuredResults) {
            if (entry == null || !"商品库文件夹".equals(String.valueOf(entry.get("type")))) {
                continue;
            }
            Object dataObj = entry.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                continue;
            }
            Map<String, Object> one = entryOf(data);
            if (one != null) {
                out.add(one);
            }
        }
        return out;
    }

    private static String renderOne(Map<String, Object> entry) {
        StringBuilder block = new StringBuilder();
        String goodsNo = plain(entry.get("goodsNo"));
        String detail = plain(entry.get("productDetailPath"));
        String sample = plain(entry.get("sampleOrderPath"));
        if (!goodsNo.isEmpty() && !detail.isEmpty()) {
            block.append("商品库：[").append(markdownLabel(goodsNo)).append("](").append(detail).append(")\n\n");
        } else if (!goodsNo.isEmpty()) {
            block.append("货号：").append(goodsNo).append("\n\n");
        }
        appendFact(block, "品名", entry.get("productName"));
        appendFact(block, "打样员", entry.get("sampler"));
        if (!sample.isEmpty()) {
            block.append("[打样单](").append(sample).append(")\n\n");
        }
        return block.toString();
    }

    private static Map<String, Object> entryOf(Map<?, ?> data) {
        String detail = goodsPath(data.get("productDetailPath"), "/goods-library/");
        String sample = goodsPath(data.get("sampleOrderPath"), "/sampler/");
        if (detail.isEmpty() && sample.isEmpty()) {
            return null;
        }
        Map<String, Object> entry = new LinkedHashMap<>();
        putPlain(entry, "goodsNo", data.get("货号"));
        putPlain(entry, "productName", data.get("品名"));
        putPlain(entry, "sampler", data.get("打样员"));
        if (!detail.isEmpty()) {
            entry.put("productDetailPath", detail);
        }
        if (!sample.isEmpty()) {
            entry.put("sampleOrderPath", sample);
        }
        List<Map<String, Object>> images = new ArrayList<>();
        for (String[] slot : SLOTS) {
            String url = httpUrl(data.get(slot[1]));
            if (url.isEmpty()) {
                continue;
            }
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("imageUrl", url);
            image.put("slotLabel", slot[0]);
            images.add(image);
        }
        if (!images.isEmpty()) {
            entry.put("images", images);
        }
        return entry;
    }

    private static void appendFact(StringBuilder block, String label, Object value) {
        String text = plain(value);
        if (text.isEmpty()) {
            return;
        }
        block.append(label).append("：").append(text).append("\n\n");
    }

    private static void putPlain(Map<String, Object> entry, String key, Object value) {
        String text = plain(value);
        if (!text.isEmpty()) {
            entry.put(key, text);
        }
    }

    private static String httpUrl(Object value) {
        String url = plain(value);
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return "";
        }
        if (url.indexOf(' ') >= 0 || url.indexOf('\n') >= 0 || url.indexOf('\r') >= 0
                || url.indexOf('<') >= 0 || url.indexOf('>') >= 0) {
            return "";
        }
        return url;
    }

    private static String markdownLabel(String text) {
        return text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
    }

    private static String goodsPath(Object value, String prefix) {
        String path = plain(value);
        if (!path.startsWith(prefix)) {
            return "";
        }
        String id = path.substring(prefix.length());
        return id.matches("[1-9]\\d{0,18}") ? prefix + id : "";
    }

    private static String plain(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return "";
        }
        return text;
    }
}
