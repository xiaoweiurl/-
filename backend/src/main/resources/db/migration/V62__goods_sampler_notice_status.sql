-- V62: 打样工作通知投递结果落库（失败/跳过也可见），支持商品页展示与手动补发。
-- ding_userid / task_id 在未发出时允许空串 / 0。

ALTER TABLE goods_sampler_notice
    ALTER COLUMN ding_userid SET DEFAULT '',
    ALTER COLUMN task_id SET DEFAULT 0;

ALTER TABLE goods_sampler_notice
    ADD COLUMN IF NOT EXISTS last_status VARCHAR(40) NOT NULL DEFAULT 'SENT',
    ADD COLUMN IF NOT EXISTS last_message VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS last_kind VARCHAR(20) NOT NULL DEFAULT 'ASSIGNMENT',
    ADD COLUMN IF NOT EXISTS persist_ok BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN goods_sampler_notice.last_status IS '最近一次投递结果：SENT/FAILED/PENDING/SKIPPED_*';
COMMENT ON COLUMN goods_sampler_notice.last_message IS '最近一次投递说明或失败原因';
COMMENT ON COLUMN goods_sampler_notice.last_kind IS 'ASSIGNMENT / FOLLOWUP';
COMMENT ON COLUMN goods_sampler_notice.persist_ok IS 'false 表示钉钉已受理但本地记下 task_id 失败';
