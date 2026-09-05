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

 Date: 26/08/2026 15:33:43
*/


-- ----------------------------
-- Table structure for order_xs_list
-- ----------------------------
DROP TABLE IF EXISTS "public"."order_xs_list";
CREATE TABLE "public"."order_xs_list" (
  "dh" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "zhdate" timestamp(6),
  "state" varchar(20) COLLATE "pg_catalog"."default",
  "zxtate" varchar(20) COLLATE "pg_catalog"."default",
  "printnum" int4,
  "jh_date" timestamp(6),
  "business_dh" varchar(50) COLLATE "pg_catalog"."default",
  "ddtype" varchar(20) COLLATE "pg_catalog"."default",
  "khname" varchar(100) COLLATE "pg_catalog"."default",
  "detailhuohaocp" varchar(100) COLLATE "pg_catalog"."default",
  "detailhuohao" varchar(100) COLLATE "pg_catalog"."default",
  "sl_sum" numeric(12,2),
  "remark" text COLLATE "pg_catalog"."default",
  "ywyname" varchar(50) COLLATE "pg_catalog"."default",
  "sfplan" varchar(10) COLLATE "pg_catalog"."default",
  "zhuser" varchar(50) COLLATE "pg_catalog"."default",
  "checkuser" varchar(50) COLLATE "pg_catalog"."default",
  "ckeckdate" timestamp(6)
)
;
COMMENT ON COLUMN "public"."order_xs_list"."dh" IS '单号';
COMMENT ON COLUMN "public"."order_xs_list"."zhdate" IS '下单日期';
COMMENT ON COLUMN "public"."order_xs_list"."state" IS '状态文本：0→编辑、1→审核、其他→待审核';
COMMENT ON COLUMN "public"."order_xs_list"."zxtate" IS '执行状态文本：0→未审核、1→已复审、其他→已经终审';
COMMENT ON COLUMN "public"."order_xs_list"."printnum" IS '打印次数';
COMMENT ON COLUMN "public"."order_xs_list"."jh_date" IS '交货日期';
COMMENT ON COLUMN "public"."order_xs_list"."business_dh" IS '业务单号';
COMMENT ON COLUMN "public"."order_xs_list"."ddtype" IS '销售类型';
COMMENT ON COLUMN "public"."order_xs_list"."khname" IS '客户名称';
COMMENT ON COLUMN "public"."order_xs_list"."detailhuohaocp" IS '成品货号';
COMMENT ON COLUMN "public"."order_xs_list"."detailhuohao" IS '生产货号';
COMMENT ON COLUMN "public"."order_xs_list"."sl_sum" IS '数量合计';
COMMENT ON COLUMN "public"."order_xs_list"."remark" IS '备注';
COMMENT ON COLUMN "public"."order_xs_list"."ywyname" IS '业务员';
COMMENT ON COLUMN "public"."order_xs_list"."sfplan" IS '是否下计划';
COMMENT ON COLUMN "public"."order_xs_list"."zhuser" IS '制单人';
COMMENT ON COLUMN "public"."order_xs_list"."checkuser" IS '审核人';
COMMENT ON COLUMN "public"."order_xs_list"."ckeckdate" IS '审核日期';

-- ----------------------------
-- Primary Key structure for table order_xs_list
-- ----------------------------
ALTER TABLE "public"."order_xs_list" ADD CONSTRAINT "order_xs_list_pkey" PRIMARY KEY ("dh");
