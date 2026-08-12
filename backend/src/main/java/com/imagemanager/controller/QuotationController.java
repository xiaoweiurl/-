package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.service.QuotationCalcService;
import com.imagemanager.tools.QuotationAssistant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报价单核算控制器
 *
 * 提供三种能力：
 * 1. /quotation/ai        - 自然语言问答（大模型 + @Tool 查询/计算，最稳方案）
 * 2. /quotation/calculate - 确定性核算（不经过大模型，纯 BigDecimal 计算）
 * 3. /quotation/query     - 关键字查询（参数化 SQL，安全）
 */
@Slf4j
@RestController
@RequestMapping("/quotation")
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationAssistant quotationAssistant;
    private final QuotationCalcService calcService;

    /**
     * 自然语言报价问答
     * body: {"question": "报价单 BJ2024001 的净成本是多少？"}
     */
    @PostMapping("/ai")
    public ApiResponse<Map<String, Object>> ai(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return ApiResponse.error("question 不能为空");
        }
        try {
            String answer = quotationAssistant.chat(question);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("question", question);
            data.put("answer", answer);
            return ApiResponse.success("报价问答成功", data);
        } catch (Exception e) {
            log.error("报价AI问答失败", e);
            return ApiResponse.error("报价AI问答失败: " + e.getMessage());
        }
    }

    /**
     * 确定性核算（不经过大模型）
     * body: {"dh": "BJ2024001", "fpkz": 0.25, "rsdj": 8, "rawTotal": 12.5, "auxTotal": 3.2}
     * 其中 fpkz/rsdj/rawTotal/auxTotal 为可选外部输入（表中没有的 BOM/染色参数）
     */
    @PostMapping("/calculate")
    public ApiResponse<Map<String, BigDecimal>> calculate(@RequestBody Map<String, Object> body) {
        Object dhObj = body.get("dh");
        if (dhObj == null || dhObj.toString().isBlank()) {
            return ApiResponse.error("dh(报价单号) 不能为空");
        }
        String dh = dhObj.toString();
        List<Map<String, Object>> rows = calcService.queryByDh(dh);
        if (rows.isEmpty()) {
            return ApiResponse.error("未找到报价单: " + dh);
        }

        Map<String, BigDecimal> overrides = new LinkedHashMap<>();
        putIfNumber(overrides, body, "fpkz");     // 缝拼克重
        putIfNumber(overrides, body, "rsdj");     // 染色单价
        putIfNumber(overrides, body, "rawTotal"); // 原料合计(BOM)
        putIfNumber(overrides, body, "auxTotal"); // 辅料合计(BOM)

        Map<String, BigDecimal> result = calcService.calculate(rows.get(0), overrides);
        return ApiResponse.success("核算成功", result);
    }

    /**
     * 关键字查询（参数化，安全）
     */
    @GetMapping("/query")
    public ApiResponse<List<Map<String, Object>>> query(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ApiResponse.error("keyword 不能为空");
        }
        return ApiResponse.success("查询成功", calcService.queryByKeyword(keyword.trim()));
    }

    private void putIfNumber(Map<String, BigDecimal> target, Map<String, Object> source, String key) {
        Object v = source.get(key);
        if (v == null) return;
        try {
            target.put(key, new BigDecimal(v.toString()));
        } catch (NumberFormatException ignored) {
        }
    }
}
