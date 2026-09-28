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

    public ImageSearchBackfillService(ImageSearchProperties properties,
                                      MilvusImageVectorIndex vectorIndex,
                                      ImageSearchCatalog catalog,
                                      ImageVectorProgressStore progressStore,
                                      ImageSearchIndexWorker worker) {
        this.properties = properties;
        this.vectorIndex = vectorIndex;
        this.catalog = catalog;
        this.progressStore = progressStore;
        this.worker = worker;
    }

    public int runAndPrint(String[] args) {
        ImageSearchBackfillEngine.Options options = parse(args);
        String source = parseSource(args);
        try {
            ImageSearchBackfillEngine.Summary summary = execute(source, options);
            System.out.println("以图搜图回填完成");
            System.out.println("扫描 " + summary.scanned + "，写入 " + summary.indexed
                    + "，跳过 " + summary.skipped + "，失败 " + summary.failed);
            for (String error : summary.errors) {
                System.out.println("失败: " + error);
            }
            if (!summary.errors.isEmpty() && summary.failed > summary.errors.size()) {
                System.out.println("其余失败已写入 image_vector_index，可用 --only-failed 再跑");
            }
            return summary.failed > 0 ? 2 : 0;
        } catch (Exception e) {
            System.err.println("回填没有跑起来: " + e.getMessage());
            log.error("图片向量回填失败", e);
            return 1;
        }
    }

    public ImageSearchBackfillEngine.Summary execute(String source, ImageSearchBackfillEngine.Options options) {
        properties.requireEnabled();
        ImageSearchFilters.assertCollectionName(properties.getCollection());
        vectorIndex.initIfEnabled();
        if (!vectorIndex.isReady()) {
            throw new IllegalStateException("图片向量库未就绪，请确认 Milvus 和 image-search.collection");
        }
        ImageSearchBackfillEngine.Summary summary = new ImageSearchBackfillEngine.Summary();
        ImageSearchBackfillEngine.Sink sink = new ImageSearchBackfillEngine.Sink() {
            @Override
            public Optional<ImageSearchBackfillEngine.Progress> find(String vectorId) {
                return progressStore.find(vectorId);
            }

            @Override
            public void markSkipped(ImageVectorRecord record, String reason) {
                progressStore.save(record, "SKIPPED", null, reason);
            }

            @Override
            public void index(ImageVectorRecord record) throws Exception {
                worker.indexForBackfill(record);
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
        log.info("图片向量回填 source={} scanned={} indexed={} skipped={} failed={}",
                normalized, summary.scanned, summary.indexed, summary.skipped, summary.failed);
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
}
