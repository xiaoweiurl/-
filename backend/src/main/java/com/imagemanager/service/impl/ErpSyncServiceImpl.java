package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.imagemanager.config.ErpProperties;
import com.imagemanager.service.ErpAuthService;
import com.imagemanager.service.ErpClient;
import com.imagemanager.service.ErpSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ERP 数据同步服务实现
 *
 * 增量同步逻辑：以 erp_sync_state.last_sync_time（数据库最新同步游标）为起点、
 * 当前时间为终点过滤数据；首次同步按全量处理（起点取 90 天前）。
 *
 * 订单模块（orders）真实模式下使用 ERP 原生时间参数 dates/datee 过滤；
 * 工艺类模块 ERP 端不支持时间过滤，拉取后按本地游标记录增量语义。
 *
 * 演示模式（demo-enabled=true 或 ERP 不可达自动降级）：按时间跨度生成
 * 模拟增量记录，保证页面进度/日志/状态完整可演示。
 */
@Slf4j
@Service
public class ErpSyncServiceImpl implements ErpSyncService {

    /** 模块定义（与接口文档一一对应） */
    private record ModuleDef(String key, String name, String endpoint, boolean supportsTimeFilter,
                             int dailyVolume) {}

    private static final List<ModuleDef> MODULES = List.of(
            new ModuleDef("orders", "销售订单", "OrderPrice/getOrdeListQuery.aspx", true, 12),
            new ModuleDef("neiyi-gongyidan", "内衣工艺单", "Technology/NGyMainQuery.aspx", false, 5),
            new ModuleDef("siwa-gongyidan", "丝袜工艺单", "Technology/SGyMainQuery.aspx", false, 4),
            new ModuleDef("gongyi-bujian", "工艺部件", "Technology/NGyBujQuery.aspx", false, 20),
            new ModuleDef("gongyi-gongxu", "工艺工序", "Technology/NGyWorkTypeQuery.aspx", false, 25),
            new ModuleDef("gongxu-gongjia", "工序工价", "Technology/NGyHuohaoPriceQuery.aspx", false, 15),
            new ModuleDef("yuanliao-bom", "原料BOM", "Technology/MaterialYLQuery.aspx", false, 18)
    );

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 首次同步默认回看天数 */
    private static final int FIRST_SYNC_LOOKBACK_DAYS = 90;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ErpAuthService erpAuthService;
    private final ErpClient erpClient;
    private final ErpProperties erpProperties;

    public ErpSyncServiceImpl(JdbcTemplate jdbcTemplate,
                              TransactionTemplate transactionTemplate,
                              ErpAuthService erpAuthService,
                              ErpClient erpClient,
                              ErpProperties erpProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.erpAuthService = erpAuthService;
        this.erpClient = erpClient;
        this.erpProperties = erpProperties;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> modules = new ArrayList<>();
        long totalRecords = 0;
        int syncedModules = 0;
        LocalDateTime latestSync = null;

        for (ModuleDef def : MODULES) {
            Map<String, Object> state = loadState(def.key());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("moduleKey", def.key());
            m.put("moduleName", def.name());
            m.put("endpoint", def.endpoint());
            m.put("supportsTimeFilter", def.supportsTimeFilter());
            m.put("lastSyncTime", state.get("last_sync_time"));
            m.put("totalRecords", state.get("total_records"));
            m.put("lastAdded", state.get("last_added"));
            m.put("lastStatus", state.get("last_status"));
            m.put("lastMessage", state.get("last_message"));
            m.put("lastDuration", state.get("last_duration_ms"));
            modules.add(m);

            Number tr = (Number) state.getOrDefault("total_records", 0L);
            totalRecords += tr.longValue();
            if (!"never".equals(state.get("last_status"))) {
                syncedModules++;
            }
            Object lst = state.get("last_sync_time");
            if (lst instanceof LocalDateTime t && (latestSync == null || t.isAfter(latestSync))) {
                latestSync = t;
            }
        }

        // 今日同步次数与失败数（日志表统计）
        Map<String, Object> today = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS today_count, "
                        + "COUNT(*) FILTER (WHERE status = 'failed') AS failed_count "
                        + "FROM erp_sync_log WHERE created_at::date = CURRENT_DATE");

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("moduleCount", MODULES.size());
        summary.put("syncedModules", syncedModules);
        summary.put("totalRecords", totalRecords);
        summary.put("todaySyncCount", ((Number) today.get("today_count")).intValue());
        summary.put("failedCount", ((Number) today.get("failed_count")).intValue());
        summary.put("latestSyncTime", latestSync != null ? latestSync.format(FMT) : null);

        out.put("modules", modules);
        out.put("summary", summary);
        return out;
    }

    @Override
    public Map<String, Object> syncModule(String moduleKey) {
        ModuleDef def = MODULES.stream()
                .filter(m -> m.key().equals(moduleKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知同步模块: " + moduleKey));
        return doSync(def);
    }

    @Override
    public List<Map<String, Object>> syncAll() {
        List<Map<String, Object>> logs = new ArrayList<>();
        for (ModuleDef def : MODULES) {
            logs.add(doSync(def));
        }
        return logs;
    }

    @Override
    public List<Map<String, Object>> getLogs(int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, module_key, module_name, sync_type, range_start, range_end, added, failed, "
                        + "status, duration_ms, message, source, created_at "
                        + "FROM erp_sync_log ORDER BY created_at DESC, id DESC LIMIT ?", size);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", row.get("id"));
            m.put("moduleKey", row.get("module_key"));
            m.put("moduleName", row.get("module_name"));
            m.put("syncType", row.get("sync_type"));
            m.put("rangeStart", fmtTs(row.get("range_start")));
            m.put("rangeEnd", fmtTs(row.get("range_end")));
            m.put("added", row.get("added"));
            m.put("failed", row.get("failed"));
            m.put("status", row.get("status"));
            m.put("duration", row.get("duration_ms"));
            m.put("message", row.get("message"));
            m.put("source", row.get("source"));
            m.put("time", fmtTs(row.get("created_at")));
            out.add(m);
        }
        return out;
    }

    @Override
    public void clearLogs() {
        transactionTemplate.executeWithoutResult(tx -> jdbcTemplate.update("DELETE FROM erp_sync_log"));
    }

    // ==================== 同步核心逻辑 ====================

    /**
     * 执行单模块增量同步（含日志落库）
     */
    private Map<String, Object> doSync(ModuleDef def) {
        String token = erpAuthService.getToken();
        if (token == null || token.isBlank()) {
            // 401：未登录，前端据此跳转 ERP 登录
            throw new ErpClient.ErpAuthException("ERP 未登录，请先登录获取 token");
        }

        long startMs = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();
        // 增量起点：数据库最新同步时间；首次回看 90 天（全量）
        LocalDateTime lastSync = loadLastSyncTime(def.key());
        boolean incremental = lastSync != null;
        LocalDateTime rangeStart = incremental ? lastSync : now.minusDays(FIRST_SYNC_LOOKBACK_DAYS);

        int added = 0;
        int failed = 0;
        String status = "success";
        String message;
        String source = "erp";

        try {
            if (erpProperties.isDemoEnabled() || isDemoToken(token)) {
                // 演示模式：按时间跨度生成模拟增量数据
                added = generateDemoIncrement(def, rangeStart, now);
                source = "demo";
                simulateLatency();
                message = buildSyncMessage(def, added, rangeStart, now, incremental, true);
            } else {
                try {
                    added = fetchRealCount(def, rangeStart, now, token);
                    message = buildSyncMessage(def, added, rangeStart, now, incremental, false);
                } catch (ErpClient.ErpAuthException e) {
                    // token 失效：清理 token，要求重新登录
                    erpAuthService.clearToken();
                    throw e;
                } catch (ErpClient.ErpNetworkException e) {
                    // ERP 不可达：降级为演示数据，保证同步流程可演示
                    log.warn("[ERP同步] {} 真实 ERP 不可达，降级演示数据: {}", def.name(), e.getMessage());
                    added = generateDemoIncrement(def, rangeStart, now);
                    source = "demo";
                    simulateLatency();
                    message = buildSyncMessage(def, added, rangeStart, now, incremental, true)
                            + "（ERP 不可达，已降级演示数据）";
                }
            }
        } catch (ErpClient.ErpAuthException e) {
            throw e;
        } catch (Exception e) {
            status = "failed";
            failed = 1;
            message = "同步失败: " + e.getMessage();
            log.error("[ERP同步] {} 同步失败", def.name(), e);
        }

        long duration = System.currentTimeMillis() - startMs;
        persistSyncResult(def, rangeStart, now, added, failed, status, message, duration, source, incremental);

        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("moduleKey", def.key());
        logEntry.put("moduleName", def.name());
        logEntry.put("syncType", incremental ? "incremental" : "full");
        logEntry.put("rangeStart", rangeStart.format(FMT));
        logEntry.put("rangeEnd", now.format(FMT));
        logEntry.put("added", added);
        logEntry.put("failed", failed);
        logEntry.put("status", status);
        logEntry.put("duration", duration);
        logEntry.put("message", message);
        logEntry.put("source", source);
        return logEntry;
    }

    /** 真实模式：调用 ERP 接口统计增量条数 */
    private int fetchRealCount(ModuleDef def, LocalDateTime rangeStart, LocalDateTime now, String token) {
        Map<String, String> params = new LinkedHashMap<>();
        if (def.supportsTimeFilter()) {
            // 订单模块：ERP 原生时间过滤 dates/datee
            params.put("dates", rangeStart.format(FMT));
            params.put("datee", now.format(FMT));
        } else {
            // 工艺类模块：ERP 端不支持时间过滤，空参全量拉取，本地记录增量语义
            params.put("id", "");
        }
        JsonNode result = erpClient.callApi(def.endpoint(), params, token);
        return result != null && result.isArray() ? result.size() : 0;
    }

    /** 演示模式：按时间跨度 × 模块日均量生成增量记录数（含随机波动） */
    private int generateDemoIncrement(ModuleDef def, LocalDateTime rangeStart, LocalDateTime now) {
        long hours = Math.max(Duration.between(rangeStart, now).toHours(), 1);
        double days = hours / 24.0;
        double base = def.dailyVolume() * Math.min(days, 30);
        int jitter = ThreadLocalRandom.current().nextInt(0, def.dailyVolume() * 2 + 1);
        return (int) Math.round(base * (0.6 + ThreadLocalRandom.current().nextDouble() * 0.8)) + jitter;
    }

    /** 模拟真实同步耗时（300~1500ms） */
    private void simulateLatency() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(300, 1200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildSyncMessage(ModuleDef def, int added, LocalDateTime rangeStart, LocalDateTime now,
                                    boolean incremental, boolean demo) {
        String type = incremental ? "增量同步" : "首次全量同步";
        String range = rangeStart.format(FMT) + " → " + now.format(FMT);
        String src = demo ? "演示数据" : "ERP";
        if (added == 0) {
            return type + "完成：范围内无新增数据（" + range + "，来源 " + src + "）";
        }
        return type + "完成：新增 " + added + " 条记录（" + range + "，来源 " + src + "）";
    }

    /** 状态表 upsert + 日志表 insert（同一事务） */
    private void persistSyncResult(ModuleDef def, LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                   int added, int failed, String status, String message,
                                   long duration, String source, boolean incremental) {
        transactionTemplate.executeWithoutResult(tx -> {
            if ("success".equals(status)) {
                jdbcTemplate.update(
                        "INSERT INTO erp_sync_state (module_key, module_name, last_sync_time, total_records, "
                                + "last_added, last_status, last_message, last_duration_ms, updated_at) "
                                + "VALUES (?,?,?,?,?,?,?,?,now()) "
                                + "ON CONFLICT (module_key) DO UPDATE SET "
                                + "last_sync_time = EXCLUDED.last_sync_time, "
                                + "total_records = erp_sync_state.total_records + EXCLUDED.total_records, "
                                + "last_added = EXCLUDED.last_added, last_status = EXCLUDED.last_status, "
                                + "last_message = EXCLUDED.last_message, "
                                + "last_duration_ms = EXCLUDED.last_duration_ms, updated_at = now()",
                        def.key(), def.name(), Timestamp.valueOf(rangeEnd), added, added, status, message, duration);
            } else {
                jdbcTemplate.update(
                        "INSERT INTO erp_sync_state (module_key, module_name, last_status, last_message, "
                                + "last_duration_ms, updated_at) VALUES (?,?,?,?,?,now()) "
                                + "ON CONFLICT (module_key) DO UPDATE SET "
                                + "last_status = EXCLUDED.last_status, last_message = EXCLUDED.last_message, "
                                + "last_duration_ms = EXCLUDED.last_duration_ms, updated_at = now()",
                        def.key(), def.name(), status, message, duration);
            }
            jdbcTemplate.update(
                    "INSERT INTO erp_sync_log (module_key, module_name, sync_type, range_start, range_end, "
                            + "added, failed, status, duration_ms, message, source, created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,now())",
                    def.key(), def.name(), incremental ? "incremental" : "full",
                    Timestamp.valueOf(rangeStart), Timestamp.valueOf(rangeEnd),
                    added, failed, status, duration, message, source);
        });
    }

    private Map<String, Object> loadState(String moduleKey) {
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT last_sync_time, total_records, last_added, last_status, last_message, last_duration_ms "
                            + "FROM erp_sync_state WHERE module_key = ?", moduleKey);
        } catch (EmptyResultDataAccessException e) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("last_sync_time", null);
            empty.put("total_records", 0L);
            empty.put("last_added", 0);
            empty.put("last_status", "never");
            empty.put("last_message", null);
            empty.put("last_duration_ms", 0L);
            return empty;
        }
    }

    private LocalDateTime loadLastSyncTime(String moduleKey) {
        try {
            Timestamp ts = jdbcTemplate.queryForObject(
                    "SELECT last_sync_time FROM erp_sync_state WHERE module_key = ? AND last_status = 'success'",
                    Timestamp.class, moduleKey);
            return ts != null ? ts.toLocalDateTime() : null;
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private boolean isDemoToken(String token) {
        return token != null && token.startsWith("demo-");
    }

    private String fmtTs(Object v) {
        if (v == null) return null;
        if (v instanceof Timestamp ts) return ts.toLocalDateTime().format(FMT);
        return String.valueOf(v);
    }
}
