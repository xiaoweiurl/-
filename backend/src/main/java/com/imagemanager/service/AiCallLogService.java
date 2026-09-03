package com.imagemanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 能力调用日志服务（用量监控真实数据源）
 *
 * 设计原则：
 * 1. 只记录系统真实发生的 AI 调用（对话/联网搜索/Embedding 等），杜绝模拟数据
 * 2. 模型名全部取自 application.yml 实际配置，不硬编码
 * 3. record() 永不抛异常——写日志失败不得影响主业务流程
 * 4. overview() 聚合用量监控页所需的全部真实统计：
 *    服务健康（近24h成功率/平均延迟）、模型用量明细、最近调用记录、
 *    今日用量、7日调用趋势、系统限流配置
 */
@Slf4j
@Service
public class AiCallLogService {

    /** 能力标识常量（与前端能力清单一一对应） */
    public static final String CAP_SMART_CHAT = "smart-chat";
    public static final String CAP_FACTORY_CHAT = "factory-chat";
    public static final String CAP_WEB_SEARCH = "web-search";
    public static final String CAP_EMBEDDING = "embedding";
    public static final String CAP_AI_RECOGNIZE = "ai-recognize";
    public static final String CAP_QUOTATION = "quotation";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${ollama.chat-model:qwen3.6}")
    private String ollamaChatModel;

    @Value("${ollama.embedding-model:bge-m3}")
    private String ollamaEmbeddingModel;

    @Value("${ollama.vision-model:qwen3.6:35b}")
    private String ollamaVisionModel;

    @Value("${minimax.model:MiniMax-M3}")
    private String minimaxModel;

    /**
     * 记录一次 AI 调用（永不抛异常）
     *
     * @param capability 能力标识（CAP_* 常量）
     * @param model      实际模型名（传 null 时按能力自动取配置值）
     * @param success    是否成功
     * @param latencyMs  耗时毫秒
     * @param tokens     Token 消耗（无则传 null）
     * @param detail     简述（自动截断 300 字符）
     */
    public void record(String capability, String model, boolean success, long latencyMs,
                       Integer tokens, String detail) {
        try {
            if (model == null || model.isBlank()) {
                model = defaultModelFor(capability);
            }
            if (detail != null && detail.length() > 300) {
                detail = detail.substring(0, 300);
            }
            jdbcTemplate.update(
                    "INSERT INTO ai_call_log (capability, model, status, latency_ms, tokens, detail) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    capability, model, success ? "success" : "fail",
                    (int) Math.min(latencyMs, Integer.MAX_VALUE), tokens, detail);
        } catch (Exception e) {
            log.debug("[AI调用日志] 写入失败(静默): {}", e.getMessage());
        }
    }

    /** 按能力取配置中的真实模型名 */
    public String defaultModelFor(String capability) {
        switch (capability) {
            case CAP_WEB_SEARCH: return minimaxModel;
            case CAP_EMBEDDING: return ollamaEmbeddingModel;
            case CAP_AI_RECOGNIZE: return ollamaVisionModel;
            default: return ollamaChatModel;
        }
    }

    /** 当前配置的模型名（供健康状态在无调用记录时也能展示真实能力清单） */
    public Map<String, String> configuredModels() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("chat", ollamaChatModel);
        m.put("embedding", ollamaEmbeddingModel);
        m.put("vision", ollamaVisionModel);
        m.put("webSearch", minimaxModel);
        return m;
    }

    /**
     * 用量监控页总览数据（全部来自 ai_call_log 真实记录与 RateLimiter 真实配置）
     */
    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        try {
            out.put("health", buildHealth());
            out.put("modelUsage", buildModelUsage());
            out.put("recent", buildRecent(20));
            out.put("todayUsage", buildTodayUsage());
            out.put("trend", buildTrend(7));
            out.put("rateLimits", buildRateLimits());
            out.put("configuredModels", configuredModels());
        } catch (Exception e) {
            log.warn("[AI调用日志] overview 查询失败: {}", e.getMessage());
            out.putIfAbsent("health", List.of());
            out.putIfAbsent("modelUsage", List.of());
            out.putIfAbsent("recent", List.of());
            out.putIfAbsent("todayUsage", List.of());
            out.putIfAbsent("trend", List.of());
            out.putIfAbsent("rateLimits", List.of());
            out.putIfAbsent("configuredModels", configuredModels());
        }
        return out;
    }

    /** 服务健康：按模型分组，近 24h 真实成功率/平均延迟/调用数 */
    private List<Map<String, Object>> buildHealth() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT model, COUNT(*) AS calls, "
                        + "ROUND(100.0 * SUM(CASE WHEN status='success' THEN 1 ELSE 0 END) / COUNT(*), 1) AS success_rate, "
                        + "ROUND(AVG(latency_ms)) AS avg_latency "
                        + "FROM ai_call_log WHERE created_at > now() - interval '24 hours' "
                        + "GROUP BY model ORDER BY calls DESC");
        for (Map<String, Object> r : rows) {
            double rate = ((Number) r.get("success_rate")).doubleValue();
            r.put("level", rate >= 95 ? "normal" : (rate >= 80 ? "warning" : "error"));
        }
        return rows;
    }

    /** 模型用量明细：按能力+模型分组，累计调用/今日调用/Token 合计/平均延迟/成功率 */
    private List<Map<String, Object>> buildModelUsage() {
        return jdbcTemplate.queryForList(
                "SELECT capability, model, COUNT(*) AS calls, "
                        + "SUM(CASE WHEN created_at::date = CURRENT_DATE THEN 1 ELSE 0 END) AS today_calls, "
                        + "COALESCE(SUM(tokens), 0) AS tokens, "
                        + "ROUND(AVG(latency_ms)) AS avg_latency, "
                        + "ROUND(100.0 * SUM(CASE WHEN status='success' THEN 1 ELSE 0 END) / COUNT(*), 1) AS success_rate "
                        + "FROM ai_call_log GROUP BY capability, model ORDER BY calls DESC");
    }

    /** 最近调用记录（真实） */
    private List<Map<String, Object>> buildRecent(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT capability, model, status, latency_ms, tokens, detail, "
                        + "to_char(created_at, 'HH24:MI:SS') AS time, "
                        + "to_char(created_at, 'YYYY-MM-DD HH24:MI:SS') AS full_time "
                        + "FROM ai_call_log ORDER BY id DESC LIMIT ?", limit);
    }

    /** 今日用量：按能力分组（今日调用数/累计调用数，无编造配额） */
    private List<Map<String, Object>> buildTodayUsage() {
        return jdbcTemplate.queryForList(
                "SELECT capability, "
                        + "SUM(CASE WHEN created_at::date = CURRENT_DATE THEN 1 ELSE 0 END) AS today, "
                        + "COUNT(*) AS total "
                        + "FROM ai_call_log GROUP BY capability ORDER BY today DESC, total DESC");
    }

    /** 近 N 日调用趋势（真实，按天分组） */
    private List<Map<String, Object>> buildTrend(int days) {
        return jdbcTemplate.queryForList(
                "SELECT to_char(d.day, 'MM/DD') AS date, "
                        + "COALESCE(s.calls, 0) AS calls, "
                        + "COALESCE(s.success, 0) AS success, "
                        + "COALESCE(s.fail, 0) AS fail "
                        + "FROM generate_series(CURRENT_DATE - (? - 1), CURRENT_DATE, interval '1 day') AS d(day) "
                        + "LEFT JOIN (SELECT created_at::date AS day, COUNT(*) AS calls, "
                        + "  SUM(CASE WHEN status='success' THEN 1 ELSE 0 END) AS success, "
                        + "  SUM(CASE WHEN status='fail' THEN 1 ELSE 0 END) AS fail "
                        + "  FROM ai_call_log GROUP BY created_at::date) s ON s.day = d.day "
                        + "ORDER BY d.day", days);
    }

    /** 系统限流配置（RateLimiter 真实值 + 当前窗口真实计数） */
    private List<Map<String, Object>> buildRateLimits() {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, long[]> snapshot = com.imagemanager.util.RateLimiter.snapshot();
        out.add(rateRow("登录接口", "LOGIN", "5次/5分钟", snapshot));
        out.add(rateRow("修改密码", "PASSWORD_CHANGE", "3次/小时", snapshot));
        out.add(rateRow("文件上传", "UPLOAD", "20次/分钟", snapshot));
        out.add(rateRow("默认接口", "DEFAULT", "100次/分钟", snapshot));
        return out;
    }

    private Map<String, Object> rateRow(String name, String type, String limitText, Map<String, long[]> snapshot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("limit", limitText);
        long[] cur = snapshot.get(type);
        row.put("current", cur != null ? cur[0] : 0);
        row.put("rejected", cur != null ? cur[1] : 0);
        return row;
    }
}
