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

 Date: 28/07/2026 08:23:34
*/


-- ----------------------------
-- Table structure for knowledge_embeddings
-- ----------------------------
DROP TABLE IF EXISTS "public"."knowledge_embeddings";
CREATE TABLE "public"."knowledge_embeddings" (
  "id" uuid NOT NULL DEFAULT uuid_generate_v4(),
  "card_id" uuid,
  "source_type" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
  "source_doc_id" varchar(100) COLLATE "pg_catalog"."default",
  "chunk_index" int4,
  "chunk_text" text COLLATE "pg_catalog"."default",
  "embedding" "public"."vector" NOT NULL,
  "embedding_model" varchar(100) COLLATE "pg_catalog"."default",
  "company" varchar(20) COLLATE "pg_catalog"."default",
  "created_at" timestamp(6) DEFAULT CURRENT_TIMESTAMP
)
;

-- ----------------------------
-- Indexes structure for table knowledge_embeddings
-- ----------------------------
CREATE INDEX "idx_embeddings_source_doc_id" ON "public"."knowledge_embeddings" USING btree (
  "source_doc_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_embeddings_source_type" ON "public"."knowledge_embeddings" USING btree (
  "source_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_knowledge_embeddings_company" ON "public"."knowledge_embeddings" USING btree (
  "company" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_knowledge_embeddings_hnsw" ON "public"."knowledge_embeddings" (
  "embedding" "public"."vector_cosine_ops" ASC NULLS LAST
);
CREATE INDEX "idx_knowledge_embeddings_source_doc_id" ON "public"."knowledge_embeddings" USING btree (
  "source_doc_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_knowledge_embeddings_source_type" ON "public"."knowledge_embeddings" USING btree (
  "source_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_knowledge_embeddings_vector" ON "public"."knowledge_embeddings" (
  "embedding" "public"."vector_cosine_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table knowledge_embeddings
-- ----------------------------
ALTER TABLE "public"."knowledge_embeddings" ADD CONSTRAINT "knowledge_embeddings_pkey" PRIMARY KEY ("id");
