-- V55: goods_library.initiator（发起人）改为必填
-- 业务要求：商品文件夹必须归属到具体发起人，创建/更新均不允许为空
-- 1) 存量空值兜底填充，避免 SET NOT NULL 失败
UPDATE goods_library
SET initiator = '未填写', updated_at = now()
WHERE initiator IS NULL OR btrim(initiator) = '';

-- 2) 添加 NOT NULL 约束（仅挡 NULL）
ALTER TABLE goods_library ALTER COLUMN initiator SET NOT NULL;

-- 3) CHECK 约束挡空字符串/纯空白（NOT NULL 无法拦截 ''）
ALTER TABLE goods_library
ADD CONSTRAINT goods_library_initiator_not_blank CHECK (btrim(initiator) <> '');
