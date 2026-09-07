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

 Date: 04/09/2026 16:08:43
*/


-- ----------------------------
-- Table structure for order_gongxu_process
-- ----------------------------
DROP TABLE IF EXISTS "public"."order_gongxu_process";
CREATE TABLE "public"."order_gongxu_process" (
  "hhname" varchar(100) COLLATE "pg_catalog"."default",
  "wtname" varchar(100) COLLATE "pg_catalog"."default",
  "jizhong" varchar(100) COLLATE "pg_catalog"."default",
  "zhenju" varchar(50) COLLATE "pg_catalog"."default",
  "zhenhao" varchar(50) COLLATE "pg_catalog"."default",
  "zhenmu" varchar(50) COLLATE "pg_catalog"."default",
  "zhens" varchar(50) COLLATE "pg_catalog"."default",
  "zline" varchar(100) COLLATE "pg_catalog"."default",
  "sline" varchar(100) COLLATE "pg_catalog"."default",
  "yongl" numeric(10,4),
  "yongl2" varchar(50) COLLATE "pg_catalog"."default",
  "sort" int4,
  "tjtype" varchar(100) COLLATE "pg_catalog"."default",
  "sctype" varchar(50) COLLATE "pg_catalog"."default",
  "using_state" varchar(10) COLLATE "pg_catalog"."default",
  "zhgx" varchar(10) COLLATE "pg_catalog"."default",
  "tims" numeric(10,2),
  "ischeck" varchar(10) COLLATE "pg_catalog"."default",
  "isrecheck" varchar(10) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."order_gongxu_process"."hhname" IS '货号';
COMMENT ON COLUMN "public"."order_gongxu_process"."wtname" IS '工序名称';
COMMENT ON COLUMN "public"."order_gongxu_process"."jizhong" IS '机种';
COMMENT ON COLUMN "public"."order_gongxu_process"."zhenju" IS '针目';
COMMENT ON COLUMN "public"."order_gongxu_process"."zhenhao" IS '针号';
COMMENT ON COLUMN "public"."order_gongxu_process"."zhenmu" IS '针距';
COMMENT ON COLUMN "public"."order_gongxu_process"."zhens" IS '针数';
COMMENT ON COLUMN "public"."order_gongxu_process"."zline" IS '缝线上';
COMMENT ON COLUMN "public"."order_gongxu_process"."sline" IS '缝线下';
COMMENT ON COLUMN "public"."order_gongxu_process"."yongl" IS '用量/CM 上';
COMMENT ON COLUMN "public"."order_gongxu_process"."yongl2" IS '用量/CM 下';
COMMENT ON COLUMN "public"."order_gongxu_process"."sort" IS '排序号';
COMMENT ON COLUMN "public"."order_gongxu_process"."tjtype" IS '统计类型';
COMMENT ON COLUMN "public"."order_gongxu_process"."sctype" IS '生产类型';
COMMENT ON COLUMN "public"."order_gongxu_process"."using_state" IS '使用中：是/否';
COMMENT ON COLUMN "public"."order_gongxu_process"."zhgx" IS '最后工序：是/否';
COMMENT ON COLUMN "public"."order_gongxu_process"."tims" IS '用时(秒)';
COMMENT ON COLUMN "public"."order_gongxu_process"."ischeck" IS '确认状态是/否';
COMMENT ON COLUMN "public"."order_gongxu_process"."isrecheck" IS '审核状态：是/否';
COMMENT ON TABLE "public"."order_gongxu_process" IS '内衣货号工艺工序查询表';
