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

 Date: 26/08/2026 15:33:24
*/


-- ----------------------------
-- Table structure for order_sw_gongyidan
-- ----------------------------
DROP TABLE IF EXISTS "public"."order_sw_gongyidan";
CREATE TABLE "public"."order_sw_gongyidan" (
  "bh" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "hhtype" varchar(50) COLLATE "pg_catalog"."default",
  "huohao" varchar(100) COLLATE "pg_catalog"."default",
  "spname" varchar(100) COLLATE "pg_catalog"."default",
  "dybanhao" varchar(50) COLLATE "pg_catalog"."default",
  "cxm" varchar(100) COLLATE "pg_catalog"."default",
  "xjkz" numeric(12,4),
  "xjsl" numeric(12,2),
  "pfkz" numeric(12,4),
  "cpkz" numeric(12,4),
  "zcl" numeric(10,4),
  "jix" varchar(50) COLLATE "pg_catalog"."default",
  "zs" varchar(50) COLLATE "pg_catalog"."default",
  "yajiao" varchar(50) COLLATE "pg_catalog"."default",
  "nd" varchar(50) COLLATE "pg_catalog"."default",
  "djcl" numeric(12,2),
  "hhywy" varchar(50) COLLATE "pg_catalog"."default",
  "qd_dys" varchar(50) COLLATE "pg_catalog"."default",
  "hd_dys" varchar(50) COLLATE "pg_catalog"."default",
  "dw" varchar(20) COLLATE "pg_catalog"."default",
  "remark" text COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."order_sw_gongyidan"."bh" IS '编号';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."hhtype" IS '货号类别';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."huohao" IS '生产货号';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."spname" IS '品名';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."dybanhao" IS '版本号';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."cxm" IS '程序名';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."xjkz" IS '下机克重';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."xjsl" IS '下机秒数';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."pfkz" IS '缝拼克重';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."cpkz" IS '成品克重';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."zcl" IS '制成率';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."jix" IS '机型';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."zs" IS '针数';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."yajiao" IS '压脚';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."nd" IS '牛顿';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."djcl" IS '理论产量';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."hhywy" IS '业务员';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."qd_dys" IS '前道打样师';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."hd_dys" IS '后道打样师';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."dw" IS '单位';
COMMENT ON COLUMN "public"."order_sw_gongyidan"."remark" IS '总备注';

-- ----------------------------
-- Primary Key structure for table order_sw_gongyidan
-- ----------------------------
ALTER TABLE "public"."order_sw_gongyidan" ADD CONSTRAINT "order_sw_gongyidan_pkey" PRIMARY KEY ("bh");
