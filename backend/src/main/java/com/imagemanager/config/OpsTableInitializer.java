package com.imagemanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;

/**
 * 运维监控表自动初始化
 * 在应用启动时检查并创建 api_metrics / system_errors 表
 * （这些表用 JdbcTemplate 访问，没有 JPA @Entity，Hibernate ddl-auto 不会自动创建）
 */
@Configuration
public class OpsTableInitializer {

    private static final Logger log = LoggerFactory.getLogger(OpsTableInitializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @PostConstruct
    public void init() {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'api_metrics'",
                Integer.class
            );
            if (count != null && count > 0) {
                log.info("api_metrics 表已存在，跳过初始化");
                return;
            }

            log.info("初始化运维监控表...");

            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS api_metrics (
                    id SERIAL PRIMARY KEY,
                    endpoint VARCHAR(255) NOT NULL,
                    method VARCHAR(10) NOT NULL,
                    status_code INT NOT NULL,
                    response_time_ms INT NOT NULL,
                    user_id VARCHAR(36),
                    company_id VARCHAR(36),
                    ip_address VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS system_errors (
                    id SERIAL PRIMARY KEY,
                    error_type VARCHAR(100) NOT NULL,
                    error_message TEXT,
                    stack_trace TEXT,
                    endpoint VARCHAR(255),
                    method VARCHAR(10),
                    status_code INT,
                    user_id VARCHAR(36),
                    severity VARCHAR(20) DEFAULT 'error',
                    occurrences INT DEFAULT 1,
                    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    status VARCHAR(20) DEFAULT 'open',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_api_metrics_endpoint ON api_metrics(endpoint)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_api_metrics_created ON api_metrics(created_at)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_system_errors_type ON system_errors(error_type)");
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_system_errors_status ON system_errors(status)");

            log.info("运维监控表初始化完成");
        } catch (Exception e) {
            log.warn("运维监控表初始化失败（表可能已存在）: {}", e.getMessage());
        }
    }
}
