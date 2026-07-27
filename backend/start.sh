#!/bin/bash

# 从 PGDATABASE_URL 解析 JDBC URL
PG_URL="${PGDATABASE_URL}"

# 解析 URL: postgresql://user:password@host:port/dbname?sslmode=require&channel_binding=require
if [[ "$PG_URL" =~ ^postgresql://([^:]+):([^@]+)@([^:]+):([0-9]+)/([^?]+) ]]; then
    PG_USER="${BASH_REMATCH[1]}"
    PG_PASSWORD="${BASH_REMATCH[2]}"
    PG_HOST="${BASH_REMATCH[3]}"
    PG_PORT="${BASH_REMATCH[4]}"
    PG_DBNAME="${BASH_REMATCH[5]}"

    # 构建 JDBC URL
    JDBC_URL="jdbc:postgresql://${PG_HOST}:${PG_PORT}/${PG_DBNAME}?sslmode=require"

    export DATABASE_URL_JDBC="$JDBC_URL"
    export DATABASE_USERNAME="$PG_USER"
    export DATABASE_PASSWORD="$PG_PASSWORD"

    echo "数据库连接配置:"
    echo "  JDBC_URL: $JDBC_URL"
    echo "  USERNAME: $PG_USER"
    echo "  DBNAME: $PG_DBNAME"
fi

# S3 兼容存储配置（阿里云 OSS）- 密钥通过环境变量注入，不硬编码
export STORAGE_TYPE=${STORAGE_TYPE:-local}
export S3_ENDPOINT=${S3_ENDPOINT:-}
export S3_REGION=${S3_REGION:-}
export S3_BUCKET_NAME=${S3_BUCKET_NAME:-}
export S3_ACCESS_KEY=${S3_ACCESS_KEY:-}
export S3_SECRET_KEY=${S3_SECRET_KEY:-}
export S3_PRESIGN_EXPIRY=${S3_PRESIGN_EXPIRY:-604800}

if [ -n "$S3_ACCESS_KEY" ] && [ -n "$S3_SECRET_KEY" ]; then
    echo "OSS存储配置:"
    echo "  ENDPOINT: $S3_ENDPOINT"
    echo "  BUCKET: $S3_BUCKET_NAME"
    echo "  REGION: $S3_REGION"
else
    echo "OSS存储配置: 未设置 S3_ACCESS_KEY/S3_SECRET_KEY，使用本地存储"
fi

# 启动 Spring Boot 应用
cd /workspace/projects/backend
./mvnw spring-boot:run
