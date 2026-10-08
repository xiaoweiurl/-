#!/usr/bin/env bash
# 回填图片向量。本进程会打开 image-search.enabled，不改变 IDEA 里正在跑的服务。
# 默认只写整图集合。裁剪集合：--variant crop。两个都写：--variant all。
set -euo pipefail
cd "$(dirname "$0")/../backend"
./mvnw -q -DskipTests compile
./mvnw -q dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/cp.txt
CP="target/classes:$(tr -d '\r\n' < target/cp.txt)"
exec java -Dfile.encoding=UTF-8 -Dimage-search.enabled=true -cp "$CP" \
  com.imagemanager.imagesearch.ImageSearchBackfill "$@"
