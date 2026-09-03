package com.imagemanager.controller;

import com.imagemanager.service.AiCallLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 能力用量监控接口（真实调用数据，无任何模拟）
 */
@Slf4j
@RestController
@RequestMapping("/ai-usage")
@Tag(name = "AI用量监控", description = "AI 能力真实调用统计（服务健康/模型用量/调用记录/限流配置）")
public class AiUsageController {

    @Autowired
    private AiCallLogService aiCallLogService;

    @GetMapping("/overview")
    @Operation(summary = "用量监控总览", description = "返回服务健康状态、模型用量明细、最近调用记录、今日用量、7日趋势、限流配置，全部来自 ai_call_log 真实记录")
    public ResponseEntity<Map<String, Object>> overview() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 200);
        body.put("message", "success");
        body.put("data", aiCallLogService.overview());
        return ResponseEntity.ok(body);
    }
}
