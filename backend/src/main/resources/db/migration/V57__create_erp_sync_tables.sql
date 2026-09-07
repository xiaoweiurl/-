-- V57: ERP 数据同步（状态游标 + 同步日志）
-- 增量同步：以 erp_sync_state.last_sync_time（数据库最新同步时间）为起点、当前时间为终点过滤数据

-- 各模块同步状态（游标）
CREATE TABLE IF NOT EXISTS erp_sync_state (
  module_key varchar(50) NOT NULL,
  module_name varchar(100),
  last_sync_time timestamp,
  total_records bigint DEFAULT 0,
  last_added int4 DEFAULT 0,
  last_status varchar(20) DEFAULT 'never',
  last_message text,
  last_duration_ms bigint DEFAULT 0,
  updated_at timestamp DEFAULT now(),
  CONSTRAINT erp_sync_state_pkey PRIMARY KEY (module_key)
);
COMMENT ON TABLE erp_sync_state IS 'ERP 各模块同步状态游标（增量同步起点）';
COMMENT ON COLUMN erp_sync_state.module_key IS '模块标识：orders/neiyi-gongyidan/siwa-gongyidan/gongyi-bujian/gongyi-gongxu/gongxu-gongjia/yuanliao-bom';
COMMENT ON COLUMN erp_sync_state.last_sync_time IS '最近一次成功同步截止时间（下次增量起点）';
COMMENT ON COLUMN erp_sync_state.total_records IS '累计同步记录数';
COMMENT ON COLUMN erp_sync_state.last_status IS 'success/failed/never';

-- 同步日志
CREATE TABLE IF NOT EXISTS erp_sync_log (
  id bigserial NOT NULL,
  module_key varchar(50),
  module_name varchar(100),
  sync_type varchar(20),
  range_start timestamp,
  range_end timestamp,
  added int4 DEFAULT 0,
  failed int4 DEFAULT 0,
  status varchar(20),
  duration_ms bigint DEFAULT 0,
  message text,
  source varchar(10) DEFAULT 'demo',
  created_at timestamp DEFAULT now(),
  CONSTRAINT erp_sync_log_pkey PRIMARY KEY (id)
);
COMMENT ON TABLE erp_sync_log IS 'ERP 数据同步日志';
COMMENT ON COLUMN erp_sync_log.sync_type IS 'incremental 增量 / full 首次全量';
COMMENT ON COLUMN erp_sync_log.range_start IS '数据范围起点（数据库最新时间）';
COMMENT ON COLUMN erp_sync_log.range_end IS '数据范围终点（当前时间）';
COMMENT ON COLUMN erp_sync_log.source IS 'erp 真实数据 / demo 演示数据';

CREATE INDEX IF NOT EXISTS idx_erp_sync_log_created ON erp_sync_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_erp_sync_log_module ON erp_sync_log(module_key, created_at DESC);
