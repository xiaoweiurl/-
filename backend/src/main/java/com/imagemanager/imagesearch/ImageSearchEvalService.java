package com.imagemanager.imagesearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读评测。不写 Milvus，不写 image_vector_index。
 * 用同一款的其他图片，以及缩放/裁剪后的原图，计算 Recall@K 和检索延迟。
 */
@Slf4j
@Service
public class ImageSearchEvalService {

    private final ImageSearchProperties properties;
    private final ImageSearchQueryService queryService;
    private final ImageEmbedder embedder;
    private final ImageVectorIndex vectorIndex;
    private final ImageSearchIndexWorker worker;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;
    private final ObjectMapper objectMapper;

    public ImageSearchEvalService(ImageSearchProperties properties,
                                  ImageSearchQueryService queryService,
                                  ImageEmbedder embedder,
                                  ImageVectorIndex vectorIndex,
                                  ImageSearchIndexWorker worker,
                                  JdbcTemplate jdbcTemplate,
                                  PlatformTransactionManager transactionManager,
                                  ObjectMapper objectMapper) {
        this.properties = properties;
        this.queryService = queryService;
        this.embedder = embedder;
        this.vectorIndex = vectorIndex;
        this.worker = worker;
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    public int runAndPrint(String[] args) {
        int groups = argInt(args, "--groups", 20);
        int synthetic = argInt(args, "--synthetic", 8);
        int k = argInt(args, "--k", 10);
        try {
            Map<String, Object> report = evaluate(groups, synthetic, k);
            System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            return 0;
        } catch (Exception e) {
            System.err.println("评测没有跑起来: " + e.getMessage());
            log.error("以图搜图评测失败", e);
            return 1;
        }
    }

    public Map<String, Object> evaluate(int groupLimit, int syntheticLimit, int k) throws Exception {
        properties.requireEnabled();
        if (!vectorIndex.isReady() && vectorIndex instanceof MilvusImageVectorIndex milvus) {
            milvus.initIfEnabled();
        }
        if (!vectorIndex.isReady()) {
            throw new IllegalStateException("图片向量库未就绪");
        }
        int groups = Math.max(0, Math.min(groupLimit, 50));
        int synthetic = Math.max(0, Math.min(syntheticLimit, 20));
        int topK = Math.max(1, Math.min(k, 50));

        Pass full = runPass(groups, synthetic, topK, EmbeddingVariant.FULL);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("readOnly", true);
        report.put("k", topK);
        report.put("styleQueries", full.styleQueries);
        report.put("syntheticQueries", full.imageRelevant.size() - full.styleQueries);
        report.put("skipped", full.skipped);
        report.put("recallAt1", round(ImageSearchRecall.meanRecall(full.imageRelevant, full.imageRetrieved, 1)));
        report.put("recallAt5", round(ImageSearchRecall.meanRecall(full.imageRelevant, full.imageRetrieved, Math.min(5, topK))));
        report.put("recallAtK", round(ImageSearchRecall.meanRecall(full.imageRelevant, full.imageRetrieved, topK)));
        report.put("hitAt1", round(ImageSearchRecall.hitRate(full.imageRelevant, full.imageRetrieved, 1)));
        report.put("hitAt5", round(ImageSearchRecall.hitRate(full.imageRelevant, full.imageRetrieved, Math.min(5, topK))));
        report.put("hitAtK", round(ImageSearchRecall.hitRate(full.imageRelevant, full.imageRetrieved, topK)));
        report.put("searchLatencyMs", latency(full.searchMs));
        report.put("endToEndLatencyMs", latency(full.endToEndMs));
        report.put("note", "顶层的 Recall 仍是整图集合、按单张图的旧口径，方便和之前的评测对比。"
                + "byCollection 里 SAME_PRODUCT 是商品级召回：去掉查询图自己之后，同一商品的任意一张图出现在 TopK 即命中。"
                + "SIMILAR_REFERENCE 不分组。crop 集合没开时 available=false。失败探针不进分母。");

        Map<String, Object> byCollection = new LinkedHashMap<>();
        byCollection.put("full", collectionBlock(properties.getCollection(), EmbeddingVariant.FULL, topK, full, true));
        if (queryService.cropCollectionReady()) {
            Pass crop = runPass(groups, synthetic, topK, EmbeddingVariant.CROP);
            byCollection.put("crop", collectionBlock(properties.resolvedCropCollection(), EmbeddingVariant.CROP, topK, crop, true));
        } else {
            Map<String, Object> unavailable = new LinkedHashMap<>();
            unavailable.put("available", false);
            unavailable.put("collection", properties.resolvedCropCollection());
            unavailable.put("reason", "裁剪集合未启用或未就绪。设置 IMAGE_SEARCH_CROP_ENABLED=true 并回填 --variant crop 之后再评。");
            byCollection.put("crop", unavailable);
        }
        report.put("byCollection", byCollection);
        Object fullBlock = byCollection.get("full");
        if (fullBlock instanceof Map<?, ?> fullMap) {
            report.put("scenarios", fullMap.get("scenarios"));
        }
        return report;
    }

    private Pass runPass(int groups, int synthetic, int topK, EmbeddingVariant variant) {
        Pass pass = new Pass();
        collectStyleProbes(groups, topK, variant, pass);
        pass.styleQueries = pass.imageRelevant.size();
        collectSyntheticProbes(synthetic, topK, variant, pass);
        return pass;
    }

    private Map<String, Object> collectionBlock(String collection, EmbeddingVariant variant, int topK, Pass pass, boolean available) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("available", available);
        block.put("collection", collection);
        block.put("variant", variant.name().toLowerCase(java.util.Locale.ROOT));
        block.put("styleQueries", pass.styleQueries);
        block.put("syntheticQueries", pass.imageRelevant.size() - pass.styleQueries);
        block.put("skipped", pass.skipped);
        block.put("recallAt1", round(ImageSearchRecall.meanRecall(pass.imageRelevant, pass.imageRetrieved, 1)));
        block.put("recallAt5", round(ImageSearchRecall.meanRecall(pass.imageRelevant, pass.imageRetrieved, Math.min(5, topK))));
        block.put("recallAtK", round(ImageSearchRecall.meanRecall(pass.imageRelevant, pass.imageRetrieved, topK)));
        block.put("scenarios", ImageSearchScenarioMetrics.scenarios(
                topK, pass.imageRelevant, pass.imageRetrieved, pass.productRelevant, pass.productRetrieved));
        block.put("searchLatencyMs", latency(pass.searchMs));
        return block;
    }

    private void collectStyleProbes(int limit, int topK, EmbeddingVariant variant, Pass pass) {
        if (limit <= 0) {
            return;
        }
        List<Probe> probes = new ArrayList<>();
        addGoodsNoProbes(probes, limit);
        addSlotProbes(probes, limit);
        addProductProbes(probes, limit);
        int used = 0;
        for (Probe probe : probes) {
            if (used >= limit) {
                break;
            }
            if (runProbe(probe, topK, variant, pass, true)) {
                used++;
            }
        }
    }

    private void collectSyntheticProbes(int limit, int topK, EmbeddingVariant variant, Pass pass) {
        if (limit <= 0) {
            return;
        }
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT id, COALESCE(title, name, '') AS title, file_key, file_path, url, "
                        + "COALESCE(NULLIF(btrim(company), ''), ?) AS company "
                        + "FROM images WHERE COALESCE(deleted, false) = false "
                        + "AND COALESCE(file_key, file_path, url, '') <> '' ORDER BY id LIMIT ?",
                properties.getDefaultCompany(), limit));
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            String id = text(row.get("id"));
            String key = ImageSearchFilters.resolveStorageKey(text(row.get("file_key")), text(row.get("file_path")), text(row.get("url")));
            if (id.isBlank() || key.isBlank()) {
                continue;
            }
            byte[] original;
            try {
                original = worker.readBytes(key);
            } catch (Exception e) {
                pass.skipped.add(id + " 原图读取失败");
                continue;
            }
            String vectorId = ImageSearchFilters.libraryVectorId(id);
            String company = text(row.get("company"));
            runTransformed(vectorId, company, original, true, topK, variant, pass);
            runTransformed(vectorId, company, original, false, topK, variant, pass);
        }
    }

    private void runTransformed(String vectorId, String company, byte[] original, boolean scale, int topK,
                                EmbeddingVariant variant, Pass pass) {
        try {
            byte[] changed = scale ? ImageTransforms.scaleHalf(original) : ImageTransforms.centerCrop(original);
            long started = System.nanoTime();
            float[] embedding = embedder.embedImage(changed, scale ? "scaled.jpg" : "crop.jpg", variant == EmbeddingVariant.CROP);
            long searchStarted = System.nanoTime();
            List<ImageSearchModels.RawHit> hits = queryService.searchRaw(embedding, "all", company, topK, variant);
            pass.searchMs.add((System.nanoTime() - searchStarted) / 1_000_000L);
            pass.endToEndMs.add((System.nanoTime() - started) / 1_000_000L);
            pass.imageRelevant.add(List.of(vectorId));
            pass.imageRetrieved.add(idsOf(hits));
        } catch (Exception e) {
            pass.skipped.add(vectorId + (scale ? " 缩放" : " 裁剪") + "失败");
        }
    }

    private boolean runProbe(Probe probe, int topK, EmbeddingVariant variant, Pass pass, boolean productMetric) {
        try {
            byte[] bytes = worker.readBytes(probe.ossKey);
            long started = System.nanoTime();
            float[] embedding = embedder.embedImage(
                    bytes, ImageSearchFilters.fileNameOf(probe.ossKey), variant == EmbeddingVariant.CROP);
            long searchStarted = System.nanoTime();
            int fetch = Math.max(topK, new SameProductStrategy().internalTopK(topK, properties));
            List<ImageSearchModels.RawHit> hits = queryService.searchRaw(embedding, "all", probe.company, fetch, variant);
            pass.searchMs.add((System.nanoTime() - searchStarted) / 1_000_000L);
            pass.endToEndMs.add((System.nanoTime() - started) / 1_000_000L);
            pass.imageRelevant.add(probe.relevantIds);
            pass.imageRetrieved.add(idsOf(hits));
            if (productMetric && probe.productKey != null && !probe.productKey.isBlank()) {
                pass.productRelevant.add(List.of(probe.productKey));
                pass.productRetrieved.add(SameProductGrouping.rankedProductKeys(
                        hits, probe.excludeVectorId, topK,
                        properties.getSameProductMultiBonus(), properties.getSameProductMultiBonusCap()));
            }
            return true;
        } catch (Exception e) {
            pass.skipped.add(probe.queryId + " 探针失败");
            return false;
        }
    }

    private void addProductProbes(List<Probe> probes, int limit) {
        List<Map<String, Object>> groups = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT product_id FROM images WHERE COALESCE(deleted, false) = false "
                        + "AND product_id IS NOT NULL AND btrim(product_id) <> '' "
                        + "GROUP BY product_id HAVING COUNT(*) >= 2 ORDER BY product_id LIMIT ?",
                limit));
        if (groups == null) {
            return;
        }
        for (Map<String, Object> group : groups) {
            String productId = text(group.get("product_id"));
            List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                    "SELECT id, file_key, file_path, url, COALESCE(product_id, '') AS product_id, "
                            + "COALESCE(NULLIF(btrim(company), ''), ?) AS company "
                            + "FROM images WHERE COALESCE(deleted, false) = false AND product_id = ? ORDER BY id",
                    properties.getDefaultCompany(), productId));
            addRowsAsProbe(probes, rows, "product:" + productId);
        }
    }

    private void addSlotProbes(List<Probe> probes, int limit) {
        List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT id, COALESCE(goods_no, '') AS goods_no, main_image_key, side_image_key, "
                        + "detail_image_key, product_image_key FROM goods_library ORDER BY id LIMIT 500"));
        if (rows == null) {
            return;
        }
        int added = 0;
        for (Map<String, Object> row : rows) {
            if (added >= limit) {
                break;
            }
            long id = ((Number) row.get("id")).longValue();
            List<Slot> slots = presentSlots(id, row);
            if (slots.size() < 2) {
                continue;
            }
            List<String> others = new ArrayList<>();
            for (int i = 1; i < slots.size(); i++) {
                others.add(slots.get(i).vectorId);
            }
            probes.add(styleProbe(slots.get(0).vectorId, slots.get(0).key, properties.getDefaultCompany(), others,
                    ImageVectorRecord.goods(id, "main", properties.getDefaultCompany(), slots.get(0).key,
                            text(row.get("goods_no")), "", properties.getDefaultCompany()),
                    slots.get(0).vectorId));
            added++;
        }
    }

    private void addGoodsNoProbes(List<Probe> probes, int limit) {
        List<Map<String, Object>> groups = txTemplate.execute(status -> jdbcTemplate.queryForList(
                "SELECT btrim(goods_no) AS goods_no FROM goods_library "
                        + "WHERE goods_no IS NOT NULL AND btrim(goods_no) <> '' "
                        + "GROUP BY btrim(goods_no) HAVING COUNT(*) >= 2 ORDER BY btrim(goods_no) LIMIT ?",
                limit));
        if (groups == null) {
            return;
        }
        for (Map<String, Object> group : groups) {
            String goodsNo = text(group.get("goods_no"));
            List<Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(
                    "SELECT id, main_image_key, side_image_key, detail_image_key, product_image_key "
                            + "FROM goods_library WHERE btrim(goods_no) = ? ORDER BY id",
                    goodsNo));
            if (rows == null) {
                continue;
            }
            List<Slot> slots = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                slots.addAll(presentSlots(((Number) row.get("id")).longValue(), row));
            }
            if (slots.size() < 2) {
                continue;
            }
            List<String> others = new ArrayList<>();
            for (int i = 1; i < slots.size(); i++) {
                others.add(slots.get(i).vectorId);
            }
            probes.add(styleProbe(slots.get(0).vectorId, slots.get(0).key, properties.getDefaultCompany(), others,
                    ImageVectorRecord.goods(slotsOwner(slots.get(0).vectorId), "main", properties.getDefaultCompany(),
                            slots.get(0).key, goodsNo, "", properties.getDefaultCompany()),
                    slots.get(0).vectorId));
        }
    }

    private static long slotsOwner(String vectorId) {
        String[] parts = vectorId == null ? new String[0] : vectorId.split(":");
        if (parts.length >= 2) {
            try {
                return Long.parseLong(parts[1]);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private void addRowsAsProbe(List<Probe> probes, List<Map<String, Object>> rows, String label) {
        if (rows == null || rows.size() < 2) {
            return;
        }
        List<String> ids = new ArrayList<>();
        String key = "";
        String company = properties.getDefaultCompany();
        String queryId = "";
        String productId = "";
        for (Map<String, Object> row : rows) {
            String id = text(row.get("id"));
            String resolved = ImageSearchFilters.resolveStorageKey(
                    text(row.get("file_key")), text(row.get("file_path")), text(row.get("url")));
            if (id.isBlank() || resolved.isBlank()) {
                continue;
            }
            ids.add(ImageSearchFilters.libraryVectorId(id));
            if (key.isBlank()) {
                key = resolved;
                company = text(row.get("company"));
                queryId = id;
                productId = text(row.get("product_id"));
            }
        }
        if (ids.size() < 2 || key.isBlank()) {
            return;
        }
        List<String> relevant = new ArrayList<>(ids);
        relevant.remove(0);
        ImageVectorRecord sample = ImageVectorRecord.library(
                queryId, company, key, "", productId, properties.getDefaultCompany());
        probes.add(styleProbe(label + ":" + queryId, key, company, relevant, sample, sample.vectorId()));
    }

    private static Probe styleProbe(String queryId, String ossKey, String company, List<String> relevantIds,
                                    ImageVectorRecord sample, String excludeVectorId) {
        return new Probe(queryId, ossKey, company, relevantIds, SameProductGrouping.productKey(sample), excludeVectorId);
    }

    private List<Slot> presentSlots(long goodsId, Map<String, Object> row) {
        List<Slot> slots = new ArrayList<>();
        addSlot(slots, goodsId, "main", text(row.get("main_image_key")));
        addSlot(slots, goodsId, "side", text(row.get("side_image_key")));
        addSlot(slots, goodsId, "detail", text(row.get("detail_image_key")));
        addSlot(slots, goodsId, "product", text(row.get("product_image_key")));
        return slots;
    }

    private static void addSlot(List<Slot> slots, long goodsId, String slot, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        slots.add(new Slot(ImageSearchFilters.goodsVectorId(goodsId, slot), key));
    }

    private static List<String> idsOf(List<ImageSearchModels.RawHit> hits) {
        List<String> ids = new ArrayList<>();
        for (ImageSearchModels.RawHit hit : hits) {
            ids.add(hit.record().vectorId());
        }
        return ids;
    }

    private static Map<String, Object> latency(List<Long> samples) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (samples.isEmpty()) {
            map.put("count", 0);
            map.put("avg", 0);
            map.put("p50", 0);
            map.put("p95", 0);
            return map;
        }
        long sum = 0L;
        for (Long sample : samples) {
            sum += sample;
        }
        map.put("count", samples.size());
        map.put("avg", sum / samples.size());
        map.put("p50", ImageSearchRecall.percentile(samples, 50));
        map.put("p95", ImageSearchRecall.percentile(samples, 95));
        return map;
    }

    private static double round(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    static int argInt(String[] args, String name, int fallback) {
        if (args == null) {
            return fallback;
        }
        for (int i = 0; i < args.length; i++) {
            if (name.equals(args[i]) && i + 1 < args.length) {
                return Integer.parseInt(args[i + 1]);
            }
        }
        return fallback;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private record Probe(String queryId, String ossKey, String company, List<String> relevantIds,
                         String productKey, String excludeVectorId) {
    }

    private static final class Pass {
        private final List<List<String>> imageRelevant = new ArrayList<>();
        private final List<List<String>> imageRetrieved = new ArrayList<>();
        private final List<List<String>> productRelevant = new ArrayList<>();
        private final List<List<String>> productRetrieved = new ArrayList<>();
        private final List<Long> searchMs = new ArrayList<>();
        private final List<Long> endToEndMs = new ArrayList<>();
        private final List<String> skipped = new ArrayList<>();
        private int styleQueries;
    }

    private record Slot(String vectorId, String key) {
    }
}
