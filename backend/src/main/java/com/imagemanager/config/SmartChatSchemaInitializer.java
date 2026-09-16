package com.imagemanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;

import java.util.Objects;

/**
 * 智能对话表 Schema 自动修复
 * 
 * 在应用启动时检查并补齐 smart_chat_conversations / smart_chat_history 表的缺失列。
 * 这些列由 V30/V33/V34 迁移脚本添加，但用户数据库可能未执行过这些迁移。
 * 
 * 使用 ALTER TABLE ... ADD COLUMN IF NOT EXISTS 保证幂等，可安全重复执行。
 */
@Configuration
public class SmartChatSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SmartChatSchemaInitializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PostConstruct
    public void init() {
        TransactionTemplate tx = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        tx.executeWithoutResult(status -> {
            try {
                // 1. 确保 smart_chat_conversations 表存在
                jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS smart_chat_conversations (
                        id UUID PRIMARY KEY,
                        user_id VARCHAR(36) NOT NULL,
                        title VARCHAR(255),
                        company VARCHAR(50),
                        created_at TIMESTAMP DEFAULT NOW(),
                        updated_at TIMESTAMP DEFAULT NOW()
                    )
                """);
                log.info("[Schema] smart_chat_conversations 表已就绪");

                // 2. 确保 smart_chat_history 表存在
                jdbc.execute("""
                    CREATE TABLE IF NOT EXISTS smart_chat_history (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id VARCHAR(36) NOT NULL,
                        session_id UUID,
                        role VARCHAR(20) NOT NULL,
                        content TEXT,
                        company VARCHAR(50),
                        created_at TIMESTAMP DEFAULT NOW()
                    )
                """);
                log.info("[Schema] smart_chat_history 表已就绪");

                // 3. 补齐 smart_chat_conversations 缺失的列
                addColumnIfNotExists("smart_chat_conversations", "model", "VARCHAR(20) DEFAULT 'designer'");
                addColumnIfNotExists("smart_chat_conversations", "updated_at", "TIMESTAMP DEFAULT NOW()");

                // 4. 补齐 smart_chat_history 缺失的列
                addColumnIfNotExists("smart_chat_history", "conversation_id", "UUID");
                addColumnIfNotExists("smart_chat_history", "reasoning_content", "TEXT");
                addColumnIfNotExists("smart_chat_history", "model", "VARCHAR(20) DEFAULT 'designer'");

                // 5. 补齐索引
                createIndexIfNotExists("idx_conv_user_company", "smart_chat_conversations(user_id, company)");
                createIndexIfNotExists("idx_conv_updated", "smart_chat_conversations(updated_at DESC)");
                createIndexIfNotExists("idx_conv_model", "smart_chat_conversations(model)");
                createIndexIfNotExists("idx_smart_chat_session", "smart_chat_history(session_id)");
                createIndexIfNotExists("idx_smart_chat_user", "smart_chat_history(user_id)");
                createIndexIfNotExists("idx_smart_chat_conversation", "smart_chat_history(conversation_id)");
                createIndexIfNotExists("idx_smart_chat_company", "smart_chat_history(company)");
                createIndexIfNotExists("idx_chat_history_model", "smart_chat_history(model)");

                log.info("[Schema] 智能对话表 Schema 修复完成");
            } catch (Exception e) {
                log.error("[Schema] 智能对话表 Schema 修复失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 安全添加列（IF NOT EXISTS 保证幂等）
     */
    private void addColumnIfNotExists(String table, String column, String definition) {
        try {
            // 先检查列是否已存在
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                Integer.class, table, column
            );
            if (count != null && count > 0) {
                return; // 列已存在，跳过
            }
            String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", table, column, definition);
            jdbc.execute(sql);
            log.info("[Schema] 已添加列: {}.{}", table, column);
        } catch (Exception e) {
            log.warn("[Schema] 添加列 {}.{} 失败（可能已存在）: {}", table, column, e.getMessage());
        }
    }

    /**
     * 安全创建索引（IF NOT EXISTS 保证幂等）
     */
    private void createIndexIfNotExists(String indexName, String definition) {
        try {
            jdbc.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + definition);
        } catch (Exception e) {
            log.warn("[Schema] 创建索引 {} 失败: {}", indexName, e.getMessage());
        }
    }
}
