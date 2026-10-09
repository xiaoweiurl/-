package com.imagemanager.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只输入货号时，回答末尾要带上商品库/打样单链接，以及这条记录真实有的每一张图。
 */
class GoodsLibraryAnswerBlockTest {

    @Test
    void rendersLinksWithoutPuttingSignedUrlsInTheAnswer() {
        String block = GoodsLibraryAnswerBlock.render(List.of(goodsEntry()));

        assertTrue(block.contains("[M19F011](/goods-library/34)"), block);
        assertTrue(block.contains("[打样单](/sampler/34)"), block);
        assertTrue(block.contains("品名：豹纹长裤"), block);
        assertTrue(block.contains("打样员：余凌辉"), block);
        assertFalse(block.contains("https://"), block);
        assertFalse(block.contains("!["), block);
        assertFalse(block.contains("产品图"), block);
        assertFalse(block.contains("javascript:"), block);
        assertFalse(block.matches("(?s).*\\d+\\.\\d{2}.*"), block);

        List<Map<String, Object>> photos = GoodsLibraryAnswerBlock.entries(List.of(goodsEntry()));
        assertEquals(1, photos.size());
        assertEquals("/goods-library/34", photos.get(0).get("productDetailPath"));
        assertEquals("/sampler/34", photos.get(0).get("sampleOrderPath"));
        assertEquals("豹纹长裤", photos.get(0).get("productName"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) photos.get(0).get("images");
        assertEquals(List.of("主图", "侧面图", "细节图"), images.stream().map(image -> image.get("slotLabel")).toList());
        assertTrue(String.valueOf(images.get(0).get("imageUrl")).contains("/main.jpg"));
        assertFalse(String.valueOf(images.get(0).get("imageUrl")).equals(String.valueOf(images.get(1).get("imageUrl"))));
        assertTrue(GoodsLibraryAnswerBlock.entries(List.of(tampered())).isEmpty());
    }

    @Test
    void citationCarriesDistinctPhotosAndDropsForeignPaths() {
        List<Map<String, Object>> sources = ChatCitation.collectSources(
                List.of(), List.of(), List.of(), List.of(goodsEntry(), tampered()), List.of(), List.of(), false);

        Map<String, Object> goods = sources.get(0);
        assertEquals("商品库文件夹", goods.get("title"));
        assertEquals("M19F011", goods.get("goodsNo"));
        assertEquals("/goods-library/34", goods.get("productDetailPath"));
        assertEquals("/sampler/34", goods.get("sampleOrderPath"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) goods.get("images");
        assertEquals(List.of("主图", "侧面图", "细节图"), images.stream().map(image -> image.get("slotLabel")).toList());
        assertTrue(String.valueOf(images.get(0).get("imageUrl")).contains("/main.jpg"));
        assertTrue(String.valueOf(images.get(1).get("imageUrl")).contains("/side.jpg"));
        assertFalse(images.get(0).get("imageUrl").equals(images.get(1).get("imageUrl")));
        assertFalse(String.valueOf(goods.get("excerpt")).contains("https://files.example"));

        Map<String, Object> foreign = sources.get(1);
        assertFalse(foreign.containsKey("productDetailPath"));
        assertFalse(foreign.containsKey("sampleOrderPath"));
        assertFalse(foreign.containsKey("images"));
    }

    @Test
    void messageWithoutGoodsRowDoesNotInventLinks() {
        assertEquals("", GoodsLibraryAnswerBlock.render(List.of()));
        Map<String, Object> plain = new LinkedHashMap<>();
        plain.put("type", "数据缺失说明");
        plain.put("summary", "暂无");
        assertEquals("", GoodsLibraryAnswerBlock.render(List.of(plain)));
    }

    private static Map<String, Object> goodsEntry() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("记录ID", 34L);
        data.put("品名", "豹纹长裤");
        data.put("货号", "M19F011");
        data.put("打样员", "余凌辉");
        data.put("productDetailPath", "/goods-library/34");
        data.put("sampleOrderPath", "/sampler/34");
        data.put("主图图片URL", "https://files.example/main.jpg?sig=fixture&X-Amz-Signature=abc");
        data.put("侧面图图片URL", "https://files.example/side.jpg?sig=fixture&X-Amz-Signature=abc");
        data.put("细节图图片URL", "https://files.example/detail.jpg?sig=one");
        data.put("产品图图片URL", "not-a-url");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "商品库文件夹");
        entry.put("summary", "商品库文件夹");
        entry.put("data", data);
        return entry;
    }

    private static Map<String, Object> tampered() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("记录ID", 8L);
        data.put("货号", "OTHER");
        data.put("productDetailPath", "https://evil.test/goods-library/8");
        data.put("sampleOrderPath", "/sampler/8/edit");
        data.put("主图图片URL", "javascript:alert(1)");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "商品库文件夹");
        entry.put("summary", "越权");
        entry.put("data", data);
        return entry;
    }
}
