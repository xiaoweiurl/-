package com.imagemanager.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 系统运维服务
 * 从数据库读取真实的API指标、错误、性能数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpsService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 获取API调用指标
     */
    public Map<String, Object> getMetrics(String period, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String company = getCompanyFromRequest(request);

        // 计算时间范围
        LocalDateTime since = calculateSince(period);

        // 1. 总体统计
        String statsSql = "SELECT " +
                "COUNT(*) as total_calls, " +
                "SUM(CASE WHEN status_code < 400 THEN 1 ELSE 0 END) as success_calls, " +
                "SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END) as error_calls, " +
                "AVG(response_time_ms) as avg_response_ms, " +
                "PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY response_time_ms) as p50_ms, " +
                "PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY response_time_ms) as p99_ms " +
                "FROM api_metrics WHERE created_at >= ?" +
                (company != null ? " AND company = ?" : "");

        Object[] statsParams = company != null ? new Object[]{since, company} : new Object[]{since};
        Map<String, Object> stats = jdbcTemplate.queryForMap(statsSql, statsParams);
        result.put("overview", stats);

        // 2. 24h请求趋势（按小时分组）
        String trendSql = "SELECT " +
                "DATE_TRUNC('hour', created_at) as hour, " +
                "COUNT(*) as total, " +
                "SUM(CASE WHEN status_code < 400 THEN 1 ELSE 0 END) as success, " +
                "SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END) as errors " +
                "FROM api_metrics WHERE created_at >= ?" +
                (company != null ? " AND company = ?" : "") +
                " GROUP BY DATE_TRUNC('hour', created_at) ORDER BY hour";

        List<Map<String, Object>> trend = jdbcTemplate.queryForList(trendSql, statsParams);
        result.put("trend", trend);

        // 3. 端点调用排行（Top 15）
        String endpointSql = "SELECT " +
                "endpoint, method, " +
                "COUNT(*) as call_count, " +
                "AVG(response_time_ms) as avg_response_ms, " +
                "SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END) as error_count " +
                "FROM api_metrics WHERE created_at >= ?" +
                (company != null ? " AND company = ?" : "") +
                " GROUP BY endpoint, method ORDER BY call_count DESC LIMIT 15";

        List<Map<String, Object>> topEndpoints = jdbcTemplate.queryForList(endpointSql, statsParams);
        result.put("topEndpoints", topEndpoints);

        // 4. 系统资源（最新一条性能快照）
        String resourceSql = "SELECT cpu_usage, memory_used_mb, memory_total_mb, " +
                "disk_used_mb, disk_total_mb, active_connections " +
                "FROM performance_snapshots WHERE 1=1" +
                (company != null ? " AND company = ?" : "") +
                " ORDER BY recorded_at DESC LIMIT 1";

        Object[] resourceParams = company != null ? new Object[]{company} : new Object[]{};
        try {
            Map<String, Object> resource = jdbcTemplate.queryForMap(resourceSql, resourceParams);
            result.put("systemResources", resource);
        } catch (Exception e) {
            result.put("systemResources", Collections.emptyMap());
        }

        result.put("period", period);
        return result;
    }

    /**
     * 获取错误列表
     */
    public Map<String, Object> getErrors(String severity, Boolean resolved, int page, int pageSize, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String company = getCompanyFromRequest(request);

        StringBuilder whereSql = new StringBuilder("WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (severity != null && !severity.isEmpty()) {
            whereSql.append(" AND severity = ?");
            params.add(severity);
        }
        if (resolved != null) {
            whereSql.append(" AND resolved = ?");
            params.add(resolved);
        }
        if (company != null) {
            whereSql.append(" AND company = ?");
            params.add(company);
        }

        // 统计
        String countSql = "SELECT COUNT(*) as total, " +
                "SUM(CASE WHEN severity = 'critical' THEN 1 ELSE 0 END) as critical_count, " +
                "SUM(CASE WHEN severity = 'error' THEN 1 ELSE 0 END) as error_count, " +
                "SUM(CASE WHEN severity = 'warning' THEN 1 ELSE 0 END) as warning_count " +
                "FROM system_errors " + whereSql;

        Map<String, Object> counts = jdbcTemplate.queryForMap(countSql, params.toArray());
        result.put("stats", counts);

        // 分页查询
        int offset = (page - 1) * pageSize;
        String listSql = "SELECT id, error_type, severity, message, endpoint, method, " +
                "status_code, resolved, occurrence_count, first_seen_at, last_seen_at, created_at " +
                "FROM system_errors " + whereSql +
                " ORDER BY last_seen_at DESC LIMIT ? OFFSET ?";

        params.add(pageSize);
        params.add(offset);
        List<Map<String, Object>> errors = jdbcTemplate.queryForList(listSql, params.toArray());
        result.put("errors", errors);
        result.put("total", counts.get("total"));
        result.put("page", page);
        result.put("pageSize", pageSize);

        return result;
    }

    /**
     * 标记错误已解决
     */
    public Map<String, Object> resolveError(String id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String userId = getUserIdFromRequest(request);

        int updated = jdbcTemplate.update(
                "UPDATE system_errors SET resolved = true, resolved_by = ?, resolved_at = NOW() WHERE id = ?",
                userId, id);

        result.put("success", updated > 0);
        result.put("id", id);
        return result;
    }

    /**
     * 获取性能指标
     */
    public Map<String, Object> getPerformance(String period, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String company = getCompanyFromRequest(request);
        LocalDateTime since = calculateSince(period);

        // 1. 各服务性能概览（从api_metrics按端点前缀分组）
        String serviceSql = "SELECT " +
                "SPLIT_PART(endpoint, '/', 2) as service, " +
                "COUNT(*) as total_requests, " +
                "AVG(response_time_ms) as avg_response_ms, " +
                "PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY response_time_ms) as p50_ms, " +
                "PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY response_time_ms) as p99_ms, " +
                "SUM(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END)::float / COUNT(*) as error_rate " +
                "FROM api_metrics WHERE created_at >= ?" +
                (company != null ? " AND company = ?" : "") +
                " GROUP BY SPLIT_PART(endpoint, '/', 2) ORDER BY total_requests DESC";

        Object[] params = company != null ? new Object[]{since, company} : new Object[]{since};
        List<Map<String, Object>> services = jdbcTemplate.queryForList(serviceSql, params);
        result.put("services", services);

        // 2. 慢查询Top10
        String slowSql = "SELECT endpoint, method, response_time_ms, status_code, " +
                "created_at, user_id, error_message " +
                "FROM api_metrics WHERE created_at >= ? AND response_time_ms > 1000" +
                (company != null ? " AND company = ?" : "") +
                " ORDER BY response_time_ms DESC LIMIT 10";

        List<Map<String, Object>> slowQueries = jdbcTemplate.queryForList(slowSql, params);
        result.put("slowQueries", slowQueries);

        // 3. 运行时指标（最新性能快照）
        String runtimeSql = "SELECT cpu_usage, memory_used_mb, memory_total_mb, memory_usage_pct, " +
                "jvm_heap_used_mb, jvm_heap_max_mb, jvm_gc_count, jvm_thread_count, " +
                "node_rss_mb, node_heap_used_mb, " +
                "db_active_connections, db_slow_queries, " +
                "active_connections, recorded_at " +
                "FROM performance_snapshots WHERE 1=1" +
                (company != null ? " AND company = ?" : "") +
                " ORDER BY recorded_at DESC LIMIT 1";

        Object[] runtimeParams = company != null ? new Object[]{company} : new Object[]{};
        try {
            Map<String, Object> runtime = jdbcTemplate.queryForMap(runtimeSql, runtimeParams);
            result.put("runtime", runtime);
        } catch (Exception e) {
            result.put("runtime", Collections.emptyMap());
        }

        result.put("period", period);
        return result;
    }

    /**
     * 获取备份列表
     */
    public Map<String, Object> getBackups(int page, int pageSize, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String company = getCompanyFromRequest(request);

        String whereClause = company != null ? "WHERE company = ?" : "";
        Object[] countParams = company != null ? new Object[]{company} : new Object[]{};

        String countSql = "SELECT COUNT(*) as total FROM backup_records " + whereClause;
        Map<String, Object> count = jdbcTemplate.queryForMap(countSql, countParams);

        int offset = (page - 1) * pageSize;
        String listSql = "SELECT id, name, type, size_bytes, status, description, " +
                "started_at, completed_at, error_message, created_by, created_at " +
                "FROM backup_records " + whereClause +
                " ORDER BY created_at DESC LIMIT ? OFFSET ?";

        List<Object> listParams = new ArrayList<>(Arrays.asList(countParams));
        listParams.add(pageSize);
        listParams.add(offset);

        List<Map<String, Object>> backups = jdbcTemplate.queryForList(listSql, listParams.toArray());
        result.put("backups", backups);
        result.put("total", count.get("total"));
        result.put("page", page);
        result.put("pageSize", pageSize);

        return result;
    }

    /**
     * 创建备份
     */
    public Map<String, Object> createBackup(String type, String description, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String userId = getUserIdFromRequest(request);
        String company = getCompanyFromRequest(request);
        String id = UUID.randomUUID().toString();
        String name = type + "_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        jdbcTemplate.update(
                "INSERT INTO backup_records (id, name, type, status, description, created_by, company, started_at) " +
                        "VALUES (?, ?, ?, 'running', ?, ?, ?, NOW())",
                id, name, type, description, userId, company);

        // 异步执行备份（实际项目中应异步调用）
        try {
            long sizeBytes = performBackup(type, id);
            jdbcTemplate.update(
                    "UPDATE backup_records SET status = 'completed', size_bytes = ?, completed_at = NOW() WHERE id = ?",
                    sizeBytes, id);
            result.put("status", "completed");
        } catch (Exception e) {
            jdbcTemplate.update(
                    "UPDATE backup_records SET status = 'failed', error_message = ? WHERE id = ?",
                    e.getMessage(), id);
            result.put("status", "failed");
            result.put("error", e.getMessage());
        }

        result.put("id", id);
        result.put("name", name);
        result.put("success", true);
        return result;
    }

    /**
     * 记录API调用指标
     */
    public void recordMetric(Map<String, Object> metric) {
        try {
            String id = UUID.randomUUID().toString();
            jdbcTemplate.update(
                    "INSERT INTO api_metrics (id, endpoint, method, status_code, response_time_ms, " +
                            "user_id, ip_address, user_agent, request_size, response_size, error_message, company) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id,
                    metric.get("endpoint"),
                    metric.get("method"),
                    metric.getOrDefault("statusCode", 200),
                    metric.getOrDefault("responseTimeMs", 0),
                    metric.get("userId"),
                    metric.get("ipAddress"),
                    metric.get("userAgent"),
                    metric.getOrDefault("requestSize", 0),
                    metric.getOrDefault("responseSize", 0),
                    metric.get("errorMessage"),
                    metric.get("company"));
        } catch (Exception e) {
            log.warn("[Ops] 记录API指标失败: {}", e.getMessage());
        }
    }

    // ========== 私有方法 ==========

    private long performBackup(String type, String id) {
        // 实际备份逻辑：pg_dump、文件复制等
        // 此处返回模拟大小，生产环境替换为真实备份
        log.info("[Ops] 执行备份: type={}, id={}", type, id);
        return 1024 * 1024 * 100; // 100MB placeholder
    }

    private LocalDateTime calculateSince(String period) {
        return switch (period) {
            case "1h" -> LocalDateTime.now().minusHours(1);
            case "6h" -> LocalDateTime.now().minusHours(6);
            case "24h" -> LocalDateTime.now().minusHours(24);
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            default -> LocalDateTime.now().minusHours(24);
        };
    }

    private String getCompanyFromRequest(HttpServletRequest request) {
        Object company = request.getAttribute("company");
        return company != null ? company.toString() : null;
    }

    private String getUserIdFromRequest(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        return userId != null ? userId.toString() : null;
    }
}
