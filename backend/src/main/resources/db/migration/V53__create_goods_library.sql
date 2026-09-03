-- ============================================================
-- V53: 商品库（goods_library）
-- 文件夹式商品管理：第一层信息（均可空，文件夹名=货号+品名）+ 四类图片 OSS key + 单个备注字段
-- 备注 remark 为自由文本，内容可填写卖点、竞品、功能、对应人群、使用场景等
-- ============================================================

CREATE TABLE IF NOT EXISTS goods_library (
  id                bigserial PRIMARY KEY,
  folder_name       varchar(300) NOT NULL DEFAULT '',   -- 文件夹名 = 货号 + 品名
  initiator         varchar(100),                        -- 发起人
  sampler           varchar(100),                        -- 打样员
  product_name      varchar(200),                        -- 品名
  goods_no          varchar(100),                        -- 货号
  customer          varchar(200),                        -- 客户
  order_no          varchar(100),                        -- 订单号
  main_image_key    varchar(500),                        -- 主图 OSS key
  side_image_key    varchar(500),                        -- 侧面图 OSS key
  detail_image_key  varchar(500),                        -- 细节 OSS key
  product_image_key varchar(500),                        -- 产品图 OSS key
  remark            text,                                -- 备注（卖点/竞品/功能/对应人群/使用场景等）
  user_id           varchar(64),
  created_at        timestamp NOT NULL DEFAULT now(),
  updated_at        timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_goods_library_created ON goods_library (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_goods_library_goods_no ON goods_library (goods_no);

COMMENT ON TABLE goods_library IS '商品库：文件夹式商品管理（货号+品名命名），图片存 OSS';
COMMENT ON COLUMN goods_library.remark IS '备注：卖点、竞品、功能、对应人群、使用场景等自由文本';

-- 已存在旧表（含五个独立备注列）时升级为单 remark 列
ALTER TABLE goods_library ADD COLUMN IF NOT EXISTS remark TEXT;
ALTER TABLE goods_library DROP COLUMN IF EXISTS selling_points,
  DROP COLUMN IF EXISTS competitors,
  DROP COLUMN IF EXISTS features,
  DROP COLUMN IF EXISTS target_audience,
  DROP COLUMN IF EXISTS usage_scenarios;
