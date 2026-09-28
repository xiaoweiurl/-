#!/usr/bin/env bash
# 只读评测：不写 Milvus，不写 image_vector_index。
set -euo pipefail
cd "$(dirname "$0")/../backend"
./mvnw -q -DskipTests compile
./mvnw -q dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile=target/cp.txt
CP="target/classes:$(tr -d '\r\n' < target/cp.txt)"
exec java -Dfile.encoding=UTF-8 -Dimage-search.enabled=true -cp "$CP" \
  com.imagemanager.imagesearch.ImageSearchEval "$@"
