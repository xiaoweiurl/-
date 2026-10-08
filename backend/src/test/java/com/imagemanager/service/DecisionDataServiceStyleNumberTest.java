package com.imagemanager.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 只输入货号时要带出本公司商品库的打样信息和图片；没有货号则不查商品库。
 * 夹具数据，不连生产库。
 */
class DecisionDataServiceStyleNumberTest {

    private static final String COMPANY = "宝娜斯集团";
    private static final String OTHER_COMPANY = "其他公司";
    private static final String SIGNED_QUERY = "?sig=fixture&X-Amz-Signature=abc";

    private JdbcTemplate jdbcTemplate;
    private DecisionDataService service;
    private final List<List<Object>> goodsLibraryArgs = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        service = new DecisionDataService();
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(service, "fileStorageService", fileStorageService);
        when(fileStorageService.generatePresignedUrl(anyString(), anyInt())).thenAnswer(invocation ->
                "https://files.example/" + invocation.getArgument(0) + SIGNED_QUERY);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(this::answerQuery);
    }

    @Test
    void styleNumberOnlyLoadsThatCompanysGoodsLibraryPhotosAndSamplingInfo() {
        List<Map<String, Object>> results = service.searchStructuredForMessage("M19F011", null, COMPANY);

        Map<String, Object> goods = findType(results, "商品库文件夹");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) goods.get("data");
        assertEquals("豹纹长裤", data.get("品名"));
        assertEquals("余凌辉", data.get("打样员"));
        assertEquals("M19F011", data.get("货号"));
        assertEquals("/goods-library/34", data.get("productDetailPath"));
        assertEquals("/sampler/34", data.get("sampleOrderPath"));
        assertTrue(String.valueOf(data.get("主图图片URL")).contains("/main.jpg" + SIGNED_QUERY));
        assertTrue(String.valueOf(data.get("侧面图图片URL")).contains("/side.jpg"));
        assertTrue(String.valueOf(data.get("细节图图片URL")).contains("/detail.jpg"));
        assertFalse(data.containsKey("产品图图片URL"));
        assertFalse(data.get("主图图片URL").equals(data.get("侧面图图片URL")));

        Map<String, Object> missing = findType(results, "数据缺失说明");
        String gap = String.valueOf(missing.get("summary"));
        assertTrue(gap.contains("产品报价/成本"), gap);
        assertTrue(gap.contains("供应商交付"), gap);
        assertTrue(gap.contains("排产明细"), gap);
        assertFalse(gap.matches(".*\\d+\\.\\d{2}.*"), gap);

        assertFalse(goodsLibraryArgs.isEmpty());
        List<Object> args = goodsLibraryArgs.get(0);
        String sql = String.valueOf(args.get(0));
        assertTrue(sql.contains("FROM goods_library"), sql);
        assertTrue(sql.contains("u.company"), sql);
        assertTrue(sql.contains("g.user_id"), sql);
        assertTrue(args.contains("%M19F011%"));
        assertEquals(COMPANY, args.get(3));
        assertFalse(results.stream().anyMatch(entry -> "产品报价信息".equals(entry.get("type"))));
    }

    @Test
    void designerPathLoadsGoodsLibraryForStyleNumberOnly() {
        List<Map<String, Object>> results = service.searchGoodsLibraryForMessage("M19F011", COMPANY);

        Map<String, Object> goods = findType(results, "商品库文件夹");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) goods.get("data");
        assertEquals("豹纹长裤", data.get("品名"));
        assertEquals("余凌辉", data.get("打样员"));
        assertEquals("/goods-library/34", data.get("productDetailPath"));
        assertEquals("/sampler/34", data.get("sampleOrderPath"));
        assertTrue(String.valueOf(data.get("主图图片URL")).contains("/main.jpg"));
        assertTrue(String.valueOf(data.get("侧面图图片URL")).contains("/side.jpg"));
        assertFalse(results.stream().anyMatch(entry -> "数据缺失说明".equals(entry.get("type"))));
    }

    @Test
    void otherCompanyDoesNotReceiveThisCompanysGoodsLibraryRow() {
        List<Map<String, Object>> results = service.searchStructuredForMessage("M19F011", null, OTHER_COMPANY);

        assertTrue(results.stream().noneMatch(entry -> "商品库文件夹".equals(entry.get("type"))));
        assertTrue(results.stream().noneMatch(entry -> {
            Object data = entry.get("data");
            return data instanceof Map<?, ?> map && (map.containsKey("productDetailPath") || map.containsKey("sampleOrderPath"));
        }));
        assertFalse(goodsLibraryArgs.isEmpty());
        assertEquals(OTHER_COMPANY, goodsLibraryArgs.get(goodsLibraryArgs.size() - 1).get(3));
    }

    @Test
    void messageWithoutStyleNumberDoesNotQueryGoodsLibrary() {
        List<Map<String, Object>> plain = service.searchStructuredForMessage("红色长裤", null, COMPANY);
        List<Map<String, Object>> keywordsOnly = service.searchGoodsLibraryForMessage("看看主图和产品图", COMPANY);
        List<Map<String, Object>> english = service.searchStructuredForMessage("hello", null, COMPANY);

        assertTrue(plain.isEmpty());
        assertTrue(keywordsOnly.isEmpty());
        assertTrue(english.isEmpty());
        assertTrue(goodsLibraryArgs.isEmpty());
        verify(jdbcTemplate, never()).queryForList(anyString(), any(Object[].class));
    }

    private Object answerQuery(InvocationOnMock invocation) {
        String sql = invocation.getArgument(0);
        List<Object> flat = new ArrayList<>();
        flat.add(sql);
        Object[] raw = invocation.getArguments();
        for (int i = 1; i < raw.length; i++) {
            if (raw[i] instanceof Object[] nested) {
                for (Object item : nested) {
                    flat.add(item);
                }
            } else if (raw[i] != null) {
                flat.add(raw[i]);
            }
        }
        if (sql.contains("FROM goods_library")) {
            goodsLibraryArgs.add(flat);
            Object viewer = flat.size() > 3 ? flat.get(3) : null;
            if (COMPANY.equals(viewer)) {
                return List.of(goodsRow());
            }
            return List.of();
        }
        return List.of();
    }

    private static Map<String, Object> goodsRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 34L);
        row.put("folder_name", "M19F011豹纹长裤");
        row.put("initiator", "张三");
        row.put("sampler", "余凌辉");
        row.put("product_name", "豹纹长裤");
        row.put("goods_no", "M19F011");
        row.put("customer", null);
        row.put("order_no", null);
        row.put("main_image_key", "goods-library/M19F011/main.jpg");
        row.put("side_image_key", "goods-library/M19F011/side.jpg");
        row.put("detail_image_key", "goods-library/M19F011/detail.jpg");
        row.put("product_image_key", "");
        row.put("remark", null);
        return row;
    }

    private static Map<String, Object> findType(List<Map<String, Object>> results, String type) {
        return results.stream()
                .filter(entry -> type.equals(entry.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少条目: " + type + "，实际=" + results));
    }
}
