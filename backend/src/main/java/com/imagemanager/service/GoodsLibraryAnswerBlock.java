package com.imagemanager.service;

import java.util.List;
import java.util.Map;

/**
 * 货号命中本公司商品库后，由后端追加到回答末尾的文字。
 * 货号和打样单是站内链接；每张真实图片用 markdown 图片语法带上完整签名 URL。
 * 签名 URL 不放进模型上下文，避免模型复述被截断的地址。
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

    public static String render(List<Map<String, Object>> structuredResults) {
        if (structuredResults == null || structuredResults.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> entry : structuredResults) {
            if (entry == null || !"商品库文件夹".equals(String.valueOf(entry.get("type")))) {
                continue;
            }
            Object dataObj = entry.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                continue;
            }
            String block = renderOne(data);
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

    private static String renderOne(Map<?, ?> data) {
        StringBuilder block = new StringBuilder();
        String goodsNo = plain(data.get("货号"));
        String detail = goodsPath(data.get("productDetailPath"), "/goods-library/");
        String sample = goodsPath(data.get("sampleOrderPath"), "/sampler/");
        if (!goodsNo.isEmpty() && !detail.isEmpty()) {
            block.append("商品库：[").append(markdownLabel(goodsNo)).append("](").append(detail).append(")\n\n");
        } else if (!goodsNo.isEmpty()) {
            block.append("货号：").append(goodsNo).append("\n\n");
        }
        appendFact(block, "品名", data.get("品名"));
        appendFact(block, "打样员", data.get("打样员"));
        if (!sample.isEmpty()) {
            block.append("[打样单](").append(sample).append(")\n\n");
        }
        for (String[] slot : SLOTS) {
            String url = markdownDestination(data.get(slot[1]));
            if (url.isEmpty()) {
                continue;
            }
            block.append("![").append(slot[0]).append("](").append(url).append(")\n\n");
        }
        return block.toString();
    }

    private static void appendFact(StringBuilder block, String label, Object value) {
        String text = plain(value);
        if (text.isEmpty()) {
            return;
        }
        block.append(label).append("：").append(text).append("\n\n");
    }

    static String markdownDestination(Object value) {
        String url = plain(value);
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return "";
        }
        if (url.indexOf(' ') >= 0 || url.indexOf('\n') >= 0 || url.indexOf('\r') >= 0
                || url.indexOf('<') >= 0 || url.indexOf('>') >= 0) {
            return "";
        }
        return url.replace("(", "%28").replace(")", "%29");
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
