-- =====================================================
-- AI 能力调用日志表
-- 价值：用量监控页真实数据源——记录系统 AI 能力的每一次实际调用
--   （对话/联网搜索/向量Embedding/图片识别等），替代前端硬编码模拟数据
-- 幂等：CREATE TABLE IF NOT EXISTS
-- =====================================================

CREATE TABLE IF NOT EXISTS ai_call_log (
  id          bigserial PRIMARY KEY,
  capability  varchar(50)  NOT NULL,
  model       varchar(100) NOT NULL,
  status      varchar(20)  NOT NULL,
  latency_ms  int          NOT NULL DEFAULT 0,
  tokens      int,
  detail      varchar(300),
  created_at  timestamp    NOT NULL DEFAULT now()
);

COMMENT ON TABLE  ai_call_log IS 'AI能力调用日志：用量监控数据源（真实调用记录）';
COMMENT ON COLUMN ai_call_log.capability IS '能力标识：smart-chat/factory-chat/web-search/embedding/ai-recognize/ai-image/quotation';
COMMENT ON COLUMN ai_call_log.model      IS '实际调用的模型名（取自系统配置，如 qwen3.6/bge-m3/MiniMax-M3）';
COMMENT ON COLUMN ai_call_log.status     IS 'success / fail';
COMMENT ON COLUMN ai_call_log.latency_ms IS '调用耗时（毫秒）';
COMMENT ON COLUMN ai_call_log.tokens     IS 'Token 消耗（LLM 返回的真实值，无则为空）';
COMMENT ON COLUMN ai_call_log.detail     IS '简述（检索词/会话等，截断300字符）';

CREATE INDEX IF NOT EXISTS idx_ai_call_log_created      ON ai_call_log (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_call_log_cap_created  ON ai_call_log (capability, created_at DESC);
