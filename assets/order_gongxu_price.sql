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

 Date: 04/09/2026 16:08:27
*/


-- ----------------------------
-- Table structure for order_gongxu_price
-- ----------------------------
DROP TABLE IF EXISTS "public"."order_gongxu_price";
CREATE TABLE "public"."order_gongxu_price" (
  "hhname" varchar(100) COLLATE "pg_catalog"."default",
  "wtname" varchar(100) COLLATE "pg_catalog"."default",
  "jsprice" numeric(10,4),
  "price" numeric(10,4),
  "tempworker_price" numeric(10,4),
  "remarkgz" text COLLATE "pg_catalog"."default",
  "state" varchar(20) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."order_gongxu_price"."hhname" IS '货号';
COMMENT ON COLUMN "public"."order_gongxu_price"."wtname" IS '工序';
COMMENT ON COLUMN "public"."order_gongxu_price"."jsprice" IS '技术工价';
COMMENT ON COLUMN "public"."order_gongxu_price"."price" IS '工价';
COMMENT ON COLUMN "public"."order_gongxu_price"."tempworker_price" IS '临时工价';
COMMENT ON COLUMN "public"."order_gongxu_price"."remarkgz" IS '工价备注';
COMMENT ON COLUMN "public"."order_gongxu_price"."state" IS '审核状态:未审核 / 已审核';
COMMENT ON TABLE "public"."order_gongxu_price" IS '内衣货号工序工价查询表';
