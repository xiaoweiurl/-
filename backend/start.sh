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

# 启动 Spring Boot 应用
cd /workspace/projects/backend
mvn spring-boot:run
