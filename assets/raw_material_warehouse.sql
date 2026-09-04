/*
 Navicat Premium Data Transfer

 Source Server         : localhost
 Source Server Type    : PostgreSQL
 Source Server Version : 180004 (180004)
 Source Host           : localhost:5432
 Source Catalog        : image_management
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 180004 (180004)
 File Encoding         : 65001

 Date: 04/09/2026 16:10:38
*/


-- ----------------------------
-- Table structure for raw_material_warehouse
-- ----------------------------
DROP TABLE IF EXISTS "public"."raw_material_warehouse";
CREATE TABLE "public"."raw_material_warehouse" (
  "id" int4 NOT NULL DEFAULT nextval('raw_material_warehouse_id_seq'::regclass),
  "product_code" varchar(128) COLLATE "pg_catalog"."default",
  "color" varchar(100) COLLATE "pg_catalog"."default",
  "batch_no" varchar(100) COLLATE "pg_catalog"."default",
  "unit" varchar(50) COLLATE "pg_catalog"."default",
  "unit_price" numeric(18,4) DEFAULT 0,
  "company" varchar(50) COLLATE "pg_catalog"."default",
  "huohao" varchar(128) COLLATE "pg_catalog"."default",
  "size" varchar(50) COLLATE "pg_catalog"."default",
  "component" varchar(100) COLLATE "pg_catalog"."default",
  "supplier" varchar(200) COLLATE "pg_catalog"."default",
  "material_name" varchar(200) COLLATE "pg_catalog"."default",
  "specification" varchar(200) COLLATE "pg_catalog"."default",
  "material_color" varchar(100) COLLATE "pg_catalog"."default",
  "twist_direction" varchar(20) COLLATE "pg_catalog"."default",
  "usage_per_unit" numeric(12,4),
  "loss_rate" numeric(8,2),
  "remark" text COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."raw_material_warehouse"."id" IS '自增主键';
COMMENT ON COLUMN "public"."raw_material_warehouse"."product_code" IS '原料编码【非报表字段】原料统计报表Excel无此列，导入不填充，遗留字段；真实物料标识=物料名称+规格';
COMMENT ON COLUMN "public"."raw_material_warehouse"."color" IS '颜色（成品颜色）Excel表头"颜色"；注意与 material_color（物料颜色）区分';
COMMENT ON COLUMN "public"."raw_material_warehouse"."batch_no" IS '批号 Excel表头"批号"；与 raw_material_purchase.batch_no 对应，同批号采购价可比';
COMMENT ON COLUMN "public"."raw_material_warehouse"."unit" IS '单位 Excel表头"单位"；kg / g / 双 等';
COMMENT ON COLUMN "public"."raw_material_warehouse"."unit_price" IS '单价【非报表字段】原料统计报表Excel无此列，导入不填充；仅页面手工维护，作为智能报价参考价与供应商对比数据源';
COMMENT ON COLUMN "public"."raw_material_warehouse"."company" IS '公司【非报表字段】原料统计报表Excel无此列，导入不填充';
COMMENT ON COLUMN "public"."raw_material_warehouse"."huohao" IS '成品货号【关联键】Excel表头"货号"；关联 order_bjd_query.huohao / order_xs_list.detailhuohao / production_plan.product_code，v_product_genealogy 谱系入口';
COMMENT ON COLUMN "public"."raw_material_warehouse"."size" IS '尺码 Excel表头"尺码"；如 22-24 / 26-28，对应 order_bjd_query.chima';
COMMENT ON COLUMN "public"."raw_material_warehouse"."component" IS '部件 Excel表头"部件"；如袜口/袜筒/脚尖/橡筋，同一货号不同部件分别用料';
COMMENT ON COLUMN "public"."raw_material_warehouse"."supplier" IS '供应商 Excel表头"供应商"；与 raw_material_purchase.supplier 同名等价，供应商比价维度';
COMMENT ON COLUMN "public"."raw_material_warehouse"."material_name" IS '物料名称 Excel表头"物料名称"；如锦纶丝/氨纶包覆纱/橡筋线';
COMMENT ON COLUMN "public"."raw_material_warehouse"."specification" IS '规格 Excel表头"规格"；如 70D/24F、2070/48F';
COMMENT ON COLUMN "public"."raw_material_warehouse"."material_color" IS '物料颜色 Excel表头"物料颜色"；原料本身的颜色（Z本色/黑/白等）';
COMMENT ON COLUMN "public"."raw_material_warehouse"."twist_direction" IS '捻向 Excel表头"捻向"；S捻 / Z捻';
COMMENT ON COLUMN "public"."raw_material_warehouse"."usage_per_unit" IS '单件用量 Excel表头"单件用量"；每双（件）成品消耗的物料量，智能报价「用量×采购最低价」的用量来源';
COMMENT ON COLUMN "public"."raw_material_warehouse"."loss_rate" IS '损耗率(%) Excel表头"损耗(%)"；净用量 = 单件用量 × (1 + loss_rate/100)';
COMMENT ON COLUMN "public"."raw_material_warehouse"."remark" IS '备注 Excel表头"备注"';
COMMENT ON TABLE "public"."raw_material_warehouse" IS '原料入库表——按「成品货号+部件+物料」记录每双袜子的用料BOM与入库明细，对齐上游Excel14列结构（货号/颜色/尺码/部件/供应商/物料名称/规格/物料颜色/批号/捻向/单位/单件用量/损耗%/备注）';

-- ----------------------------
-- Indexes structure for table raw_material_warehouse
-- ----------------------------
CREATE INDEX "idx_rmw_huohao" ON "public"."raw_material_warehouse" USING btree (
  "huohao" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_rmw_material_name" ON "public"."raw_material_warehouse" USING btree (
  "material_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_rmw_supplier" ON "public"."raw_material_warehouse" USING btree (
  "supplier" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table raw_material_warehouse
-- ----------------------------
ALTER TABLE "public"."raw_material_warehouse" ADD CONSTRAINT "raw_material_warehouse_pkey" PRIMARY KEY ("id");
