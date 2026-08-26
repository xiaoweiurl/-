-- =====================================================
-- 销售订单表 + 丝袜工艺单表（上游同步表）
-- 价值：
--   order_xs_list     业务员效能(ywyname) / 真实订单需求(sl_sum) / 交期风险(jh_date+state+sfplan)
--   order_sw_gongyidan 工艺参数(xjkz/xjsl/zcl/jix/zs/djcl) / fpkz缝拼克重权威数据源(pfkz)
-- 关联键：huohao(生产货号) 贯通 报价单↔销售订单↔工艺单↔排产表
-- 幂等：CREATE TABLE IF NOT EXISTS，不清空上游已同步数据
-- =====================================================

-- ---------- 1. 销售订单表 ----------
CREATE TABLE IF NOT EXISTS order_xs_list (
  dh           varchar(50) NOT NULL,
  zhdate       timestamp,
  state        varchar(20),
  zxtate       varchar(20),
  printnum     int4,
  jh_date      timestamp,
  business_dh  varchar(50),
  ddtype       varchar(20),
  khname       varchar(100),
  detailhuohaocp varchar(100),
  detailhuohao varchar(100),
  sl_sum       numeric(12,2),
  remark       text,
  ywyname      varchar(50),
  sfplan       varchar(10),
  zhuser       varchar(50),
  checkuser    varchar(50),
  ckeckdate    timestamp
);

COMMENT ON TABLE  order_xs_list IS '销售订单表（上游同步）：业务员效能/订单需求/交期风险数据源';
COMMENT ON COLUMN order_xs_list.dh             IS '单号';
COMMENT ON COLUMN order_xs_list.zhdate         IS '下单日期';
COMMENT ON COLUMN order_xs_list.state          IS '状态文本：0→编辑、1→审核、其他→待审核';
COMMENT ON COLUMN order_xs_list.zxtate         IS '执行状态文本：0→未审核、1→已复审、其他→已经终审';
COMMENT ON COLUMN order_xs_list.printnum       IS '打印次数';
COMMENT ON COLUMN order_xs_list.jh_date        IS '交货日期';
COMMENT ON COLUMN order_xs_list.business_dh    IS '业务单号';
COMMENT ON COLUMN order_xs_list.ddtype         IS '销售类型';
COMMENT ON COLUMN order_xs_list.khname         IS '客户名称';
COMMENT ON COLUMN order_xs_list.detailhuohaocp IS '成品货号';
COMMENT ON COLUMN order_xs_list.detailhuohao   IS '生产货号';
COMMENT ON COLUMN order_xs_list.sl_sum         IS '数量合计';
COMMENT ON COLUMN order_xs_list.remark         IS '备注';
COMMENT ON COLUMN order_xs_list.ywyname        IS '业务员（业务员效能分析核心字段）';
COMMENT ON COLUMN order_xs_list.sfplan         IS '是否下计划';
COMMENT ON COLUMN order_xs_list.zhuser         IS '制单人';
COMMENT ON COLUMN order_xs_list.checkuser      IS '审核人';
COMMENT ON COLUMN order_xs_list.ckeckdate      IS '审核日期';

CREATE INDEX IF NOT EXISTS idx_order_xs_list_khname       ON order_xs_list (khname);
CREATE INDEX IF NOT EXISTS idx_order_xs_list_ywyname      ON order_xs_list (ywyname);
CREATE INDEX IF NOT EXISTS idx_order_xs_list_detailhuohao ON order_xs_list (detailhuohao);
CREATE INDEX IF NOT EXISTS idx_order_xs_list_jh_date      ON order_xs_list (jh_date);

-- ---------- 2. 丝袜工艺单表 ----------
CREATE TABLE IF NOT EXISTS order_sw_gongyidan (
  bh        varchar(50) NOT NULL,
  hhtype    varchar(50),
  huohao    varchar(100),
  spname    varchar(100),
  dybanhao  varchar(50),
  cxm       varchar(100),
  xjkz      numeric(12,4),
  xjsl      numeric(12,2),
  pfkz      numeric(12,4),
  cpkz      numeric(12,4),
  zcl       numeric(10,4),
  jix       varchar(50),
  zs        varchar(50),
  yajiao    varchar(50),
  nd        varchar(50),
  djcl      numeric(12,2),
  hhywy     varchar(50),
  qd_dys    varchar(50),
  hd_dys    varchar(50),
  dw        varchar(20),
  remark    text
);

COMMENT ON TABLE  order_sw_gongyidan IS '丝袜工艺单表（上游同步）：工艺参数与缝拼克重权威数据源';
COMMENT ON COLUMN order_sw_gongyidan.bh       IS '编号';
COMMENT ON COLUMN order_sw_gongyidan.hhtype   IS '货号类别';
COMMENT ON COLUMN order_sw_gongyidan.huohao   IS '生产货号（关联报价单/销售订单/排产表）';
COMMENT ON COLUMN order_sw_gongyidan.spname   IS '品名';
COMMENT ON COLUMN order_sw_gongyidan.dybanhao IS '版本号';
COMMENT ON COLUMN order_sw_gongyidan.cxm      IS '程序名';
COMMENT ON COLUMN order_sw_gongyidan.xjkz     IS '下机克重';
COMMENT ON COLUMN order_sw_gongyidan.xjsl     IS '下机秒数（产能计算权威输入）';
COMMENT ON COLUMN order_sw_gongyidan.pfkz     IS '缝拼克重（报价核算 fpkz 的权威兜底数据源）';
COMMENT ON COLUMN order_sw_gongyidan.cpkz     IS '成品克重';
COMMENT ON COLUMN order_sw_gongyidan.zcl      IS '制成率';
COMMENT ON COLUMN order_sw_gongyidan.jix      IS '机型';
COMMENT ON COLUMN order_sw_gongyidan.zs       IS '针数';
COMMENT ON COLUMN order_sw_gongyidan.yajiao   IS '压脚';
COMMENT ON COLUMN order_sw_gongyidan.nd       IS '牛顿';
COMMENT ON COLUMN order_sw_gongyidan.djcl     IS '理论产量（产能标准基准，可对比实际排产算利用率偏差）';
COMMENT ON COLUMN order_sw_gongyidan.hhywy    IS '业务员';
COMMENT ON COLUMN order_sw_gongyidan.qd_dys   IS '前道打样师';
COMMENT ON COLUMN order_sw_gongyidan.hd_dys   IS '后道打样师';
COMMENT ON COLUMN order_sw_gongyidan.dw       IS '单位';
COMMENT ON COLUMN order_sw_gongyidan.remark   IS '总备注';

CREATE INDEX IF NOT EXISTS idx_order_sw_gongyidan_huohao ON order_sw_gongyidan (huohao);
CREATE INDEX IF NOT EXISTS idx_order_sw_gongyidan_jix    ON order_sw_gongyidan (jix);
CREATE INDEX IF NOT EXISTS idx_order_sw_gongyidan_hhywy  ON order_sw_gongyidan (hhywy);
