package com.imagemanager.imagesearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 可重复执行的回填决策：已成功且对象键没变就跳过，失败记下来继续下一条。
 */
public final class ImageSearchBackfillEngine {

    public enum Decision {
        INDEX,
        SKIP_DONE,
        SKIP_NO_KEY
    }

    public record Progress(String status, String ossKey) {
    }

    public record Options(boolean force, boolean onlyFailed, int limit) {
        public Options {
            if (limit < 0) {
                limit = 0;
            }
        }
    }

    public static final class Summary {
        public int scanned;
        public int indexed;
        public int skipped;
        public int failed;
        public final List<String> errors = new ArrayList<>();

        public boolean limitReached(int limit) {
            return limit > 0 && (indexed + failed) >= limit;
        }

        public void addError(String message) {
            if (errors.size() < 50 && message != null && !message.isBlank()) {
                errors.add(message.length() > 300 ? message.substring(0, 300) : message);
            }
        }
    }

    public interface Sink {
        Optional<Progress> find(String vectorId);

        /** 没有对象键时记 SKIPPED，便于下次直接跳过。 */
        void markSkipped(ImageVectorRecord record, String reason);

        /** 成功记 DONE，失败记 FAILED 后再把异常抛出来。 */
        void index(ImageVectorRecord record) throws Exception;
    }

    private ImageSearchBackfillEngine() {
    }

    public static Decision decide(ImageVectorRecord record, Progress existing, Options options) {
        boolean noKey = record.ossKey() == null || record.ossKey().isBlank();
        if (noKey) {
            if (existing != null && "SKIPPED".equals(existing.status())) {
                return Decision.SKIP_DONE;
            }
            return Decision.SKIP_NO_KEY;
        }
        if (options.force()) {
            return Decision.INDEX;
        }
        if (existing != null && "DONE".equals(existing.status()) && record.ossKey().equals(existing.ossKey())) {
            return Decision.SKIP_DONE;
        }
        if (options.onlyFailed()) {
            if (existing != null && "FAILED".equals(existing.status())) {
                return Decision.INDEX;
            }
            return Decision.SKIP_DONE;
        }
        return Decision.INDEX;
    }

    public static void run(List<ImageVectorRecord> records, Options options, Sink sink, Summary summary) {
        for (ImageVectorRecord record : records) {
            if (summary.limitReached(options.limit())) {
                return;
            }
            summary.scanned++;
            Progress existing = sink.find(record.vectorId()).orElse(null);
            Decision decision = decide(record, existing, options);
            switch (decision) {
                case SKIP_DONE -> summary.skipped++;
                case SKIP_NO_KEY -> {
                    sink.markSkipped(record, "没有可读取的图片地址");
                    summary.skipped++;
                }
                case INDEX -> {
                    try {
                        sink.index(record);
                        summary.indexed++;
                    } catch (Exception e) {
                        summary.failed++;
                        summary.addError(record.vectorId() + ": " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
                    }
                }
            }
        }
    }
}
