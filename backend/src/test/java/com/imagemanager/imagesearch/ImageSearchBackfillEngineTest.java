package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSearchBackfillEngineTest {

    @Test
    void skipsDoneRetriesFailureAndContinues() {
        ImageVectorRecord done = ImageVectorRecord.library("done", "宝娜斯集团", "oss/done.jpg", "完成", "", "宝娜斯集团");
        ImageVectorRecord failed = ImageVectorRecord.library("failed", "宝娜斯集团", "oss/bad.jpg", "失败", "", "宝娜斯集团");
        ImageVectorRecord fresh = ImageVectorRecord.library("fresh", "宝娜斯集团", "oss/new.jpg", "新", "", "宝娜斯集团");
        ImageVectorRecord empty = ImageVectorRecord.library("empty", "宝娜斯集团", "", "空", "", "宝娜斯集团");

        Map<String, ImageSearchBackfillEngine.Progress> progress = new HashMap<>();
        progress.put(done.vectorId(), new ImageSearchBackfillEngine.Progress("DONE", done.ossKey()));
        progress.put(failed.vectorId(), new ImageSearchBackfillEngine.Progress("FAILED", failed.ossKey()));
        List<String> indexed = new ArrayList<>();

        ImageSearchBackfillEngine.Sink sink = sink(progress, indexed, failed.vectorId());
        ImageSearchBackfillEngine.Summary summary = new ImageSearchBackfillEngine.Summary();
        ImageSearchBackfillEngine.run(List.of(done, failed, fresh, empty),
                new ImageSearchBackfillEngine.Options(false, false, 0), sink, summary);

        assertEquals(1, summary.indexed);
        assertEquals(2, summary.skipped);
        assertEquals(1, summary.failed);
        assertEquals(List.of(fresh.vectorId()), indexed);
        assertEquals("FAILED", progress.get(failed.vectorId()).status());
        assertEquals("SKIPPED", progress.get(empty.vectorId()).status());
        assertTrue(summary.errors.get(0).contains(failed.vectorId()));

        indexed.clear();
        summary = new ImageSearchBackfillEngine.Summary();
        ImageSearchBackfillEngine.run(List.of(empty),
                new ImageSearchBackfillEngine.Options(false, false, 0), sink, summary);
        assertEquals(1, summary.skipped);
        assertEquals(0, summary.indexed);
    }

    @Test
    void onlyFailedDoesNotReindexFinishedRows() {
        ImageVectorRecord done = ImageVectorRecord.library("done", "宝娜斯集团", "oss/done.jpg", "完成", "", "宝娜斯集团");
        ImageVectorRecord failed = ImageVectorRecord.library("failed", "宝娜斯集团", "oss/bad.jpg", "失败", "", "宝娜斯集团");
        Map<String, ImageSearchBackfillEngine.Progress> progress = new HashMap<>();
        progress.put(done.vectorId(), new ImageSearchBackfillEngine.Progress("DONE", done.ossKey()));
        progress.put(failed.vectorId(), new ImageSearchBackfillEngine.Progress("FAILED", failed.ossKey()));
        List<String> indexed = new ArrayList<>();
        ImageSearchBackfillEngine.Summary summary = new ImageSearchBackfillEngine.Summary();
        ImageSearchBackfillEngine.run(List.of(done, failed),
                new ImageSearchBackfillEngine.Options(false, true, 0),
                sink(progress, indexed, ""), summary);
        assertEquals(List.of(failed.vectorId()), indexed);
        assertEquals(1, summary.skipped);
        assertEquals("DONE", progress.get(failed.vectorId()).status());
    }

    @Test
    void limitCountsAttemptsNotSkips() {
        ImageVectorRecord done = ImageVectorRecord.library("done", "宝娜斯集团", "a.jpg", "完成", "", "宝娜斯集团");
        ImageVectorRecord a = ImageVectorRecord.library("a", "宝娜斯集团", "b.jpg", "甲", "", "宝娜斯集团");
        ImageVectorRecord b = ImageVectorRecord.library("b", "宝娜斯集团", "c.jpg", "乙", "", "宝娜斯集团");
        Map<String, ImageSearchBackfillEngine.Progress> progress = new HashMap<>();
        progress.put(done.vectorId(), new ImageSearchBackfillEngine.Progress("DONE", done.ossKey()));
        List<String> indexed = new ArrayList<>();
        ImageSearchBackfillEngine.Summary summary = new ImageSearchBackfillEngine.Summary();
        ImageSearchBackfillEngine.run(List.of(done, a, b),
                new ImageSearchBackfillEngine.Options(false, false, 1),
                sink(progress, indexed, ""), summary);
        assertEquals(List.of(a.vectorId()), indexed);
        assertEquals(1, summary.indexed);
    }

    private static ImageSearchBackfillEngine.Sink sink(Map<String, ImageSearchBackfillEngine.Progress> progress,
                                                       List<String> indexed, String failId) {
        return new ImageSearchBackfillEngine.Sink() {
            @Override
            public Optional<ImageSearchBackfillEngine.Progress> find(String vectorId) {
                return Optional.ofNullable(progress.get(vectorId));
            }

            @Override
            public void markSkipped(ImageVectorRecord record, String reason) {
                progress.put(record.vectorId(), new ImageSearchBackfillEngine.Progress("SKIPPED", record.ossKey()));
            }

            @Override
            public void index(ImageVectorRecord record) {
                if (record.vectorId().equals(failId)) {
                    progress.put(record.vectorId(), new ImageSearchBackfillEngine.Progress("FAILED", record.ossKey()));
                    throw new IllegalStateException("oss 读取失败");
                }
                indexed.add(record.vectorId());
                progress.put(record.vectorId(), new ImageSearchBackfillEngine.Progress("DONE", record.ossKey()));
            }
        };
    }
}
