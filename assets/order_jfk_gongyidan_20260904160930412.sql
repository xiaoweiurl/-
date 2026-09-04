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

 Date: 04/09/2026 16:09:00
*/


-- ----------------------------
-- Table structure for order_jfk_gongyidan
-- ----------------------------
DROP TABLE IF EXISTS "public"."order_jfk_gongyidan";
CREATE TABLE "public"."order_jfk_gongyidan" (
  "bh" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "hhtype" varchar(50) COLLATE "pg_catalog"."default",
  "huohao" varchar(100) COLLATE "pg_catalog"."default",
  "spname" varchar(100) COLLATE "pg_catalog"."default",
  "designer" varchar(50) COLLATE "pg_catalog"."default",
  "dw" varchar(20) COLLATE "pg_catalog"."default",
  "rsjgh" varchar(200) COLLATE "pg_catalog"."default",
  "qd_dys" varchar(100) COLLATE "pg_catalog"."default",
  "hd_dys" varchar(100) COLLATE "pg_catalog"."default",
  "dybanhao" varchar(50) COLLATE "pg_catalog"."default",
  "remark" text COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."bh" IS '编号';
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."hhtype" IS '货号类别';
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."huohao" IS '货号';
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."spname" IS '品名';
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."designer" IS '设计师';
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."dw" IS '单位';
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."rsjgh" IS '染色厂';
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."qd_dys" IS '前道打样师傅';
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."hd_dys" IS '后道打样师傅';
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."dybanhao" IS '打样版号';
COMMENT ON COLUMN "public"."order_jfk_gongyidan"."remark" IS '备注';
COMMENT ON TABLE "public"."order_jfk_gongyidan" IS '内衣工艺单数据表';

-- ----------------------------
-- Primary Key structure for table order_jfk_gongyidan
-- ----------------------------
ALTER TABLE "public"."order_jfk_gongyidan" ADD CONSTRAINT "order_jfk_gongyidan_pkey" PRIMARY KEY ("bh");
