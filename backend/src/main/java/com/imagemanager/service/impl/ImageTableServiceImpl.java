package com.imagemanager.service.impl;

import com.imagemanager.service.ImageTableService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态表管理服务实现
 * 使用 EntityManager 执行动态 DDL 操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageTableServiceImpl implements ImageTableService {

    private final EntityManager entityManager;

    // 表名前缀
    private static final String TABLE_PREFIX = "images_";

    @Override
    @Transactional
    public boolean createUserImageTable(String userId) {
        if (userId == null || userId.isEmpty()) {
            log.error("用户ID为空，无法创建表");
            return false;
        }

        String tableName = getUserTableName(userId);
        
        // 检查表是否已存在
        if (userImageTableExists(userId)) {
            log.info("用户图片表已存在: {}", tableName);
            return true;
        }

        try {
            // 创建表的 DDL（复制 images 表结构）
            String createTableSQL = String.format("""
                CREATE TABLE %s (
                    id VARCHAR(36) PRIMARY KEY,
                    url VARCHAR(500) NOT NULL,
                    name VARCHAR(255) NOT NULL,
                    original_name VARCHAR(255),
                    size BIGINT,
                    width INTEGER,
                    height INTEGER,
                    file_type VARCHAR(20),
                    album_id VARCHAR(36),
                    product_id VARCHAR(36),
                    is_main_image BOOLEAN DEFAULT FALSE,
                    favorite BOOLEAN DEFAULT FALSE,
                    view_count INTEGER DEFAULT 0,
                    download_count INTEGER DEFAULT 0,
                    tags JSONB,
                    deleted BOOLEAN DEFAULT FALSE,
                    deleted_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    user_id VARCHAR(36)
                )
                """, tableName);
            
            entityManager.createNativeQuery(createTableSQL).executeUpdate();
            
            // 创建索引
            String createIndexSQL1 = String.format("CREATE INDEX idx_%s_album_id ON %s(album_id)", tableName.replace("_", "_"), tableName);
            String createIndexSQL2 = String.format("CREATE INDEX idx_%s_product_id ON %s(product_id)", tableName.replace("_", "_"), tableName);
            String createIndexSQL3 = String.format("CREATE INDEX idx_%s_deleted ON %s(deleted)", tableName.replace("_", "_"), tableName);
            String createIndexSQL4 = String.format("CREATE INDEX idx_%s_is_main_image ON %s(is_main_image)", tableName.replace("_", "_"), tableName);
            String createIndexSQL5 = String.format("CREATE INDEX idx_%s_favorite ON %s(favorite)", tableName.replace("_", "_"), tableName);
            String createIndexSQL6 = String.format("CREATE INDEX idx_%s_created_at ON %s(created_at)", tableName.replace("_", "_"), tableName);
            
            entityManager.createNativeQuery(createIndexSQL1).executeUpdate();
            entityManager.createNativeQuery(createIndexSQL2).executeUpdate();
            entityManager.createNativeQuery(createIndexSQL3).executeUpdate();
            entityManager.createNativeQuery(createIndexSQL4).executeUpdate();
            entityManager.createNativeQuery(createIndexSQL5).executeUpdate();
            entityManager.createNativeQuery(createIndexSQL6).executeUpdate();
            
            log.info("用户图片表创建成功: {}", tableName);
            return true;
        } catch (Exception e) {
            log.error("创建用户图片表失败: {}", tableName, e);
            return false;
        }
    }

    @Override
    public boolean userImageTableExists(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        
        String tableName = getUserTableName(userId);
        
        try {
            // PostgreSQL 查询表是否存在
            String checkSQL = """
                SELECT EXISTS (
                    SELECT FROM information_schema.tables 
                    WHERE table_schema = 'public' 
                    AND table_name = ?
                )
                """;
            
            Query query = entityManager.createNativeQuery(checkSQL);
            query.setParameter(1, tableName);
            
            Boolean exists = (Boolean) query.getSingleResult();
            return exists != null && exists;
        } catch (Exception e) {
            log.error("检查表是否存在失败: {}", tableName, e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean deleteUserImageTable(String userId) {
        if (userId == null || userId.isEmpty()) {
            log.error("用户ID为空，无法删除表");
            return false;
        }

        String tableName = getUserTableName(userId);
        
        if (!userImageTableExists(userId)) {
            log.info("用户图片表不存在: {}", tableName);
            return true;
        }

        try {
            String dropSQL = String.format("DROP TABLE IF EXISTS %s CASCADE", tableName);
            entityManager.createNativeQuery(dropSQL).executeUpdate();
            
            log.info("用户图片表删除成功: {}", tableName);
            return true;
        } catch (Exception e) {
            log.error("删除用户图片表失败: {}", tableName, e);
            return false;
        }
    }

    @Override
    public List<String> getAllUserImageTableNames() {
        try {
            // PostgreSQL 查询所有以 images_ 开头的表
            String querySQL = """
                SELECT table_name 
                FROM information_schema.tables 
                WHERE table_schema = 'public' 
                AND table_name LIKE 'images_%'
                AND table_name != 'images'
                """;
            
            List<?> results = entityManager.createNativeQuery(querySQL).getResultList();
            List<String> tableNames = new ArrayList<>();
            for (Object obj : results) {
                tableNames.add((String) obj);
            }
            return tableNames;
        } catch (Exception e) {
            log.error("查询所有用户图片表失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public String getUserTableName(String userId) {
        // 将 userId 中的特殊字符替换为下划线（如 "user-1" -> "images_user_1"）
        String sanitizedUserId = userId.replaceAll("[^a-zA-Z0-9]", "_");
        return TABLE_PREFIX + sanitizedUserId;
    }
}