package com.imagemanager.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;

/**
 * 数据模型表自动初始化
 * 在应用启动时检查并创建 data_models / data_model_fields / data_model_records 表
 * （这些表用 JdbcTemplate 访问，没有 JPA @Entity，Hibernate ddl-auto 不会自动创建）
 */
@Configuration
public class DataModelTableInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataModelTableInitializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @PostConstruct
    public void init() {
        try {
            // 检查表是否已存在
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'data_models'",
                Integer.class
            );
            if (count != null && count > 0) {
                return;
            }

            log.info("初始化数据模型表...");

            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_models (
                    id VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    description TEXT,
                    icon VARCHAR(50),
                    version INT DEFAULT 1,
                    is_system BOOLEAN DEFAULT FALSE,
                    settings JSONB DEFAULT '{}',
                    user_id VARCHAR(36),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_model_fields (
                    id VARCHAR(36) PRIMARY KEY,
                    model_id VARCHAR(36) NOT NULL REFERENCES data_models(id) ON DELETE CASCADE,
                    name VARCHAR(100) NOT NULL,
                    field_key VARCHAR(100) NOT NULL,
                    field_type VARCHAR(30) NOT NULL DEFAULT 'text',
                    required BOOLEAN DEFAULT FALSE,
                    unique_field BOOLEAN DEFAULT FALSE,
                    default_value TEXT,
                    options JSONB,
                    validation JSONB,
                    description TEXT,
                    sort_order INT DEFAULT 0,
                    group_name VARCHAR(100),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            jdbc.execute("""
                CREATE TABLE IF NOT EXISTS data_model_records (
                    id VARCHAR(36) PRIMARY KEY,
                    model_id VARCHAR(36) NOT NULL REFERENCES data_models(id) ON DELETE CASCADE,
                    data JSONB NOT NULL DEFAULT '{}',
                    user_id VARCHAR(36),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            // 插入示例数据
            jdbc.execute("""
                INSERT INTO data_models (id, name, description, icon, is_system, user_id)
                VALUES
                    ('dm-product', '产品信息', '产品基础信息模型，包含名称、编码、分类、规格等', 'Package', TRUE, 'system'),
                    ('dm-supplier', '供应商信息', '供应商资质与联系信息', 'Building', TRUE, 'system'),
                    ('dm-order', '订单信息', '采购/销售订单数据', 'FileText', TRUE, 'system'),
                    ('dm-material', '原料信息', '原料规格与采购信息', 'Layers', TRUE, 'system')
                ON CONFLICT (id) DO NOTHING
            """);

            jdbc.execute("""
                INSERT INTO data_model_fields (id, model_id, name, field_key, field_type, required, sort_order, group_name)
                VALUES
                    ('f-p1', 'dm-product', '产品名称', 'name', 'text', TRUE, 1, '基础信息'),
                    ('f-p2', 'dm-product', '产品编码', 'code', 'text', TRUE, 2, '基础信息'),
                    ('f-p3', 'dm-product', '分类', 'category', 'select', FALSE, 3, '基础信息'),
                    ('f-p4', 'dm-product', '规格', 'spec', 'text', FALSE, 4, '基础信息'),
                    ('f-p5', 'dm-product', '单价', 'price', 'number', FALSE, 5, '价格信息'),
                    ('f-p6', 'dm-product', '成本价', 'costPrice', 'number', FALSE, 6, '价格信息'),
                    ('f-s1', 'dm-supplier', '供应商名称', 'name', 'text', TRUE, 1, '基础信息'),
                    ('f-s2', 'dm-supplier', '联系人', 'contact', 'text', FALSE, 2, '基础信息'),
                    ('f-s3', 'dm-supplier', '联系电话', 'phone', 'text', FALSE, 3, '基础信息'),
                    ('f-s4', 'dm-supplier', '地址', 'address', 'text', FALSE, 4, '基础信息'),
                    ('f-o1', 'dm-order', '订单编号', 'orderNo', 'text', TRUE, 1, '订单信息'),
                    ('f-o2', 'dm-order', '客户名称', 'customer', 'text', TRUE, 2, '订单信息'),
                    ('f-o3', 'dm-order', '金额', 'amount', 'number', TRUE, 3, '订单信息'),
                    ('f-o4', 'dm-order', '日期', 'date', 'date', FALSE, 4, '订单信息'),
                    ('f-m1', 'dm-material', '原料名称', 'name', 'text', TRUE, 1, '基础信息'),
                    ('f-m2', 'dm-material', '原料编码', 'code', 'text', TRUE, 2, '基础信息'),
                    ('f-m3', 'dm-material', '单位', 'unit', 'text', FALSE, 3, '基础信息'),
                    ('f-m4', 'dm-material', '采购价', 'price', 'number', FALSE, 4, '价格信息')
                ON CONFLICT (id) DO NOTHING
            """);

            log.info("数据模型表初始化完成");
        } catch (Exception e) {
            log.warn("数据模型表初始化失败（表可能已存在）: {}", e.getMessage());
        }
    }
}
