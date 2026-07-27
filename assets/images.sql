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

 Date: 27/07/2026 13:47:20
*/


-- ----------------------------
-- Table structure for images
-- ----------------------------
DROP TABLE IF EXISTS "public"."images";
CREATE TABLE "public"."images" (
  "id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL DEFAULT (uuid_generate_v4())::text,
  "name" varchar(500) COLLATE "pg_catalog"."default" NOT NULL,
  "original_name" varchar(500) COLLATE "pg_catalog"."default",
  "file_path" varchar(1000) COLLATE "pg_catalog"."default" NOT NULL,
  "file_key" varchar(500) COLLATE "pg_catalog"."default",
  "url" varchar(1000) COLLATE "pg_catalog"."default",
  "thumbnail_url" varchar(1000) COLLATE "pg_catalog"."default",
  "file_size" int8 NOT NULL,
  "mime_type" varchar(100) COLLATE "pg_catalog"."default",
  "format" varchar(50) COLLATE "pg_catalog"."default",
  "width" int4,
  "height" int4,
  "taken_at" timestamp(6),
  "uploader_id" varchar(36) COLLATE "pg_catalog"."default",
  "user_id" varchar(36) COLLATE "pg_catalog"."default" NOT NULL,
  "company" varchar(50) COLLATE "pg_catalog"."default",
  "source" varchar(20) COLLATE "pg_catalog"."default" DEFAULT 'upload'::character varying,
  "deleted" bool DEFAULT false,
  "deleted_at" timestamp(6),
  "created_at" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "updated_at" timestamp(6) DEFAULT CURRENT_TIMESTAMP,
  "ai_confidence" float8,
  "album_id" varchar(36) COLLATE "pg_catalog"."default",
  "album_name" varchar(100) COLLATE "pg_catalog"."default",
  "classify_method" varchar(20) COLLATE "pg_catalog"."default",
  "description" text COLLATE "pg_catalog"."default",
  "display_order" int4,
  "download_count" int4,
  "favorite" bool DEFAULT false,
  "file_type" varchar(10) COLLATE "pg_catalog"."default",
  "is_main_image" bool DEFAULT false,
  "original_url" varchar(500) COLLATE "pg_catalog"."default",
  "product_id" varchar(255) COLLATE "pg_catalog"."default",
  "resolution" varchar(20) COLLATE "pg_catalog"."default",
  "size" int8,
  "size_formatted" varchar(20) COLLATE "pg_catalog"."default",
  "title" varchar(255) COLLATE "pg_catalog"."default",
  "view_count" int4
)
;

-- ----------------------------
-- Indexes structure for table images
-- ----------------------------
CREATE INDEX "idx_image_album_id" ON "public"."images" USING btree (
  "album_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_image_created_at" ON "public"."images" USING btree (
  "created_at" "pg_catalog"."timestamp_ops" ASC NULLS LAST
);
CREATE INDEX "idx_image_deleted" ON "public"."images" USING btree (
  "deleted" "pg_catalog"."bool_ops" ASC NULLS LAST
);
CREATE INDEX "idx_image_user_id" ON "public"."images" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_images_company" ON "public"."images" USING btree (
  "company" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_images_created_at" ON "public"."images" USING btree (
  "created_at" "pg_catalog"."timestamp_ops" ASC NULLS LAST
);
CREATE INDEX "idx_images_deleted" ON "public"."images" USING btree (
  "deleted" "pg_catalog"."bool_ops" ASC NULLS LAST
);
CREATE INDEX "idx_images_file_key" ON "public"."images" USING btree (
  "file_key" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_images_source" ON "public"."images" USING btree (
  "source" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_images_user_id" ON "public"."images" USING btree (
  "user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table images
-- ----------------------------
ALTER TABLE "public"."images" ADD CONSTRAINT "images_pkey" PRIMARY KEY ("id");
