-- 知识批量导入任务表（Milvus 直写，不存 pgvector）
CREATE TABLE IF NOT EXISTS knowledge_import_task (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(1024) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    total_files INT NOT NULL DEFAULT 0,
    processed_files INT NOT NULL DEFAULT 0,
    failed_files INT NOT NULL DEFAULT 0,
    total_chunks BIGINT NOT NULL DEFAULT 0,
    error_msg TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ
);

-- 导入失败明细表
CREATE TABLE IF NOT EXISTS knowledge_import_error (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    file_name VARCHAR(2048),
    error_msg TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kimport_error_task ON knowledge_import_error(task_id);
