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

 Date: 27/07/2026 16:34:01
*/


-- ----------------------------
-- Table structure for albums
-- ----------------------------
DROP TABLE IF EXISTS "public"."albums";
CREATE TABLE "public"."albums" (
  "id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL DEFAULT (uuid_generate_v4())::text,
  "name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "description" text COLLATE "pg_catalog"."default",
  "cover_image_id" varchar(36) COLLATE "pg_catalog"."default",
  "user_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "type" varchar(20) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'user'::character varying,
  "matching_mode" varchar(20) COLLATE "pg_catalog"."default" DEFAULT 'include'::character varying,
  "color" varchar(20) COLLATE "pg_catalog"."default",
  "sort_order" int4 DEFAULT 0,
  "company" varchar(50) COLLATE "pg_catalog"."default",
  "created_at" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "updated_at" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "cover_url" varchar(500) COLLATE "pg_catalog"."default",
  "full_name" varchar(200) COLLATE "pg_catalog"."default",
  "image_count" int4,
  "is_system" bool DEFAULT false,
  "matching_config" text COLLATE "pg_catalog"."default",
  "parent_id" varchar(36) COLLATE "pg_catalog"."default",
  "path" varchar(500) COLLATE "pg_catalog"."default",
  "share_enabled" bool,
  "share_password" varchar(50) COLLATE "pg_catalog"."default",
  "visibility" varchar(20) COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Indexes structure for table albums
-- ----------------------------
CREATE INDEX "idx_album_sort_order" ON "public"."albums" USING btree (
  "sort_order" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_album_user_id" ON "public"."albums" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_albums_company" ON "public"."albums" USING btree (
  "company" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_albums_user_id" ON "public"."albums" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table albums
-- ----------------------------
ALTER TABLE "public"."albums" ADD CONSTRAINT "albums_pkey" PRIMARY KEY ("id");
