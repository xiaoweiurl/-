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

 Date: 04/09/2026 16:08:15
*/


-- ----------------------------
-- Table structure for order_buj_component
-- ----------------------------
DROP TABLE IF EXISTS "public"."order_buj_component";
CREATE TABLE "public"."order_buj_component" (
  "hhname" varchar(100) COLLATE "pg_catalog"."default",
  "color" varchar(50) COLLATE "pg_catalog"."default",
  "chima" varchar(20) COLLATE "pg_catalog"."default",
  "buj" varchar(100) COLLATE "pg_catalog"."default",
  "zbj" int4,
  "jix" varchar(100) COLLATE "pg_catalog"."default",
  "zs" int4,
  "cxm" varchar(100) COLLATE "pg_catalog"."default",
  "tongjing" numeric(10,2),
  "bili" varchar(50) COLLATE "pg_catalog"."default",
  "kez" numeric(10,2),
  "xjtime" numeric(10,2),
  "tjcxm" varchar(100) COLLATE "pg_catalog"."default",
  "tjxs" varchar(50) COLLATE "pg_catalog"."default",
  "tzs" varchar(50) COLLATE "pg_catalog"."default",
  "skzjj" varchar(100) COLLATE "pg_catalog"."default",
  "xf" varchar(50) COLLATE "pg_catalog"."default",
  "zznd" varchar(50) COLLATE "pg_catalog"."default",
  "llcl" numeric(10,2),
  "remark" text COLLATE "pg_catalog"."default",
  "vchima" varchar(20) COLLATE "pg_catalog"."default",
  "vcolor" varchar(50) COLLATE "pg_catalog"."default",
  "vtzs" varchar(50) COLLATE "pg_catalog"."default",
  "ischeck" varchar(10) COLLATE "pg_catalog"."default",
  "isrecheck" varchar(10) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."order_buj_component"."hhname" IS '货号';
COMMENT ON COLUMN "public"."order_buj_component"."color" IS '颜色';
COMMENT ON COLUMN "public"."order_buj_component"."chima" IS '尺码';
COMMENT ON COLUMN "public"."order_buj_component"."buj" IS '部件';
COMMENT ON COLUMN "public"."order_buj_component"."zbj" IS '主部件:1 是 / 0 不是';
COMMENT ON COLUMN "public"."order_buj_component"."jix" IS '机型';
COMMENT ON COLUMN "public"."order_buj_component"."zs" IS '针数';
COMMENT ON COLUMN "public"."order_buj_component"."cxm" IS '程序名';
COMMENT ON COLUMN "public"."order_buj_component"."tongjing" IS '口径';
COMMENT ON COLUMN "public"."order_buj_component"."bili" IS '比例';
COMMENT ON COLUMN "public"."order_buj_component"."kez" IS '克重';
COMMENT ON COLUMN "public"."order_buj_component"."xjtime" IS '下机时间';
COMMENT ON COLUMN "public"."order_buj_component"."tjcxm" IS '调机程序名';
COMMENT ON COLUMN "public"."order_buj_component"."tjxs" IS '调机线速';
COMMENT ON COLUMN "public"."order_buj_component"."tzs" IS '提字色';
COMMENT ON COLUMN "public"."order_buj_component"."skzjj" IS '圣克罩间距';
COMMENT ON COLUMN "public"."order_buj_component"."xf" IS '吸风 M/S';
COMMENT ON COLUMN "public"."order_buj_component"."zznd" IS '织造难度';
COMMENT ON COLUMN "public"."order_buj_component"."llcl" IS '理论产量';
COMMENT ON COLUMN "public"."order_buj_component"."remark" IS '备注';
COMMENT ON COLUMN "public"."order_buj_component"."vchima" IS '校验尺码';
COMMENT ON COLUMN "public"."order_buj_component"."vcolor" IS '校验颜色';
COMMENT ON COLUMN "public"."order_buj_component"."vtzs" IS '校验提字色';
COMMENT ON COLUMN "public"."order_buj_component"."ischeck" IS '确认状态:是/否';
COMMENT ON COLUMN "public"."order_buj_component"."isrecheck" IS '审核状态:是/否';
COMMENT ON TABLE "public"."order_buj_component" IS '内衣货号工艺部件查询表';
