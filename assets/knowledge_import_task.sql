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

 Date: 17/08/2026 16:48:42
*/


-- ----------------------------
-- Table structure for knowledge_import_task
-- ----------------------------
DROP TABLE IF EXISTS "public"."knowledge_import_task";
CREATE TABLE "public"."knowledge_import_task" (
  "id" int8 NOT NULL DEFAULT nextval('knowledge_import_task_id_seq'::regclass),
  "source" varchar(1024) COLLATE "pg_catalog"."default" NOT NULL,
  "status" varchar(20) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'RUNNING'::character varying,
  "total_files" int4 NOT NULL DEFAULT 0,
  "processed_files" int4 NOT NULL DEFAULT 0,
  "failed_files" int4 NOT NULL DEFAULT 0,
  "total_chunks" int8 NOT NULL DEFAULT 0,
  "error_msg" text COLLATE "pg_catalog"."default",
  "started_at" timestamptz(6) NOT NULL DEFAULT now(),
  "finished_at" timestamptz(6)
)
;

-- ----------------------------
-- Primary Key structure for table knowledge_import_task
-- ----------------------------
ALTER TABLE "public"."knowledge_import_task" ADD CONSTRAINT "knowledge_import_task_pkey" PRIMARY KEY ("id");
