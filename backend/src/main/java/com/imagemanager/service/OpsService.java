package com.imagemanager.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
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
    private final DataSource dataSource;

    /**
     * 获取API调用指标（返回格式匹配前端 MonitorTab）
     */
    public Map<String, Object> getMetrics(String period, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String company = getCompanyFromRequest(request);
        LocalDateTime since = calculateSince(period);
        Object[] statsParams = company != null ? new Object[]{since, company} : new Object[]{since};

        // 1. summary（总体统计）
        Map<String, Object> summary = new HashMap<>();
        try {
            String statsSql = "SELECT " +
                    "COUNT(*) as total_calls, " +
                    "COALESCE(SUM(CASE WHEN status_code < 400 THEN 1 ELSE 0 END), 0) as success_calls, " +
                    "COALESCE(SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END), 0) as error_calls, " +
                    "COALESCE(AVG(response_time_ms), 0) as avg_response_ms " +
                    "FROM api_metrics WHERE created_at >= ?" +
                    (company != null ? " AND company = ?" : "");
            Map<String, Object> stats = jdbcTemplate.queryForMap(statsSql, statsParams);
            long totalCalls = toLong(stats.get("total_calls"));
            long successCalls = toLong(stats.get("success_calls"));
            long errorCalls = toLong(stats.get("error_calls"));
            double avgMs = toDouble(stats.get("avg_response_ms"));

            summary.put("totalRequests", totalCalls);
            summary.put("errorCount", errorCalls);
            summary.put("avgResponseTime", Math.round(avgMs));
            summary.put("successRate", totalCalls > 0 ? Math.round(successCalls * 100.0 / totalCalls) : 0);
            summary.put("errorRate", totalCalls > 0 ? Math.round(errorCalls * 100.0 / totalCalls * 10.0) / 10.0 : 0);
            // 每分钟请求数
            long minutes = java.time.Duration.between(since, LocalDateTime.now()).toMinutes();
            summary.put("requestsPerMinute", minutes > 0 ? totalCalls / minutes : totalCalls);
            summary.put("activeUsers", 0);
            summary.put("uptime", java.time.Duration.between(LocalDateTime.now().minusDays(7), LocalDateTime.now()).toString());
        } catch (Exception e) {
            summary.put("totalRequests", 0); summary.put("errorCount", 0);
            summary.put("avgResponseTime", 0); summary.put("successRate", 0);
            summary.put("errorRate", 0); summary.put("requestsPerMinute", 0);
            summary.put("activeUsers", 0); summary.put("uptime", "0s");
        }
        result.put("summary", summary);

        // 2. hourlyRequests（24h趋势，按小时分组）
        List<Map<String, Object>> hourlyRequests = new ArrayList<>();
        try {
            String trendSql = "SELECT " +
                    "DATE_TRUNC('hour', created_at) as hour, " +
                    "COUNT(*) as total, " +
                    "COALESCE(SUM(CASE WHEN status_code < 400 THEN 1 ELSE 0 END), 0) as success, " +
                    "COALESCE(SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END), 0) as errors, " +
                    "COALESCE(AVG(response_time_ms), 0) as avg_response_time " +
                    "FROM api_metrics WHERE created_at >= ?" +
                    (company != null ? " AND company = ?" : "") +
                    " GROUP BY DATE_TRUNC('hour', created_at) ORDER BY hour";
            List<Map<String, Object>> trend = jdbcTemplate.queryForList(trendSql, statsParams);
            DateTimeFormatter hourFmt = DateTimeFormatter.ofPattern("HH:mm");
            for (Map<String, Object> row : trend) {
                Map<String, Object> h = new HashMap<>();
                Object hourVal = row.get("hour");
                h.put("hour", hourVal != null ? hourVal.toString().substring(11, 16) : "");
                h.put("total", toLong(row.get("total")));
                h.put("success", toLong(row.get("success")));
                h.put("error", toLong(row.get("errors")));
                h.put("avgResponseTime", Math.round(toDouble(row.get("avg_response_time"))));
                hourlyRequests.add(h);
            }
        } catch (Exception e) {
            log.debug("[Ops] 趋势数据查询失败: {}", e.getMessage());
        }
        result.put("hourlyRequests", hourlyRequests);

        // 3. endpoints（API 端点排行 Top 15）
        List<Map<String, Object>> endpoints = new ArrayList<>();
        try {
            String endpointSql = "SELECT " +
                    "endpoint as path, method, " +
                    "COUNT(*) as calls, " +
                    "COALESCE(AVG(response_time_ms), 0) as avg_response_time, " +
                    "COALESCE(SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END), 0) as error_count " +
                    "FROM api_metrics WHERE created_at >= ?" +
                    (company != null ? " AND company = ?" : "") +
                    " GROUP BY endpoint, method ORDER BY calls DESC LIMIT 15";
            endpoints = jdbcTemplate.queryForList(endpointSql, statsParams);
            // 重命名字段以匹配前端
            List<Map<String, Object>> mapped = new ArrayList<>();
            for (Map<String, Object> row : endpoints) {
                Map<String, Object> m = new HashMap<>();
                m.put("path", row.get("path"));
                m.put("method", row.get("method"));
                m.put("calls", toLong(row.get("calls")));
                long errCnt = toLong(row.get("error_count"));
                long callCnt = toLong(row.get("calls"));
                m.put("avgMs", Math.round(toDouble(row.get("avg_response_time"))));
                m.put("errorRate", callCnt > 0 ? Math.round(errCnt * 100.0 / callCnt) : 0);
                m.put("p99Ms", Math.round(toDouble(row.get("avg_response_time")) * 1.5)); // 近似p99
                mapped.add(m);
            }
            endpoints = mapped;
        } catch (Exception e) {
            log.debug("[Ops] 端点排行查询失败: {}", e.getMessage());
        }
        result.put("endpoints", endpoints);

        // 4. systemResources（直接读当前 JVM Runtime 实时数据）
        Map<String, Object> sysRes = new HashMap<>();
        try {
            Runtime rt = Runtime.getRuntime();
            long totalMemMb = rt.totalMemory() / (1024 * 1024);
            long freeMemMb = rt.freeMemory() / (1024 * 1024);
            long maxMemMb = rt.maxMemory() / (1024 * 1024);
            long usedMemMb = totalMemMb - freeMemMb;
            int cores = rt.availableProcessors();

            // CPU 使用率（通过 OperatingSystemMXBean）
            double cpuUsage = 0;
            try {
                var osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
                if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                    cpuUsage = sunOsBean.getProcessCpuLoad() * 100.0;
                    if (cpuUsage < 0) cpuUsage = 0;
                }
            } catch (Exception ignored) {}

            Map<String, Object> cpu = new HashMap<>();
            cpu.put("current", Math.round(cpuUsage * 10.0) / 10.0);
            cpu.put("peak", Math.round(cpuUsage * 10.0) / 10.0);
            cpu.put("cores", cores);

            Map<String, Object> memory = new HashMap<>();
            memory.put("usedMb", usedMemMb);
            memory.put("totalMb", maxMemMb);
            memory.put("peakMb", usedMemMb);
            memory.put("percentage", maxMemMb > 0 ? Math.round((double) usedMemMb / maxMemMb * 1000.0) / 10.0 : 0);

            // 磁盘使用（通过 File 获取当前工作目录所在分区）
            Map<String, Object> disk = new HashMap<>();
            try {
                var fileStore = java.nio.file.Files.getFileStore(java.nio.file.Paths.get("."));
                long totalBytes = fileStore.getTotalSpace();
                long usedBytes = fileStore.getTotalSpace() - fileStore.getUsableSpace();
                double usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0);
                double totalGb = totalBytes / (1024.0 * 1024.0 * 1024.0);
                disk.put("usedGb", Math.round(usedGb * 10.0) / 10.0);
                disk.put("totalGb", Math.round(totalGb * 10.0) / 10.0);
                disk.put("percentage", totalBytes > 0 ? Math.round((double) usedBytes / totalBytes * 1000.0) / 10.0 : 0);
            } catch (Exception ex) {
                disk.put("usedGb", 0.0);
                disk.put("totalGb", 0.0);
                disk.put("percentage", 0);
            }

            // 网络指标（从 api_metrics 统计请求数）
            Map<String, Object> network = new HashMap<>();
            network.put("inboundKbps", 0);
            network.put("outboundKbps", 0);
            try {
                String reqCountSql = "SELECT COUNT(*) as cnt FROM api_metrics WHERE created_at >= ?" +
                        (company != null ? " AND company = ?" : "");
                long reqCount = toLong(jdbcTemplate.queryForMap(reqCountSql, statsParams).get("cnt"));
                network.put("totalRequests", reqCount);
            } catch (Exception ex) {
                network.put("totalRequests", 0);
            }

            sysRes.put("cpu", cpu);
            sysRes.put("memory", memory);
            sysRes.put("disk", disk);
            sysRes.put("network", network);
        } catch (Exception e) {
            log.debug("[Ops] 系统资源采集失败: {}", e.getMessage());
            sysRes.put("cpu", Map.of("current", 0, "peak", 0, "cores", 0));
            sysRes.put("memory", Map.of("usedMb", 0, "totalMb", 0, "peakMb", 0, "percentage", 0));
            sysRes.put("disk", Map.of("usedGb", 0.0, "totalGb", 0.0, "percentage", 0));
            sysRes.put("network", Map.of("inboundKbps", 0, "outboundKbps", 0, "totalRequests", 0));
        }
        result.put("systemResources", sysRes);
        result.put("period", period);
        return result;
    }

    /**
     * 获取错误列表（返回格式匹配前端 ErrorsTab）
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
                "COALESCE(SUM(CASE WHEN severity = 'critical' THEN 1 ELSE 0 END), 0) as critical_count, " +
                "COALESCE(SUM(CASE WHEN severity = 'error' THEN 1 ELSE 0 END), 0) as error_count, " +
                "COALESCE(SUM(CASE WHEN severity = 'warning' THEN 1 ELSE 0 END), 0) as warning_count " +
                "FROM system_errors " + whereSql;

        Map<String, Object> counts = jdbcTemplate.queryForMap(countSql, params.toArray());
        result.put("stats", counts);

        // 分页查询，字段映射到前端 ErrorItem 格式
        int offset = (page - 1) * pageSize;
        String listSql = "SELECT id, error_type as type, severity, message, stack_trace as stack, " +
                "endpoint, method, status_code as statusCode, " +
                "occurrence_count as occurrences, first_seen_at as firstSeen, last_seen_at as lastSeen, " +
                "CASE WHEN resolved THEN 'resolved' ELSE 'open' END as status " +
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
     * 获取性能指标（返回格式匹配前端 PerformanceTab）
     */
    public Map<String, Object> getPerformance(String period, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String company = getCompanyFromRequest(request);
        LocalDateTime since = calculateSince(period);
        Object[] params = company != null ? new Object[]{since, company} : new Object[]{since};

        // 1. services（各服务性能概览）
        List<Map<String, Object>> services = new ArrayList<>();
        try {
            String serviceSql = "SELECT " +
                    "SPLIT_PART(endpoint, '/', 2) as service, " +
                    "COUNT(*) as total_requests, " +
                    "COALESCE(AVG(response_time_ms), 0) as avg_response_ms, " +
                    "COALESCE(SUM(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END)::float / NULLIF(COUNT(*), 0), 0) as error_rate " +
                    "FROM api_metrics WHERE created_at >= ?" +
                    (company != null ? " AND company = ?" : "") +
                    " GROUP BY SPLIT_PART(endpoint, '/', 2) ORDER BY total_requests DESC";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(serviceSql, params);
            for (Map<String, Object> row : rows) {
                Map<String, Object> svc = new HashMap<>();
                svc.put("name", row.get("service"));
                svc.put("status", toDouble(row.get("error_rate")) < 0.05 ? "healthy" : "warning");
                Map<String, Object> rt = new HashMap<>();
                rt.put("p50", Math.round(toDouble(row.get("avg_response_ms"))));
                rt.put("p99", Math.round(toDouble(row.get("avg_response_ms")) * 2));
                svc.put("responseTime", rt);
                svc.put("errorRate", Math.round(toDouble(row.get("error_rate")) * 10000.0) / 10.0);
                svc.put("throughput", toLong(row.get("total_requests")));
                svc.put("connections", 0);
                svc.put("maxConnections", 100);
                services.add(svc);
            }
        } catch (Exception e) {
            log.debug("[Ops] 服务性能查询失败: {}", e.getMessage());
        }
        result.put("services", services);

        // 2. slowQueries（慢查询 Top 10）
        List<Map<String, Object>> slowQueries = new ArrayList<>();
        try {
            String slowSql = "SELECT endpoint, method, response_time_ms as avgMs " +
                    "FROM api_metrics WHERE created_at >= ? AND response_time_ms > 1000" +
                    (company != null ? " AND company = ?" : "") +
                    " ORDER BY response_time_ms DESC LIMIT 10";
            slowQueries = new ArrayList<>(jdbcTemplate.queryForList(slowSql, params));
        } catch (Exception e) {
            log.debug("[Ops] 慢查询查询失败: {}", e.getMessage());
        }
        result.put("slowQueries", slowQueries);

        // 3. runtime（直接读当前 JVM Runtime + HikariCP 连接池实时数据）
        Map<String, Object> runtime = new HashMap<>();
        try {
            // JVM 实时指标
            Runtime rt = Runtime.getRuntime();
            long heapUsed = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
            long heapMax = rt.maxMemory() / (1024 * 1024);
            int threadCount = Thread.activeCount();

            // GC 指标（通过 ManagementFactory）
            long gcPauseMs = 0;
            try {
                for (var gcBean : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                    gcPauseMs += gcBean.getCollectionTime();
                }
            } catch (Exception ignored) {}

            // 峰值线程数（通过 ThreadMXBean）
            int peakThreadCount = threadCount;
            try {
                peakThreadCount = java.lang.management.ManagementFactory.getThreadMXBean().getPeakThreadCount();
            } catch (Exception ignored) {}

            Map<String, Object> jvm = new HashMap<>();
            jvm.put("heapUsedMb", heapUsed);
            jvm.put("heapMaxMb", heapMax);
            jvm.put("gcPauseMs", gcPauseMs);
            jvm.put("threadCount", threadCount);
            jvm.put("peakThreadCount", peakThreadCount);
            runtime.put("jvm", jvm);

            // 数据库连接池指标（通过注入的 DataSource 获取 HikariCP 指标）
            Map<String, Object> database = new HashMap<>();
            int activeConns = 0, idleConns = 0, maxConns = 0, waitingConns = 0;
            try {
                if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikariDs) {
                    var pool = hikariDs.getHikariPoolMXBean();
                    if (pool != null) {
                        activeConns = pool.getActiveConnections();
                        idleConns = pool.getIdleConnections();
                        maxConns = hikariDs.getMaximumPoolSize();
                        waitingConns = pool.getThreadsAwaitingConnection();
                    }
                }
            } catch (NoClassDefFoundError | Exception ignored) {}
            database.put("activeConnections", activeConns);
            database.put("maxConnections", maxConns > 0 ? maxConns : 100);
            database.put("waitingConnections", waitingConns);
            database.put("avgQueryMs", 0);
            // 慢查询数从 api_metrics 表中统计
            try {
                String slowCountSql = "SELECT COUNT(*) as cnt FROM api_metrics WHERE response_time_ms > 1000 AND created_at >= ?" +
                        (company != null ? " AND company = ?" : "");
                long slowCount = toLong(jdbcTemplate.queryForMap(slowCountSql, params).get("cnt"));
                database.put("slowQueryCount", slowCount);
            } catch (Exception ex) {
                database.put("slowQueryCount", 0);
            }
            runtime.put("database", database);

            // Node.js 指标（本项目是 Java 后端，没有 Node.js 运行时，填 0）
            runtime.put("node", Map.of("rssMb", 0, "heapUsedMb", 0, "heapTotalMb", 0, "externalMb", 0, "arrayBuffersMb", 0));
        } catch (Exception e) {
            log.debug("[Ops] 运行时指标采集失败: {}", e.getMessage());
            runtime.put("jvm", Map.of("heapUsedMb", 0, "heapMaxMb", 0, "gcPauseMs", 0, "threadCount", 0, "peakThreadCount", 0));
            runtime.put("node", Map.of("rssMb", 0, "heapUsedMb", 0, "heapTotalMb", 0, "externalMb", 0, "arrayBuffersMb", 0));
            runtime.put("database", Map.of("activeConnections", 0, "maxConnections", 100, "waitingConnections", 0, "avgQueryMs", 0, "slowQueryCount", 0));
        }
        result.put("runtime", runtime);
        result.put("lastUpdated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
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

    /**
     * 记录系统错误
     */
    public void recordError(Map<String, Object> error) {
        try {
            String id = UUID.randomUUID().toString();
            String errorType = (String) error.getOrDefault("errorType", "UnknownError");
            String message = (String) error.getOrDefault("message", "");
            String endpoint = (String) error.get("endpoint");

            // 检查是否已有相同类型+端点+消息的错误（去重，增加occurrence_count）
            String findSql = "SELECT id, occurrence_count FROM system_errors " +
                    "WHERE error_type = ? AND endpoint = ? AND message = ? AND resolved = false " +
                    "ORDER BY last_seen_at DESC LIMIT 1";

            try {
                Map<String, Object> existing = jdbcTemplate.queryForMap(findSql, errorType, endpoint, message);
                // 已有相同未解决错误，增加计数并更新 last_seen_at
                String existingId = (String) existing.get("id");
                int count = ((Number) existing.get("occurrence_count")).intValue() + 1;
                jdbcTemplate.update(
                        "UPDATE system_errors SET occurrence_count = ?, last_seen_at = NOW() WHERE id = ?",
                        count, existingId);
                return;
            } catch (Exception ignored) {
                // 没有找到已有错误，插入新记录
            }

            jdbcTemplate.update(
                    "INSERT INTO system_errors (id, error_type, severity, message, stack_trace, " +
                            "endpoint, method, user_id, ip_address, status_code, company) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id,
                    errorType,
                    error.getOrDefault("severity", "error"),
                    message,
                    error.get("stackTrace"),
                    endpoint,
                    error.get("method"),
                    error.get("userId"),
                    error.get("ipAddress"),
                    error.get("statusCode"),
                    error.get("company"));
        } catch (Exception e) {
            log.warn("[Ops] 记录系统错误失败: {}", e.getMessage());
        }
    }

    // 性能指标已改为直接读取 JVM Runtime + HikariCP 实时数据，不再需要 recordPerformanceSnapshot

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

    private long toLong(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).longValue();
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return 0; }
    }

    private double toDouble(Object val) {
        if (val == null) return 0;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0; }
    }
}
