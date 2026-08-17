package com.imagemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.util.Enumeration;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.github.universalchardet.UniversalDetector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PreDestroy;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 知识批量导入服务（200G 级，流式处理，直写 Milvus）
 *
 * 设计要点：
 * 1. 流式遍历：ZipFile 逐条目读取中央目录，绝不把整个 zip 或全部文件加载进堆内存
 * 2. 单文件先落临时文件（磁盘缓冲，不占堆），解析完即删
 * 3. 三级流水线：生产者(读zip/目录) -> 解析池(解析+切片+向量化) -> 写入线程(攒批写Milvus)
 * 4. 背压控制：有界缓冲队列 + in-flight 信号量，解析慢时自动阻塞生产者，内存恒定
 * 5. 幂等：doc_id = 文件内容 SHA-256 前缀，重导前先按 doc_id 删除旧向量
 * 6. 在线查询隔离：导入线程池独立，30-40 人搜索不受影响
 */
@Slf4j
@Service
public class KnowledgeImportService {

    private final JdbcTemplate jdbcTemplate;
    private final MilvusService milvusService;
    private final DocumentParserService documentParserService;
    private final ObjectMapper objectMapper;

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.embedding-model:bge-m3}")
    private String embeddingModel;

    @Value("${app.ollama.vision-model:qwen3.6:35b}")
    private String visionModel;

    @Value("${knowledge-import.parse-threads:2}")
    private int parseThreads;

    @Value("${knowledge-import.embed-batch-size:32}")
    private int embedBatchSize;

    @Value("${knowledge-import.milvus-batch-size:512}")
    private int milvusBatchSize;

    @Value("${knowledge-import.buffer-capacity:2048}")
    private int bufferCapacity;

    @Value("${knowledge-import.max-inflight-files:16}")
    private int maxInflightFiles;

    @Value("${knowledge-import.ocr-enabled:true}")
    private boolean ocrEnabled;

    @Value("${knowledge-import.ocr-concurrency:4}")
    private int ocrConcurrency;

    @Value("${knowledge-import.chunk-size:800}")
    private int chunkSize;

    @Value("${knowledge-import.chunk-overlap:100}")
    private int chunkOverlap;

    @Value("${knowledge-import.max-file-size-mb:500}")
    private long maxFileSizeMb;

    @Value("${knowledge-import.max-concurrent-tasks:1}")
    private int maxConcurrentTasks;

    /** 任务执行池（后台异步，不占用 HTTP 线程） */
    private final ExecutorService taskExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "knowledge-import-task");
        t.setDaemon(true);
        return t;
    });

    /** 写入线程池（单线程串行写 Milvus，保证批次顺序与速率稳定） */
    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "milvus-writer");
        t.setDaemon(true);
        return t;
    });

    /** OCR 并发信号量（多模态模型重负载，严格限流） */
    private Semaphore ocrSemaphore;

    /** 运行中任务进度（内存态，接口直读） */
    private final ConcurrentHashMap<Long, ImportTaskProgress> progressMap = new ConcurrentHashMap<>();

    /** 运行中任务上下文（用于取消） */
    private final ConcurrentHashMap<Long, ImportContext> runningContexts = new ConcurrentHashMap<>();

    /** 并发导入限制信号量 */
    private Semaphore taskSemaphore;

    public KnowledgeImportService(JdbcTemplate jdbcTemplate,
                                  MilvusService milvusService,
                                  DocumentParserService documentParserService,
                                  ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.milvusService = milvusService;
        this.documentParserService = documentParserService;
        this.objectMapper = objectMapper;
    }

    private synchronized Semaphore ocrSemaphore() {
        if (ocrSemaphore == null) {
            ocrSemaphore = new Semaphore(Math.max(1, ocrConcurrency));
        }
        return ocrSemaphore;
    }

    private synchronized Semaphore taskSemaphore() {
        if (taskSemaphore == null) {
            taskSemaphore = new Semaphore(Math.max(1, maxConcurrentTasks));
        }
        return taskSemaphore;
    }

    // ========== 进度对象 ==========

    public static class ImportTaskProgress {
        public Long taskId;
        public String source;
        public volatile String status = "RUNNING"; // RUNNING/COMPLETED/FAILED/CANCELLED
        public volatile int totalFiles;
        public volatile int processedFiles;
        public volatile int failedFiles;
        public volatile long totalChunks;
        public volatile long startTime;
        public volatile long endTime;
        public final List<String> recentErrors = Collections.synchronizedList(new LinkedList<>());

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("taskId", taskId);
            m.put("source", source);
            m.put("status", status);
            m.put("totalFiles", totalFiles);
            m.put("processedFiles", processedFiles);
            m.put("failedFiles", failedFiles);
            m.put("totalChunks", totalChunks);
            m.put("elapsedSeconds", (endTime > 0 ? endTime : System.currentTimeMillis()) - startTime > 0
                    ? ((endTime > 0 ? endTime : System.currentTimeMillis()) - startTime) / 1000 : 0);
            synchronized (recentErrors) {
                m.put("recentErrors", new ArrayList<>(recentErrors));
            }
            return m;
        }
    }

    /** 导入任务上下文（流水线共享状态） */
    private static class ImportContext {
        final long taskId;
        final String userId;
        final ImportTaskProgress progress;
        final BlockingQueue<JsonObject> buffer;
        final AtomicLong chunkCounter = new AtomicLong();
        final AtomicInteger processedCounter = new AtomicInteger();
        final AtomicInteger failCounter = new AtomicInteger();
        final AtomicInteger sinceLastPersist = new AtomicInteger();
        final List<Path> tempDirs = Collections.synchronizedList(new ArrayList<>());
        volatile boolean cancelled = false;
        volatile boolean producerDone = false;
        Throwable writerError = null;

        ImportContext(long taskId, String userId, ImportTaskProgress progress, int bufferCapacity) {
            this.taskId = taskId;
            this.userId = userId;
            this.progress = progress;
            this.buffer = new ArrayBlockingQueue<>(bufferCapacity);
        }
    }

    // ========== 提交导入 ==========

    /**
     * 按服务器路径导入（zip 压缩包或文件夹）
     */
    public Map<String, Object> submitPath(String path, String userId) {
        Path root = Paths.get(path);
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("路径不存在: " + path);
        }
        if (!Files.isReadable(root)) {
            throw new IllegalArgumentException("路径不可读: " + path);
        }
        if (!tryAcquireTaskSlot()) {
            throw new IllegalStateException("已有导入任务在运行，请等待完成后再提交（max-concurrent-tasks=" + maxConcurrentTasks + "）");
        }
        return startTask(root, path, userId);
    }

    /**
     * 上传 zip 文件导入（保存到系统临时目录后流式处理）
     */
    public Map<String, Object> submitUpload(MultipartFile file, String userId) throws IOException {
        Path tempZip = Files.createTempFile("knowledge-import-", ".zip");
        file.transferTo(tempZip);
        if (!tryAcquireTaskSlot()) {
            Files.deleteIfExists(tempZip);
            throw new IllegalStateException("已有导入任务在运行，请等待完成后再提交");
        }
        return startTask(tempZip, file.getOriginalFilename() != null ? file.getOriginalFilename() : tempZip.getFileName().toString(), userId);
    }

    private boolean tryAcquireTaskSlot() {
        return taskSemaphore().tryAcquire();
    }

    private Map<String, Object> startTask(Path root, String sourceName, String userId) {
        // 创建任务记录（knowledge_import_task 表没有 user_id 字段）
        Long taskId = jdbcTemplate.queryForObject(
                "INSERT INTO knowledge_import_task (source) VALUES (?) RETURNING id",
                Long.class, sourceName);

        ImportTaskProgress progress = new ImportTaskProgress();
        progress.taskId = taskId;
        progress.source = sourceName;
        progress.startTime = System.currentTimeMillis();
        progressMap.put(taskId, progress);

        taskExecutor.submit(() -> {
            try {
                runImport(root, taskId, userId);
            } finally {
                taskSemaphore().release();
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", taskId);
        result.put("status", "RUNNING");
        result.put("message", "导入任务已提交，后台流式处理中");
        return result;
    }

    // ========== 核心导入流程 ==========

    private void runImport(Path root, long taskId, String userId) {
        ImportTaskProgress progress = progressMap.get(taskId);
        ImportContext ctx = new ImportContext(taskId, userId, progress, bufferCapacity);
        runningContexts.put(taskId, ctx);

        ExecutorService parsePool = Executors.newFixedThreadPool(Math.max(1, parseThreads), r -> {
            Thread t = new Thread(r, "knowledge-parse-" + taskId);
            t.setDaemon(true);
            return t;
        });

        long start = System.currentTimeMillis();
        try {
            if (!milvusService.isEnabled()) {
                throw new IllegalStateException("Milvus 未连接，无法导入");
            }

            // 启动 Milvus 写入线程
            Future<?> writerFuture = writerExecutor.submit(() -> writerLoop(ctx));

            // 生产者：流式遍历 zip / 目录
            if (Files.isDirectory(root)) {
                walkDirectory(root, ctx, parsePool);
            } else {
                walkZip(root, "", ctx, parsePool, 0);
            }

            // 等待所有解析任务完成
            parsePool.shutdown();
            boolean parseDone = parsePool.awaitTermination(7, TimeUnit.DAYS);
            if (!parseDone) {
                log.warn("导入任务 {} 解析线程池超时未结束", taskId);
            }

            ctx.producerDone = true;
            writerFuture.get(1, TimeUnit.DAYS);

            if (ctx.writerError != null) {
                throw new RuntimeException("Milvus 写入失败: " + ctx.writerError.getMessage(), ctx.writerError);
            }

            milvusService.flush();

            String finalStatus = ctx.cancelled ? "CANCELLED" : "COMPLETED";
            progress.status = finalStatus;
            progress.endTime = System.currentTimeMillis();
            persistTask(progress, finalStatus, null);
            log.info("导入任务 {} 完成: 文件 {}/{}, 失败 {}, 切片 {}, 耗时 {}s",
                    taskId, progress.processedFiles, progress.totalFiles,
                    progress.failedFiles, progress.totalChunks,
                    (System.currentTimeMillis() - start) / 1000);
        } catch (Exception e) {
            log.error("导入任务 {} 失败: {}", taskId, e.getMessage(), e);
            ctx.cancelled = true;
            progress.status = "FAILED";
            progress.endTime = System.currentTimeMillis();
            progress.recentErrors.add("任务失败: " + e.getMessage());
            persistTask(progress, "FAILED", e.getMessage());
        } finally {
            parsePool.shutdownNow();
            // 等待仍在运行的解析任务退出，避免清理临时文件时它们还在读
            try { parsePool.awaitTermination(60, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            cleanTempDirs(ctx);
            runningContexts.remove(taskId);
        }
    }

    /**
     * 清理任务期间登记的所有临时目录（在解析池完全停止后调用）
     */
    private void cleanTempDirs(ImportContext ctx) {
        for (Path dir : ctx.tempDirs) {
            try (var leftovers = Files.list(dir)) {
                leftovers.forEach(f -> {
                    try { Files.deleteIfExists(f); } catch (IOException ignored) {}
                });
            } catch (IOException ignored) {}
            try { Files.deleteIfExists(dir); } catch (IOException ignored) {}
        }
        ctx.tempDirs.clear();
    }

    /**
     * 流式遍历目录（Files.walk 惰性流）
     */
    private void walkDirectory(Path root, ImportContext ctx, ExecutorService parsePool) throws Exception {
        Semaphore inflight = new Semaphore(maxInflightFiles);
        List<String> supportedExts = supportedExtensions();

        try (var paths = Files.walk(root)) {
            Iterator<Path> it = paths.iterator();
            while (it.hasNext()) {
                if (ctx.cancelled) return;
                Path p = it.next();
                if (Files.isDirectory(p)) continue;

                String name = p.getFileName().toString();
                String ext = extOf(name);
                if (!supportedExts.contains(ext)) continue;

                ctx.progress.totalFiles++;
                if (Files.size(p) > maxFileSizeMb * 1024L * 1024L) {
                    ctx.progress.failedFiles = ctx.failCounter.incrementAndGet();
                    recordError(ctx, name, "文件超过 " + maxFileSizeMb + "MB 限制");
                    continue;
                }

                inflight.acquire();
                if (ctx.cancelled) { inflight.release(); return; }
                String hash = sha256Of(p);
                submitParseJob(p, name, ext, hash, ctx, parsePool, inflight, false);
            }
        }
    }

    /**
     * 遍历 zip（ZipFile 随机访问模式，读取中央目录，兼容 ZIP64 / 中文文件名 / 嵌套 zip）
     *
     * 注意：不能用 ZipArchiveInputStream —— 它无法处理 Windows 工具压缩的
     * "stored entry 使用 data descriptor" 格式（compressed/uncompressed size 校验失败）。
     * 本项目所有 zip 来源（服务器路径/上传临时文件/嵌套临时文件）均为本地磁盘文件，
     * 可随机访问，使用 ZipFile 是官方推荐做法。
     */
    private void walkZip(Path zipFile, String prefix, ImportContext ctx,
                         ExecutorService parsePool, int depth) throws Exception {
        if (depth > 3) {
            log.warn("zip 嵌套超过 3 层，跳过: {}", zipFile);
            return;
        }
        Semaphore inflight = new Semaphore(maxInflightFiles);
        List<String> supportedExts = supportedExtensions();
        Path tempDir = Files.createTempDirectory("kimport-");
        ctx.tempDirs.add(tempDir);
        byte[] buf = new byte[128 * 1024];

        try (ZipFile zf = openZipFile(zipFile)) {
            Enumeration<ZipArchiveEntry> entries = zf.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (ctx.cancelled) return;
                if (entry.isDirectory()) continue;

                String entryName = entry.getName();
                String ext = extOf(entryName);

                // 嵌套 zip：拷出后递归处理
                if ("zip".equals(ext)) {
                    if (entry.getSize() > maxFileSizeMb * 1024L * 1024L) {
                        recordError(ctx, prefix + entryName, "嵌套zip超过大小限制");
                        continue;
                    }
                    Path nested = Files.createTempFile(tempDir, "nested-", ".zip");
                    try (InputStream is = zf.getInputStream(entry)) {
                        copyStream(is, nested, buf);
                    }
                    ctx.progress.totalFiles++; // zip 自身计一个文件
                    try {
                        walkZip(nested, prefix + entryName + "/", ctx, parsePool, depth + 1);
                        ctx.progress.processedFiles = ctx.processedCounter.incrementAndGet();
                    } finally {
                        Files.deleteIfExists(nested);
                    }
                    continue;
                }

                if (!supportedExts.contains(ext)) continue;

                if (entry.getSize() > maxFileSizeMb * 1024L * 1024L) {
                    ctx.progress.totalFiles++;
                    ctx.progress.failedFiles = ctx.failCounter.incrementAndGet();
                    recordError(ctx, prefix + entryName, "文件超过 " + maxFileSizeMb + "MB 限制");
                    continue;
                }

                ctx.progress.totalFiles++;
                inflight.acquire();
                if (ctx.cancelled) { inflight.release(); return; }

                // 条目 -> 临时文件（磁盘缓冲，不占堆内存）
                Path tempFile = Files.createTempFile(tempDir, "f-", "." + ext);
                String hash;
                try (InputStream is = zf.getInputStream(entry)) {
                    hash = copyStreamWithHash(is, tempFile, buf);
                }

                try {
                    submitParseJob(tempFile, prefix + entryName, ext, hash, ctx, parsePool, inflight, true);
                } catch (RejectedExecutionException e) {
                    inflight.release();
                    Files.deleteIfExists(tempFile);
                    throw e;
                }
            }
        }
        // 临时目录不在这里清理：解析任务在 parsePool 异步运行，仍需读取临时文件。
        // 统一登记到 ctx.tempDirs，待整个导入任务结束（解析池 shutdown 后）再清理。
    }

    /**
     * 打开 zip：UTF-8 优先（Mac/Linux 标准 + EFS 标志自动生效），解码失败回退 GBK（Windows 中文压缩包）
     */
    private ZipFile openZipFile(Path zipPath) throws IOException {
        ZipFile zf = null;
        try {
            zf = ZipFile.builder()
                    .setFile(zipPath.toFile())
                    .setCharset(StandardCharsets.UTF_8)
                    .setUseUnicodeExtraFields(true)
                    .get();
            zf.getEntries(); // 强制解析中央目录（文件名解码失败会在此抛出，触发回退）
            return zf;
        } catch (Exception e) {
            log.debug("zip 以 UTF-8 打开失败，回退 GBK: {}", e.getMessage());
            if (zf != null) {
                try { zf.close(); } catch (IOException ignored) {}
            }
            return ZipFile.builder()
                    .setFile(zipPath.toFile())
                    .setCharset(Charset.forName("GBK"))
                    .setUseUnicodeExtraFields(true)
                    .get();
        }
    }

    /**
     * 提交单文件解析任务到解析池（解析 -> 切片 -> 批量向量化 -> 入缓冲队列）
     *
     * @param deleteAfter 解析完成后是否删除文件（zip 临时文件删，目录原文件不删）
     */
    private void submitParseJob(Path file, String virtualName, String ext, String docId,
                                ImportContext ctx, ExecutorService parsePool, Semaphore inflight,
                                boolean deleteAfter) {
        parsePool.submit(() -> {
            try {
                if (ctx.cancelled) return;
                parseAndEmbedFile(file, virtualName, ext, docId, ctx);
            } catch (Exception e) {
                int failed = ctx.failCounter.incrementAndGet();
                ctx.progress.failedFiles = failed;
                String errorMsg = e.getMessage();
                if (errorMsg == null || errorMsg.isBlank()) {
                    errorMsg = e.getClass().getSimpleName() + ": " + e.toString();
                }
                recordError(ctx, virtualName, errorMsg);
                log.warn("文件解析失败: {} -> {}", virtualName, errorMsg, e);
            } finally {
                if (deleteAfter) {
                    try { Files.deleteIfExists(file); } catch (IOException ignored) {}
                }
                inflight.release();
            }
        });
    }

    // ========== 单文件解析 + 向量化 ==========

    private void parseAndEmbedFile(Path file, String virtualName, String ext,
                                   String docId, ImportContext ctx) throws Exception {
        // 幂等：重导前删除旧向量
        milvusService.deleteByDocId(docId);

        String text;
        String docType = ext;
        switch (ext) {
            case "pdf" -> text = parsePdf(file);
            case "doc", "docx" -> text = parseWord(file);
            case "xls", "xlsx" -> text = parseExcel(file);
            case "csv", "txt", "text", "md" -> text = parseTextFile(file);
            case "jpg", "jpeg", "png", "gif", "webp", "bmp" -> {
                if (!ocrEnabled) {
                    ctx.processedCounter.incrementAndGet();
                    ctx.progress.processedFiles = ctx.processedCounter.get();
                    persistDocMeta(ctx, virtualName, docId, "image", 0, null, "SKIPPED");
                    return;
                }
                text = ocrImage(file);
                docType = "image";
            }
            default -> text = null;
        }

        if (text == null || text.isBlank()) {
            ctx.processedCounter.incrementAndGet();
            ctx.progress.processedFiles = ctx.processedCounter.get();
            persistDocMeta(ctx, virtualName, docId, docType, 0, null, "SKIPPED");
            return;
        }

        List<String> chunks = documentParserService.chunkText(text, chunkSize, chunkOverlap);
        if (chunks.isEmpty()) {
            ctx.processedCounter.incrementAndGet();
            ctx.progress.processedFiles = ctx.processedCounter.get();
            persistDocMeta(ctx, virtualName, docId, docType, 0, null, "SKIPPED");
            return;
        }

        String fileName = virtualName.length() > 1000 ? virtualName.substring(0, 1000) : virtualName;

        // 按 embedBatchSize 分批向量化（Ollama 批量接口，一次 HTTP 处理多个 chunk）
        for (int i = 0; i < chunks.size(); i += embedBatchSize) {
            if (ctx.cancelled) return;
            List<String> batch = chunks.subList(i, Math.min(i + embedBatchSize, chunks.size()));
            float[][] embeddings = getEmbeddings(batch);
            if (embeddings == null || embeddings.length != batch.size()) {
                throw new RuntimeException("向量化返回数量不匹配: 期望 " + batch.size()
                        + " 实际 " + (embeddings == null ? 0 : embeddings.length));
            }
            for (int j = 0; j < batch.size(); j++) {
                String content = batch.get(j);
                if (content.length() > 8000) content = content.substring(0, 8000);
                JsonObject row = milvusService.buildRow(docId, fileName, docType,
                        i + j, content, embeddings[j]);
                // 有界队列 put：写满自动阻塞 = 天然背压
                ctx.buffer.put(row);
            }
        }

        int processed = ctx.processedCounter.incrementAndGet();
        ctx.progress.processedFiles = processed;
        persistDocMeta(ctx, virtualName, docId, docType, chunks.size(), text, "COMPLETED");
        maybePersistProgress(ctx);
    }

    /**
     * 导入文档元数据同步写入 knowledge_base_docs（知识库列表可见向量化状态）。
     * docId（32位hex）格式化为 UUID 作为主键，重导时幂等更新。
     */
    private void persistDocMeta(ImportContext ctx, String virtualName, String docId,
                                String docType, int chunkCount, String fullText, String status) {
        try {
            String uuid = docId.replaceAll("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
            String title = virtualName.contains("/")
                    ? virtualName.substring(virtualName.lastIndexOf('/') + 1)
                    : virtualName;
            if (title.length() > 250) title = title.substring(0, 250);
            String content = fullText == null ? null
                    : (fullText.length() > 50000 ? fullText.substring(0, 50000) : fullText);
            String name1000 = virtualName.length() > 1000 ? virtualName.substring(0, 1000) : virtualName;
            jdbcTemplate.update("""
                    INSERT INTO knowledge_base_docs
                        (id, user_id, category_id, title, file_name, file_type, file_size,
                         file_path, content, status, tags, chunk_count, embedding_status,
                         file_content, created_at, updated_at)
                    VALUES (CAST(? AS uuid), ?, NULL, ?, ?, ?, 0, 'bulk-import', NULL, 'COMPLETED',
                            'bulk-import', ?, ?, ?, NOW(), NOW())
                    ON CONFLICT (id) DO UPDATE SET
                        chunk_count = EXCLUDED.chunk_count,
                        embedding_status = EXCLUDED.embedding_status,
                        file_content = EXCLUDED.file_content,
                        title = EXCLUDED.title,
                        file_name = EXCLUDED.file_name,
                        updated_at = NOW()
                    """,
                    uuid, ctx.userId, title, name1000, docType, chunkCount, status, content);
        } catch (Exception ex) {
            log.warn("文档元数据写入失败: {} -> {}", virtualName, ex.getMessage());
        }
    }

    // ========== 文件解析器（临时文件模式，堆内存可控） ==========

    private String parsePdf(Path file) throws Exception {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setSuppressDuplicateOverlappingText(true);
            try {
                String text = stripper.getText(document);
                return text != null ? text : "";
            } catch (Exception fontError) {
                return extractPdfPageByPage(document);
            }
        }
    }

    private String extractPdfPageByPage(PDDocument document) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            try {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setStartPage(i + 1);
                stripper.setEndPage(i + 1);
                String pageText = stripper.getText(document);
                if (pageText != null && !pageText.isBlank()) {
                    sb.append(pageText).append("\n");
                }
            } catch (Exception e) {
                sb.append("[第").append(i + 1).append("页无法解析]\n");
            }
        }
        return sb.toString();
    }

    private String parseWord(Path file) throws Exception {
        // 老格式 .doc 用 HWPF（poi-scratchpad），.docx 用 XWPF
        if (file.getFileName().toString().toLowerCase().endsWith(".doc")) {
            try (InputStream is = new BufferedInputStream(Files.newInputStream(file));
                 WordExtractor extractor = new WordExtractor(is)) {
                String text = extractor.getText();
                return text != null ? text : "";
            }
        }
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file));
             XWPFDocument doc = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            return sb.toString();
        }
    }

    /**
     * Excel 解析：「表头: 值」键值对格式保留列对应关系；
     * 合并单元格向下填充 + 首列同值延续，保证每行数据带完整上下文（检索友好）
     */
    private String parseExcel(Path file) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator;
            try {
                evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            } catch (Exception e) {
                evaluator = null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet == null) continue;
                try {
                    appendSheetText(sb, sheet, formatter, evaluator);
                } catch (Exception e) {
                    sb.append("[工作表解析失败: ").append(e.getMessage()).append("]\n");
                }
            }
            return sb.toString();
        }
    }

    private void appendSheetText(StringBuilder sb, Sheet sheet,
                                 DataFormatter formatter, FormulaEvaluator evaluator) {
        sb.append("【工作表: ").append(sheet.getSheetName()).append("】\n");

        // 合并单元格区域列表：区域内的空格子视为首格值的延续
        List<CellRangeAddress> merged = new ArrayList<>();
        try {
            for (int m = 0; m < sheet.getNumMergedRegions(); m++) {
                merged.add(sheet.getMergedRegion(m));
            }
        } catch (Exception ignore) {
            // 部分损坏文件合并区域读取失败，忽略后按普通表格处理
        }

        int firstRow = sheet.getFirstRowNum();
        Row headerRow = sheet.getRow(firstRow);
        List<String> headers = new ArrayList<>();
        int headerLen = 0;
        if (headerRow != null) {
            headerLen = Math.max(0, headerRow.getLastCellNum());
            for (int c = 0; c < headerLen; c++) {
                headers.add(cellText(formatter, evaluator, headerRow.getCell(c)));
            }
        }

        boolean hasHeader = !headers.isEmpty() && headers.stream().anyMatch(h -> !h.isEmpty());

        if (!hasHeader) {
            // 无表头：整行管道分隔
            for (Row row : sheet) {
                if (row == null) continue;
                List<String> cells = new ArrayList<>();
                int len = Math.max(0, row.getLastCellNum());
                for (int c = 0; c < len; c++) {
                    cells.add(cellText(formatter, evaluator, row.getCell(c)));
                }
                if (cells.stream().anyMatch(s -> !s.isEmpty())) {
                    sb.append(String.join(" | ", cells)).append("\n");
                }
            }
        } else {
            // 有表头：键值对 + 合并单元格填充 + 首列延续
            String[] carry = new String[headers.size()];
            for (int r = firstRow + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                List<String> parts = new ArrayList<>();
                boolean hasData = false;
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header.isEmpty()) continue;
                    String value = cellText(formatter, evaluator, row.getCell(c));
                    if (value.isEmpty()) {
                        // 合并单元格：区域内空格子取区域首格值
                        String mergedVal = mergedValueAt(merged, sheet, formatter, evaluator, r, c);
                        if (mergedVal != null) value = mergedVal;
                        // 首列额外延续（部分表不用合并单元格，留空表示同上）
                        if (value.isEmpty() && c == 0 && carry[c] != null) value = carry[c];
                    } else {
                        carry[c] = value;
                    }
                    if (!value.isEmpty()) {
                        parts.add(header + ": " + value);
                        hasData = true;
                    }
                }
                if (hasData) {
                    sb.append(String.join("；", parts)).append("\n");
                }
            }
        }
        sb.append("\n");
    }

    /** 取 (row, col) 所在合并区域的首格值；不在任何区域内返回 null */
    private String mergedValueAt(List<CellRangeAddress> merged, Sheet sheet,
                                 DataFormatter formatter, FormulaEvaluator evaluator,
                                 int rowIdx, int colIdx) {
        for (CellRangeAddress region : merged) {
            if (region.isInRange(rowIdx, colIdx)) {
                Row firstRow = sheet.getRow(region.getFirstRow());
                if (firstRow == null) return null;
                return cellText(formatter, evaluator, firstRow.getCell(region.getFirstColumn()));
            }
        }
        return null;
    }

    /** DataFormatter 统一取值：覆盖 STRING/NUMERIC/BOOLEAN/FORMULA（含缓存值），彻底避免 NPE */
    private String cellText(DataFormatter formatter, FormulaEvaluator evaluator, Cell cell) {
        if (cell == null) return "";
        try {
            String v = formatter.formatCellValue(cell, evaluator);
            return v == null ? "" : v.trim();
        } catch (Exception e) {
            try {
                String v = formatter.formatCellValue(cell);
                return v == null ? "" : v.trim();
            } catch (Exception e2) {
                return "";
            }
        }
    }

    private String parseTextFile(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > 50 * 1024 * 1024) {
            // 超大文本流式按行读取
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Files.newInputStream(file), detectCharset(file)))) {
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) > 0) {
                    sb.append(buf, 0, n);
                    if (sb.length() > 20_000_000) break; // 2000万字符上限保护
                }
                return sb.toString();
            }
        }
        return new String(bytes, detectCharset(file));
    }

    private String detectCharset(Path file) throws IOException {
        try (InputStream is = Files.newInputStream(file)) {
            UniversalDetector detector = new UniversalDetector(null);
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0 && !detector.isDone()) {
                detector.handleData(buf, 0, n);
            }
            detector.dataEnd();
            String charset = detector.getDetectedCharset();
            return charset != null ? charset : "UTF-8";
        }
    }

    // ========== 图片 OCR（qwen3.6:35b 多模态，信号量限流） ==========

    private String ocrImage(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        String base64 = Base64.getEncoder().encodeToString(bytes);

        Map<String, Object> body = new HashMap<>();
        body.put("model", visionModel);
        body.put("stream", false);
        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", "请提取图片中的所有文字内容，按原文排版输出，不要解释。如果没有文字，描述图片中的关键信息。");
        msg.put("images", List.of(base64));
        body.put("messages", List.of(msg));

        ocrSemaphore().acquire();
        try {
            String resp = postJson(ollamaBaseUrl + "/api/chat",
                    objectMapper.writeValueAsString(body), 180_000);
            JsonNode root = objectMapper.readTree(resp);
            if (root.has("message") && root.get("message").has("content")) {
                return root.get("message").get("content").asText();
            }
            return null;
        } finally {
            ocrSemaphore().release();
        }
    }

    // ========== Ollama 批量向量化 ==========

    /**
     * 批量获取向量（Ollama /api/embed input 支持数组，一次 HTTP 处理一批）
     */
    private float[][] getEmbeddings(List<String> texts) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", embeddingModel);
        body.put("input", texts);

        String resp = postJson(ollamaBaseUrl + "/api/embed",
                objectMapper.writeValueAsString(body), 120_000);
        JsonNode root = objectMapper.readTree(resp);
        if (!root.has("embeddings") || !root.get("embeddings").isArray()) {
            log.error("批量embedding返回异常: {}", resp.length() > 500 ? resp.substring(0, 500) : resp);
            return null;
        }
        JsonNode arr = root.get("embeddings");
        float[][] out = new float[arr.size()][];
        for (int i = 0; i < arr.size(); i++) {
            JsonNode vec = arr.get(i);
            float[] f = new float[vec.size()];
            for (int j = 0; j < vec.size(); j++) {
                f[j] = (float) vec.get(j).asDouble();
            }
            out[i] = f;
        }
        return out;
    }

    private String postJson(String urlStr, String jsonBody, int timeoutMs) throws Exception {
        URI uri = URI.create(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(timeoutMs);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            if (code < 200 || code >= 300) {
                throw new RuntimeException("Ollama HTTP " + code + ": "
                        + (sb.length() > 300 ? sb.substring(0, 300) : sb));
            }
            return sb.toString();
        }
    }

    // ========== Milvus 写入线程（攒批 + 串行写入） ==========

    private void writerLoop(ImportContext ctx) {
        List<JsonObject> pending = new ArrayList<>(milvusBatchSize);
        try {
            while (!ctx.cancelled) {
                JsonObject row = ctx.buffer.poll(1, TimeUnit.SECONDS);
                if (row != null) {
                    pending.add(row);
                    if (pending.size() >= milvusBatchSize) {
                        milvusService.batchInsert(pending);
                        ctx.chunkCounter.addAndGet(pending.size());
                        ctx.progress.totalChunks = ctx.chunkCounter.get();
                        pending.clear();
                    }
                } else if (ctx.producerDone && ctx.buffer.isEmpty()) {
                    break;
                }
            }
            // 收尾：写入剩余
            if (!pending.isEmpty() && !ctx.cancelled) {
                milvusService.batchInsert(pending);
                ctx.chunkCounter.addAndGet(pending.size());
                ctx.progress.totalChunks = ctx.chunkCounter.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            ctx.writerError = e;
            ctx.cancelled = true; // 通知生产者/解析线程停止
            ctx.buffer.clear();   // 清空缓冲，解除解析线程的 put 阻塞
            log.error("Milvus 写入线程异常: {}", e.getMessage(), e);
        }
    }

    // ========== 工具方法 ==========

    private List<String> supportedExtensions() {
        return List.of("pdf", "doc", "docx", "xls", "xlsx", "csv", "txt", "text", "md",
                "jpg", "jpeg", "png", "gif", "webp", "bmp");
    }

    private String extOf(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return "";
        return name.substring(idx + 1).toLowerCase();
    }

    /** 拷贝流到文件，同时计算 SHA-256（返回前 32 位作为 docId） */
    private String copyStreamWithHash(InputStream in, Path target, byte[] buf) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(target), 256 * 1024)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                digest.update(buf, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.substring(0, 32);
    }

    private void copyStream(InputStream in, Path target, byte[] buf) throws Exception {
        try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(target), 256 * 1024)) {
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
    }

    private String sha256Of(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file), 256 * 1024)) {
            byte[] buf = new byte[128 * 1024];
            int n;
            while ((n = is.read(buf)) > 0) {
                digest.update(buf, 0, n);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.substring(0, 32);
    }

    private void recordError(ImportContext ctx, String fileName, String error) {
        // 确保错误信息不为空
        String msg = error;
        if (msg == null || msg.isBlank()) {
            msg = "未知错误（请查看后端日志）";
        }
        if (msg.length() > 500) {
            msg = msg.substring(0, 500) + "...";
        }
        ctx.progress.recentErrors.add(fileName + " -> " + msg);
        synchronized (ctx.progress.recentErrors) {
            while (ctx.progress.recentErrors.size() > 100) {
                ctx.progress.recentErrors.remove(0);
            }
        }
        try {
            jdbcTemplate.update("INSERT INTO knowledge_import_error (task_id, file_name, error_msg) VALUES (?,?,?)",
                    ctx.taskId, fileName, msg);
        } catch (Exception e) {
            log.warn("记录导入错误失败: {}", e.getMessage());
        }
    }

    /** 每处理 50 个文件持久化一次进度（断电也能看到中间状态） */
    private void maybePersistProgress(ImportContext ctx) {
        if (ctx.sinceLastPersist.incrementAndGet() >= 50) {
            ctx.sinceLastPersist.set(0);
            persistTask(ctx.progress, "RUNNING", null);
        }
    }

    private void persistTask(ImportTaskProgress p, String status, String errorMsg) {
        try {
            jdbcTemplate.update(
                    "UPDATE knowledge_import_task SET status=?, total_files=?, processed_files=?, failed_files=?, total_chunks=?, error_msg=?, finished_at=CASE WHEN ? IN ('COMPLETED','FAILED','CANCELLED') THEN NOW() ELSE finished_at END WHERE id=?",
                    status, p.totalFiles, p.processedFiles, p.failedFiles, p.totalChunks, errorMsg, status, p.taskId);
        } catch (Exception e) {
            log.warn("持久化导入任务状态失败: {}", e.getMessage());
        }
    }

    // ========== 进度查询 / 取消 ==========

    public Map<String, Object> getProgress(long taskId) {
        ImportTaskProgress p = progressMap.get(taskId);
        if (p != null) {
            return p.toMap();
        }
        // 已重启或不在内存中，查 PG
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM knowledge_import_task WHERE id = ?", taskId);
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
        } catch (Exception ignored) {}
        throw new IllegalArgumentException("任务不存在: " + taskId);
    }

    public Map<String, Object> cancel(long taskId) {
        ImportContext ctx = runningContexts.get(taskId);
        if (ctx == null) {
            throw new IllegalArgumentException("任务不在运行中: " + taskId);
        }
        ctx.cancelled = true;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", taskId);
        m.put("message", "取消信号已发送，任务将在当前批次完成后停止");
        return m;
    }

    public List<Map<String, Object>> listTasks(int limit) {
        return jdbcTemplate.queryForList(
                "SELECT * FROM knowledge_import_task ORDER BY id DESC LIMIT ?", Math.max(1, limit));
    }

    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdownNow();
        writerExecutor.shutdownNow();
    }
}
