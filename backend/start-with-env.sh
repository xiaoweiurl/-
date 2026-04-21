#!/bin/bash

# 将 postgresql:// 转换为 jdbc:postgresql://
export DATABASE_URL_JDBC=$(echo $PGDATABASE_URL | sed 's|^postgresql://|jdbc:postgresql://|')

# 启动 Java 应用
java -jar target/image-manager-backend-1.0.0.jar
