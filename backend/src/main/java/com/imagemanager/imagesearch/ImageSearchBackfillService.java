package com.imagemanager.imagesearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 从数据库和对象存储回填图片向量。可重复执行，已完成且对象键未变的会跳过。
 */
@Slf4j
@Service
public class ImageSearchBackfillService {

    private final ImageSearchProperties properties;
    private final MilvusImageVectorIndex vectorIndex;
    private final ImageSearchCatalog catalog;
    private final ImageVectorProgressStore progressStore;
    private final ImageSearchIndexWorker worker;
    private final ImageEmbedder embedder;

    public ImageSearchBackfillService(ImageSearchProperties properties,
                                      MilvusImageVectorIndex vectorIndex,
                                      ImageSearchCatalog catalog,
                                      ImageVectorProgressStore progressStore,
                                      ImageSearchIndexWorker worker,
                                      ImageEmbedder embedder) {
        this.properties = properties;
        this.vectorIndex = vectorIndex;
        this.catalog = catalog;
        this.progressStore = progressStore;
        this.worker = worker;
        this.embedder = embedder;
    }

    public int runAndPrint(String[] args) {
        ImageSearchBackfillEngine.Options options = parse(args);
        String source = parseSource(args);
        String variantArg = parseVariant(args);
        try {
            int code = 0;
            if ("all".equals(variantArg) || "full".equals(variantArg)) {
                code = Math.max(code, printSummary(execute(source, options, EmbeddingVariant.FULL), "full"));
            }
            if ("all".equals(variantArg) || "crop".equals(variantArg)) {
                code = Math.max(code, printSummary(execute(source, options, EmbeddingVariant.CROP), "crop"));
            }
            return code;
        } catch (Exception e) {
            System.err.println("回填没有跑起来: " + e.getMessage());
            log.error("图片向量回填失败", e);
            return 1;
        }
    }

    private int printSummary(ImageSearchBackfillEngine.Summary summary, String variant) {
        System.out.println("以图搜图回填完成（" + variant + "）");
        System.out.println("扫描 " + summary.scanned + "，写入 " + summary.indexed
                + "，跳过 " + summary.skipped + "，失败 " + summary.failed);
        for (String error : summary.errors) {
            System.out.println("失败: " + error);
        }
        if (!summary.errors.isEmpty() && summary.failed > summary.errors.size()) {
            System.out.println("其余失败已写入 image_vector_index，可用 --only-failed --variant " + variant + " 再跑");
        }
        return summary.failed > 0 ? 2 : 0;
    }

    public ImageSearchBackfillEngine.Summary execute(String source, ImageSearchBackfillEngine.Options options) {
        return execute(source, options, EmbeddingVariant.FULL);
    }

    public ImageSearchBackfillEngine.Summary execute(String source, ImageSearchBackfillEngine.Options options,
                                                     EmbeddingVariant variant) {
        EmbeddingVariant actual = variant == null ? EmbeddingVariant.FULL : variant;
        properties.requireEnabled();
        ImageSearchFilters.assertCollectionName(properties.getCollection());
        vectorIndex.initIfEnabled();
        if (!vectorIndex.isReady()) {
            throw new IllegalStateException("图片向量库未就绪，请确认 Milvus 和 image-search.collection");
        }
        if (actual == EmbeddingVariant.CROP) {
            ImageSearchFilters.assertCollectionName(properties.resolvedCropCollection());
            if (!properties.isCropEnabled() || !vectorIndex.supports(EmbeddingVariant.CROP)) {
                throw new IllegalStateException(
                        "裁剪集合未就绪。请设置 IMAGE_SEARCH_CROP_ENABLED=true，确认集合 "
                                + properties.resolvedCropCollection() + " 可写，并且不要改动 image_vectors_vitl");
            }
            if (!embedder.cropDetectorReady()) {
                throw new IllegalStateException(
                        "向量服务没有打开主体裁剪。请在 image-embed-service\\start.ps1 加上 IMAGE_EMBED_CROP=1 后重启，"
                                + "curl http://127.0.0.1:8002/health 里 crop_detector_ready 应为 true");
            }
        }
        ImageSearchBackfillEngine.Summary summary = new ImageSearchBackfillEngine.Summary();
        ImageSearchBackfillEngine.Sink sink = new ImageSearchBackfillEngine.Sink() {
            @Override
            public Optional<ImageSearchBackfillEngine.Progress> find(String vectorId) {
                return progressStore.find(vectorId, actual);
            }

            @Override
            public void markSkipped(ImageVectorRecord record, String reason) {
                progressStore.save(record, "SKIPPED", null, reason, actual);
            }

            @Override
            public void index(ImageVectorRecord record) throws Exception {
                worker.indexForBackfill(record, actual);
            }
        };
        String normalized = source == null ? "all" : source.trim().toLowerCase(Locale.ROOT);
        if ("all".equals(normalized) || "library".equals(normalized)) {
            String cursor = "";
            while (!summary.limitReached(options.limit())) {
                List<ImageVectorRecord> page = catalog.libraryPage(cursor, 200);
                if (page.isEmpty()) {
                    break;
                }
                cursor = page.get(page.size() - 1).sourceId();
                ImageSearchBackfillEngine.run(page, options, sink, summary);
            }
        }
        if (("all".equals(normalized) || "goods".equals(normalized)) && !summary.limitReached(options.limit())) {
            long cursor = 0L;
            while (!summary.limitReached(options.limit())) {
                ImageSearchCatalog.GoodsChunk chunk = catalog.goodsChunk(cursor, 100);
                if (chunk.done()) {
                    break;
                }
                cursor = chunk.lastId();
                ImageSearchBackfillEngine.run(chunk.records(), options, sink, summary);
            }
        }
        log.info("图片向量回填 variant={} source={} scanned={} indexed={} skipped={} failed={}",
                actual.name().toLowerCase(Locale.ROOT), normalized, summary.scanned, summary.indexed, summary.skipped, summary.failed);
        return summary;
    }

    static ImageSearchBackfillEngine.Options parse(String[] args) {
        boolean force = false;
        boolean onlyFailed = false;
        int limit = 0;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--force".equals(arg)) {
                    force = true;
                } else if ("--only-failed".equals(arg)) {
                    onlyFailed = true;
                } else if ("--limit".equals(arg) && i + 1 < args.length) {
                    limit = Integer.parseInt(args[++i]);
                }
            }
        }
        return new ImageSearchBackfillEngine.Options(force, onlyFailed, limit);
    }

    static String parseSource(String[] args) {
        if (args == null) {
            return "all";
        }
        for (int i = 0; i < args.length; i++) {
            if ("--source".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return "all";
    }

    /** full、crop 或 all。默认只回填整图集合，避免把已经跑过的整图再编码一遍。 */
    static String parseVariant(String[] args) {
        if (args == null) {
            return "full";
        }
        for (int i = 0; i < args.length; i++) {
            if ("--variant".equals(args[i]) && i + 1 < args.length) {
                String value = args[i + 1] == null ? "" : args[i + 1].trim().toLowerCase(Locale.ROOT);
                if ("full".equals(value) || "crop".equals(value) || "all".equals(value)) {
                    return value;
                }
                throw new IllegalArgumentException("--variant 只能是 full、crop 或 all");
            }
        }
        return "full";
    }
}
