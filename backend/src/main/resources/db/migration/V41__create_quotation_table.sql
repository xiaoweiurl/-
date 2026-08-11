-- =====================================================
-- 报价单表 (quotations)
-- 存储产品报价相关的成本、利润、工价等字段
-- =====================================================
CREATE TABLE IF NOT EXISTS quotations (
    id              BIGSERIAL PRIMARY KEY,

    -- 基础信息（字符串字段非空，与实体规范一致；DEFAULT '' 防止插入缺省失败）
    dh              VARCHAR(64)  NOT NULL,              -- 报价单号
    zhdate          TIMESTAMP,                          -- 制单日期（可空）
    khname          VARCHAR(255) NOT NULL DEFAULT '',   -- 客户名称
    huohao          VARCHAR(128) NOT NULL DEFAULT '',   -- 生产货号
    houhaocp        VARCHAR(128) NOT NULL DEFAULT '',   -- 成品货号
    remark          TEXT,                               -- 备注（可空）
    chima           VARCHAR(64)  NOT NULL DEFAULT '',   -- 尺码

    -- 成本与税金
    zpl             NUMERIC(18,4),                      -- 正品率
    jcb             NUMERIC(18,4),                      -- 净成本
    yunfei          NUMERIC(18,4),                      -- 运费
    shuijin         NUMERIC(18,4),                      -- 理论税金
    shuijin_sg      NUMERIC(18,4),                      -- 实际税金
    xscb            NUMERIC(18,4),                      -- 销售成本
    khfl            NUMERIC(18,4),                      -- 客户返利

    -- 售价与利润
    saleprice       NUMERIC(18,4),                      -- 产品售价
    mlr             NUMERIC(18,4),                      -- 单机毛利润
    mlr_dp          NUMERIC(18,4),                      -- 单品毛利润
    bzlr            NUMERIC(18,4),                      -- 标准利润
    jsprice         NUMERIC(18,4),                      -- 结算价
    myprice         NUMERIC(18,4),                      -- 美元价

    -- 前道（织造）
    countprice      NUMERIC(18,4),                      -- 前道合计
    zhis            NUMERIC(18,4),                      -- 下机时间
    lyl             NUMERIC(18,4),                      -- 利用率
    rcl             NUMERIC(18,4),                      -- 日产量
    zzsb            VARCHAR(128) NOT NULL DEFAULT '',   -- 织造设备
    sbdj            NUMERIC(18,4),                      -- 机台费
    zzcb            NUMERIC(18,4),                      -- 织造成本
    qdglf           NUMERIC(18,4),                      -- 前道管理费用

    -- 后道（缝拼/包装）
    dxprice         NUMERIC(18,4),                      -- 定型
    otherprice      NUMERIC(18,4),                      -- 其他工价
    yllyl           NUMERIC(18,4),                      -- 原料利用率
    sumprice        NUMERIC(18,4),                      -- 原料金额
    fpprice         NUMERIC(18,4),                      -- 缝拼工价
    hdprice         NUMERIC(18,4),                      -- 后道合计
    bzprice         NUMERIC(18,4),                      -- 包装
    hdglf           NUMERIC(18,4),                      -- 后道管理费用
    fllyl           NUMERIC(18,4),                      -- 辅料利用率
    flsum           NUMERIC(18,4),                      -- 辅料金额

    -- 系统字段
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 报价单号唯一索引
CREATE UNIQUE INDEX IF NOT EXISTS uk_quotations_dh ON quotations (dh);

-- 常用查询索引
CREATE INDEX IF NOT EXISTS idx_quotations_khname ON quotations (khname);
CREATE INDEX IF NOT EXISTS idx_quotations_huohao ON quotations (huohao);
CREATE INDEX IF NOT EXISTS idx_quotations_zhdate ON quotations (zhdate);

-- 更新时间自动维护
CREATE OR REPLACE FUNCTION update_quotations_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_quotations_updated_at ON quotations;
CREATE TRIGGER trg_quotations_updated_at
    BEFORE UPDATE ON quotations
    FOR EACH ROW
    EXECUTE FUNCTION update_quotations_updated_at();

COMMENT ON TABLE quotations IS '报价单表：产品报价成本/利润/工价明细';
