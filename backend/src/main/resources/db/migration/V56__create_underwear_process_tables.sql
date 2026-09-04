-- V56: 内衣工艺单全链路关联表（业务员LLM对话货号维度ERP数据）
-- 关联键：order_jfk_gongyidan.huohao = order_buj_component.hhname
--        = order_gongxu_process.hhname = order_gongxu_price.hhname
--        = raw_material_warehouse.huohao（已存在，V10）
-- 用途：货号全链路带出 采购原料品种/机台机型/理论产量/工序/工价，配合报价单与销售订单综合分析

-- 内衣工艺单数据表（若已存在则跳过）
CREATE TABLE IF NOT EXISTS order_jfk_gongyidan (
  bh varchar(50) NOT NULL,
  hhtype varchar(50),
  huohao varchar(100),
  spname varchar(100),
  designer varchar(50),
  dw varchar(20),
  rsjgh varchar(200),
  qd_dys varchar(100),
  hd_dys varchar(100),
  dybanhao varchar(50),
  remark text,
  CONSTRAINT order_jfk_gongyidan_pkey PRIMARY KEY (bh)
);
COMMENT ON TABLE order_jfk_gongyidan IS '内衣工艺单数据表';

-- 内衣货号工艺部件查询表（机台机型/理论产量/织造难度，按部件）
CREATE TABLE IF NOT EXISTS order_buj_component (
  hhname varchar(100),
  color varchar(50),
  chima varchar(20),
  buj varchar(100),
  zbj int4,
  jix varchar(100),
  zs int4,
  cxm varchar(100),
  tongjing numeric(10,2),
  bili varchar(50),
  kez numeric(10,2),
  xjtime numeric(10,2),
  tjcxm varchar(100),
  tjxs varchar(50),
  tzs varchar(50),
  skzjj varchar(100),
  xf varchar(50),
  zznd varchar(50),
  llcl numeric(10,2),
  remark text,
  vchima varchar(20),
  vcolor varchar(50),
  vtzs varchar(50),
  ischeck varchar(10),
  isrecheck varchar(10)
);
COMMENT ON TABLE order_buj_component IS '内衣货号工艺部件查询表（hhname=货号，jix=机型，llcl=理论产量）';
CREATE INDEX IF NOT EXISTS idx_buj_hhname ON order_buj_component (hhname);

-- 内衣货号工艺工序查询表（工序名称/机种/针数/用时）
CREATE TABLE IF NOT EXISTS order_gongxu_process (
  hhname varchar(100),
  wtname varchar(100),
  jizhong varchar(100),
  zhenju varchar(50),
  zhenhao varchar(50),
  zhenmu varchar(50),
  zhens varchar(50),
  zline varchar(100),
  sline varchar(100),
  yongl numeric(10,4),
  yongl2 varchar(50),
  sort int4,
  tjtype varchar(100),
  sctype varchar(50),
  using_state varchar(10),
  zhgx varchar(10),
  tims numeric(10,2),
  ischeck varchar(10),
  isrecheck varchar(10)
);
COMMENT ON TABLE order_gongxu_process IS '内衣货号工艺工序查询表（hhname=货号，wtname=工序名称）';
CREATE INDEX IF NOT EXISTS idx_gxp_hhname ON order_gongxu_process (hhname);

-- 内衣货号工序工价查询表（技术工价/工价/临时工价）
CREATE TABLE IF NOT EXISTS order_gongxu_price (
  hhname varchar(100),
  wtname varchar(100),
  jsprice numeric(10,4),
  price numeric(10,4),
  tempworker_price numeric(10,4),
  remarkgz text,
  state varchar(20)
);
COMMENT ON TABLE order_gongxu_price IS '内衣货号工序工价查询表（hhname=货号，wtname=工序）';
CREATE INDEX IF NOT EXISTS idx_gxpr_hhname ON order_gongxu_price (hhname);
