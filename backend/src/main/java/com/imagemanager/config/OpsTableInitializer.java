package com.imagemanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;

/**
 * 运维监控表自动初始化
 * 在应用启动时检查并创建 api_metrics / system_errors / backup_records 表
 * （这些表用 JdbcTemplate 访问，没有 JPA @Entity，Hibernate ddl-auto 不会自动创建）
 * 
 * 建表语句与 V35__create_ops_tables.sql 迁移脚本保持一致
 */
@Configuration
public class OpsTableInitializer {

    private static final Logger log = LoggerFactory.getLogger(OpsTableInitializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PostConstruct
    public void init() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            try {
                // api_metrics 表 — 与 V35 迁移脚本一致
                jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS api_metrics (
                        id VARCHAR(36) PRIMARY KEY,
                        endpoint VARCHAR(255) NOT NULL,
                        method VARCHAR(10) NOT NULL,
                        status_code INT NOT NULL,
                        response_time_ms BIGINT NOT NULL,
                        user_id VARCHAR(36),
                        ip_address VARCHAR(50),
                        user_agent TEXT,
                        request_size BIGINT DEFAULT 0,
                        response_size BIGINT DEFAULT 0,
                        error_message TEXT,
                        company VARCHAR(100),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // system_errors 表 — 与 V35 迁移脚本一致
                jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS system_errors (
                        id VARCHAR(36) PRIMARY KEY,
                        error_type VARCHAR(50) NOT NULL,
                        severity VARCHAR(20) NOT NULL DEFAULT 'error',
                        message TEXT NOT NULL,
                        stack_trace TEXT,
                        endpoint VARCHAR(255),
                        method VARCHAR(10),
                        user_id VARCHAR(36),
                        ip_address VARCHAR(50),
                        request_body TEXT,
                        status_code INT,
                        resolved BOOLEAN DEFAULT FALSE,
                        resolved_by VARCHAR(36),
                        resolved_at TIMESTAMP,
                        occurrence_count INT DEFAULT 1,
                        first_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        last_seen_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        company VARCHAR(100),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // backup_records 表 — 与 V35 迁移脚本一致
                jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS backup_records (
                        id VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        type VARCHAR(50) NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'pending',
                        description TEXT,
                        size_bytes BIGINT DEFAULT 0,
                        error_message TEXT,
                        created_by VARCHAR(36),
                        company VARCHAR(100),
                        started_at TIMESTAMP,
                        completed_at TIMESTAMP,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // 索引
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_api_metrics_endpoint ON api_metrics(endpoint, method)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_api_metrics_created_at ON api_metrics(created_at)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_api_metrics_status ON api_metrics(status_code)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_api_metrics_company ON api_metrics(company)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_system_errors_type ON system_errors(error_type)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_system_errors_severity ON system_errors(severity)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_system_errors_resolved ON system_errors(resolved)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_system_errors_last_seen ON system_errors(last_seen_at)");
                jdbc.execute("CREATE INDEX IF NOT EXISTS idx_system_errors_company ON system_errors(company)");

                log.info("运维监控表初始化完成");
            } catch (Exception e) {
                log.warn("运维监控表初始化异常（可能表已存在）: {}", e.getMessage());
            }
        });
    }
}
