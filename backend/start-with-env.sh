#!/bin/bash

# 将 postgresql:// 转换为 jdbc:postgresql://
export DATABASE_URL_JDBC=$(echo $PGDATABASE_URL | sed 's|^postgresql://|jdbc:postgresql://|')

# S3 兼容存储配置（阿里云 OSS）- 密钥通过环境变量注入，不硬编码
export STORAGE_TYPE=${STORAGE_TYPE:-local}
export S3_ENDPOINT=${S3_ENDPOINT:-}
export S3_REGION=${S3_REGION:-}
export S3_BUCKET_NAME=${S3_BUCKET_NAME:-}
export S3_ACCESS_KEY=${S3_ACCESS_KEY:-}
export S3_SECRET_KEY=${S3_SECRET_KEY:-}
export S3_PRESIGN_EXPIRY=${S3_PRESIGN_EXPIRY:-604800}

# 启动 Java 应用
java -jar target/image-manager-backend-1.0.0.jar
