-- V61: 打样员工作通知投递记录（用于表单保存后补发「已填写」通知，无法原地改 ActionCard 正文）
-- 钉钉 asyncsend_v2 的 ActionCard 发出后不能改 markdown/字段；本表记下 task_id 与当时展示的货号/品名，
-- 打样表单补全后再向同一 userid 发一封摘要卡，并按 task_id 去重。

CREATE TABLE IF NOT EXISTS goods_sampler_notice (
    goods_id            BIGINT PRIMARY KEY REFERENCES goods_library(id) ON DELETE CASCADE,
    ding_userid         VARCHAR(64) NOT NULL,
    sampler_name        VARCHAR(100),
    task_id             BIGINT NOT NULL,
    sent_folder_name    VARCHAR(300),
    sent_goods_no       VARCHAR(100),
    sent_product_name   VARCHAR(200),
    sent_initiator      VARCHAR(100),
    sent_at             TIMESTAMP NOT NULL DEFAULT NOW(),
    followup_task_id    BIGINT,
    followup_sent_at    TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_goods_sampler_notice_userid
    ON goods_sampler_notice (ding_userid);

COMMENT ON TABLE goods_sampler_notice IS '商品库打样工作通知：最近一次指派卡片及表单回填补发';
COMMENT ON COLUMN goods_sampler_notice.task_id IS '钉钉 asyncsend_v2 返回的指派通知 task_id';
COMMENT ON COLUMN goods_sampler_notice.followup_task_id IS '表单补全后补发摘要卡的 task_id；空表示尚未补发';
