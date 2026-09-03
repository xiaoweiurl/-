-- =====================================================
-- 商品库表（文件夹式商品管理）
-- 第一层信息：发起人/打样员/品名/货号/客户/订单号（均可空）
-- 第二层图片：主图/侧面图/细节/产品图（存 OSS key，均可空）
-- 备注信息：卖点/竞品/功能/对应人群/使用场景（均可空）
-- 文件夹名 = 货号 + 品名
-- 幂等：CREATE TABLE IF NOT EXISTS
-- =====================================================

CREATE TABLE IF NOT EXISTS goods_library (
  id                bigserial PRIMARY KEY,
  folder_name       varchar(300) NOT NULL DEFAULT '',
  initiator         varchar(100),
  sampler           varchar(100),
  product_name      varchar(200),
  goods_no          varchar(100),
  customer          varchar(200),
  order_no          varchar(100),
  main_image_key    varchar(500),
  side_image_key    varchar(500),
  detail_image_key  varchar(500),
  product_image_key varchar(500),
  selling_points    text,
  competitors       text,
  features          text,
  target_audience   text,
  usage_scenarios   text,
  user_id           varchar(64),
  created_at        timestamp NOT NULL DEFAULT now(),
  updated_at        timestamp NOT NULL DEFAULT now()
);

COMMENT ON TABLE  goods_library IS '商品库：文件夹式商品管理（封面=主图）';
COMMENT ON COLUMN goods_library.folder_name       IS '文件夹名称 = 货号 + 品名';
COMMENT ON COLUMN goods_library.initiator         IS '发起人';
COMMENT ON COLUMN goods_library.sampler           IS '打样员';
COMMENT ON COLUMN goods_library.product_name      IS '品名';
COMMENT ON COLUMN goods_library.goods_no          IS '货号';
COMMENT ON COLUMN goods_library.customer          IS '客户';
COMMENT ON COLUMN goods_library.order_no          IS '订单号';
COMMENT ON COLUMN goods_library.main_image_key    IS '主图 OSS key';
COMMENT ON COLUMN goods_library.side_image_key    IS '侧面图 OSS key';
COMMENT ON COLUMN goods_library.detail_image_key  IS '细节图 OSS key';
COMMENT ON COLUMN goods_library.product_image_key IS '产品图 OSS key';
COMMENT ON COLUMN goods_library.selling_points    IS '卖点';
COMMENT ON COLUMN goods_library.competitors       IS '竞品';
COMMENT ON COLUMN goods_library.features          IS '功能';
COMMENT ON COLUMN goods_library.target_audience   IS '对应人群';
COMMENT ON COLUMN goods_library.usage_scenarios   IS '使用场景';

CREATE INDEX IF NOT EXISTS idx_goods_library_created  ON goods_library (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_goods_library_goods_no ON goods_library (goods_no);
