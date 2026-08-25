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
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hwpf.usermodel.Range;
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

    /** 三张 PG 表的实际列集合（启动时加载，写入按列存在动态适配） */
    private volatile Set<String> taskTableCols = Set.of();
    private volatile Set<String> errorTableCols = Set.of();
    private volatile Set<String> docsTableCols = Set.of();

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
        // POI HWPF 对部分 .doc 的内部告警走 JUL 且带完整堆栈（无文件名、刷屏），抑制到 SEVERE；
        // 真正的解析失败由本服务捕获并带文件名记录
        java.util.logging.Logger.getLogger("org.apache.poi").setLevel(java.util.logging.Level.SEVERE);
        // 启动时幂等 DDL 自愈：无表建表、有表补列（手建表结构不确定也能适配）
        ensureSchema();
        // 启动时缓存三张 PG 表的实际列集合：导入写入全部按"列存在才写"动态适配，
        // 避免手建表缺列/多列导致整个导入链路静默失败
        this.taskTableCols = loadColumns("knowledge_import_task");
        this.errorTableCols = loadColumns("knowledge_import_error");
        this.docsTableCols = loadColumns("knowledge_base_docs");
        verifyTable("knowledge_import_task", taskTableCols,
                "id", "source", "status", "total_files", "processed_files", "failed_files", "total_chunks");
        verifyTable("knowledge_import_error", errorTableCols, "task_id", "file_name", "error_msg");
        if (docsTableCols.isEmpty()) {
            log.error("[导入] 表 knowledge_base_docs 不存在或不可查，导入文档将无法在知识库列表展示");
        } else if (!docsTableCols.contains("id") || !docsTableCols.contains("title")) {
            log.error("[导入] knowledge_base_docs 缺少核心列 id/title，实际列: {}", docsTableCols);
        }
    }

    /**
     * 启动时幂等 DDL 自愈：
     * 1. knowledge_import_task / knowledge_import_error 不存在则创建（标准结构）
     * 2. 已存在（手建）则 ALTER ADD COLUMN IF NOT EXISTS 补齐缺失列
     * 3. knowledge_base_docs 补 doc_id 业务列 + 索引（一个文档只一行，按内容哈希去重）
     * 每条 DDL 独立 try/catch，单条失败不影响启动与其他语句
     */
    private void ensureSchema() {
        List<String> ddls = List.of(
                "CREATE TABLE IF NOT EXISTS public.knowledge_import_task (" +
                        "id BIGSERIAL PRIMARY KEY, source VARCHAR(512), status VARCHAR(32) DEFAULT 'RUNNING', " +
                        "total_files INT DEFAULT 0, processed_files INT DEFAULT 0, failed_files INT DEFAULT 0, " +
                        "skipped_files INT DEFAULT 0, total_chunks INT DEFAULT 0, error_msg TEXT, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, finished_at TIMESTAMP NULL)",
                "CREATE TABLE IF NOT EXISTS public.knowledge_import_error (" +
                        "id BIGSERIAL PRIMARY KEY, task_id BIGINT NOT NULL, file_name TEXT, error_msg TEXT, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                // 手建表补列（CREATE TABLE IF NOT EXISTS 不会动已有表，逐列补齐）
                "ALTER TABLE public.knowledge_import_task ADD COLUMN IF NOT EXISTS source VARCHAR(512)",
                "ALTER TABLE public.knowledge_import_task ADD COLUMN IF NOT EXISTS status VARCHAR(32) DEFAULT 'RUNNING'",
                "ALTER TABLE public.knowledge_import_task ADD COLUMN IF NOT EXISTS total_files INT DEFAULT 0",
                "ALTER TABLE public.knowledge_import_task ADD COLUMN IF NOT EXISTS processed_files INT DEFAULT 0",
                "ALTER TABLE public.knowledge_import_task ADD COLUMN IF NOT EXISTS failed_files INT DEFAULT 0",
                "ALTER TABLE public.knowledge_import_task ADD COLUMN IF NOT EXISTS skipped_files INT DEFAULT 0",
                "ALTER TABLE public.knowledge_import_task ADD COLUMN IF NOT EXISTS total_chunks INT DEFAULT 0",
                "ALTER TABLE public.knowledge_import_task ADD COLUMN IF NOT EXISTS error_msg TEXT",
                "ALTER TABLE public.knowledge_import_task ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                "ALTER TABLE public.knowledge_import_task ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP NULL",
                "ALTER TABLE public.knowledge_import_error ADD COLUMN IF NOT EXISTS task_id BIGINT",
                "ALTER TABLE public.knowledge_import_error ADD COLUMN IF NOT EXISTS file_name TEXT",
                "ALTER TABLE public.knowledge_import_error ADD COLUMN IF NOT EXISTS error_msg TEXT",
                "ALTER TABLE public.knowledge_import_error ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                // knowledge_base_docs：doc_id = 文件内容 SHA-256 前32位，一个文档无论切多少片只一行
                "ALTER TABLE public.knowledge_base_docs ADD COLUMN IF NOT EXISTS doc_id VARCHAR(64)",
                "CREATE INDEX IF NOT EXISTS idx_kb_docs_doc_id ON public.knowledge_base_docs (doc_id)"
        );
        for (String ddl : ddls) {
            try {
                jdbcTemplate.execute(ddl);
            } catch (Exception e) {
                log.warn("[导入] DDL 自愈语句执行失败（已跳过）: {} -> {}",
                        ddl.length() > 80 ? ddl.substring(0, 80) + "..." : ddl, e.getMessage());
            }
        }
        log.info("[导入] DDL 自愈完成（task/error 表与 knowledge_base_docs.doc_id 已确保存在）");
    }

    /** 读取 PG 表的列名集合（统一小写） */
    private Set<String> loadColumns(String table) {
        try {
            Set<String> cols = new HashSet<>();
            for (String c : jdbcTemplate.queryForList(
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?", String.class, table)) {
                cols.add(c.toLowerCase());
            }
            log.info("[导入] 表 public.{} 实际列: {}", table, cols);
            return cols;
        } catch (Exception e) {
            log.error("[导入] 读取表 public.{} 列信息失败: {}", table, e.getMessage(), e);
            return Set.of();
        }
    }

    /** 校验必需列，缺失则打 ERROR（帮助定位手建表结构不完整问题） */
    private void verifyTable(String table, Set<String> actual, String... required) {
        if (actual.isEmpty()) {
            log.error("[导入] 表 {} 不存在或不可查！任务进度/错误明细将无法落库", table);
            return;
        }
        List<String> missing = new ArrayList<>();
        for (String r : required) {
            if (!actual.contains(r)) missing.add(r);
        }
        if (!missing.isEmpty()) {
            log.error("[导入] 表 {} 缺少列: {}（实际列: {}）", table, missing, actual);
        }
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
        public volatile int skippedFiles;
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
            m.put("skippedFiles", skippedFiles);
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
        final String company;
        final ImportTaskProgress progress;
        final BlockingQueue<JsonObject> buffer;
        final AtomicLong chunkCounter = new AtomicLong();
        final AtomicInteger processedCounter = new AtomicInteger();
        final AtomicInteger failCounter = new AtomicInteger();
        final AtomicInteger skippedCounter = new AtomicInteger();
        final AtomicInteger sinceLastPersist = new AtomicInteger();
        final List<Path> tempDirs = Collections.synchronizedList(new ArrayList<>());
        volatile boolean cancelled = false;
        volatile boolean producerDone = false;
        Throwable writerError = null;

        ImportContext(long taskId, String userId, String company, ImportTaskProgress progress, int bufferCapacity) {
            this.taskId = taskId;
            this.userId = userId;
            this.company = company;
            this.progress = progress;
            this.buffer = new ArrayBlockingQueue<>(bufferCapacity);
        }
    }

    // ========== 提交导入 ==========

    /**
     * 按服务器路径导入（zip 压缩包或文件夹）
     */
    public Map<String, Object> submitPath(String path, String userId, String company) {
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
        return startTask(root, path, userId, company);
    }

    /**
     * 上传 zip 文件导入（保存到系统临时目录后流式处理）
     */
    public Map<String, Object> submitUpload(MultipartFile file, String userId, String company) throws IOException {
        Path tempZip = Files.createTempFile("knowledge-import-", ".zip");
        file.transferTo(tempZip);
        if (!tryAcquireTaskSlot()) {
            Files.deleteIfExists(tempZip);
            throw new IllegalStateException("已有导入任务在运行，请等待完成后再提交");
        }
        return startTask(tempZip, file.getOriginalFilename() != null ? file.getOriginalFilename() : tempZip.getFileName().toString(), userId, company);
    }

    private boolean tryAcquireTaskSlot() {
        return taskSemaphore().tryAcquire();
    }

    private Map<String, Object> startTask(Path root, String sourceName, String userId, String company) {
        log.info("[导入] startTask 开始: source={}, userId={}, company={}, taskTableCols.size={}", sourceName, userId, company, taskTableCols.size());
        // 创建任务记录：首选 INSERT..RETURNING（要求 id 有 bigserial 默认值），
        // 失败（手建表无序列/列缺失）则降级手动 MAX(id)+1 显式插 id
        long taskId;
        // created_at 列存在则显式写入（手建表可能 NOT NULL 无默认值）
        String createdExpr = taskTableCols.contains("created_at") ? ", created_at" : "";
        String createdVal = taskTableCols.contains("created_at") ? ", NOW()" : "";
        try {
            Long tid = jdbcTemplate.queryForObject(
                    "INSERT INTO knowledge_import_task (source, status" + createdExpr + ") VALUES (?, 'RUNNING'"
                            + createdVal + ") RETURNING id",
                    Long.class, sourceName);
            taskId = tid != null ? tid : -1L;
        } catch (Exception e) {
            log.warn("[导入] INSERT..RETURNING 创建任务失败，降级手动生成 id: {}", e.getMessage());
            Long maxId = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(id), 0) FROM knowledge_import_task", Long.class);
            taskId = (maxId == null ? 0 : maxId) + 1;
            jdbcTemplate.update("INSERT INTO knowledge_import_task (id, source, status" + createdExpr
                            + ") VALUES (?,?,?" + createdVal + ")",
                    taskId, sourceName, "RUNNING");
        }
        if (taskId < 0) {
            throw new IllegalStateException("任务 id 生成失败");
        }

        ImportTaskProgress progress = new ImportTaskProgress();
        progress.taskId = taskId;
        progress.source = sourceName;
        progress.startTime = System.currentTimeMillis();
        progressMap.put(taskId, progress);
        // 立即落一条 RUNNING 记录：任务列表/历史页签第一时间可见，不等首个进度周期
        persistTask(progress, "RUNNING", null);

        taskExecutor.submit(() -> {
            try {
                runImport(root, taskId, userId, company);
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

    private void runImport(Path root, long taskId, String userId, String company) {
        ImportTaskProgress progress = progressMap.get(taskId);
        ImportContext ctx = new ImportContext(taskId, userId, company, progress, bufferCapacity);
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
        // 幂等：相同内容(docId)已导入且未变更则跳过
        if (milvusService.existsByDocId(docId)) {
            ctx.skippedCounter.incrementAndGet();
            ctx.progress.skippedFiles = ctx.skippedCounter.get();
            // 跳过也要保证 knowledge_base_docs 有行：否则前端知识库永远看不到该文档
            persistDocMetaSkipped(ctx, virtualName, docId, ext);
            maybePersistProgress(ctx);
            log.debug("文件未变更，跳过: {}", virtualName);
            return;
        }

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

        // 内容有更新：先清掉旧向量再写新
        milvusService.deleteByDocId(docId);

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
     * 按启动时缓存的表列集合动态拼 SQL：列存在才写，手建表缺列不会导致整体失败。
     */
    private void persistDocMeta(ImportContext ctx, String virtualName, String docId,
                                String docType, int chunkCount, String fullText, String status) {
        upsertDocMeta(ctx, virtualName, docId, docType, chunkCount, fullText, status, false);
    }

    /**
     * 跳过文件（内容未变更、向量已在 Milvus）也要保证 knowledge_base_docs 有行：
     * 无行则补建（COMPLETED）；已有行仅回填 company，且不覆盖原切片数（preserveChunkCount）
     */
    private void persistDocMetaSkipped(ImportContext ctx, String virtualName, String docId, String docType) {
        upsertDocMeta(ctx, virtualName, docId, docType, 0, null, "COMPLETED", true);
    }

    /**
     * 文档元数据落库（去重核心）：
     * 一个文档无论被 Milvus 切成多少片，knowledge_base_docs 只保留一行。
     * 判重优先级：doc_id（内容哈希，重导同一文件幂等覆盖）-> file_name 兜底。
     * 应用层 SELECT -> UPDATE/INSERT，不依赖 ON CONFLICT 与特定主键结构，兼容手建表。
     */
    private void upsertDocMeta(ImportContext ctx, String virtualName, String docId,
                               String docType, int chunkCount, String fullText, String status,
                               boolean preserveChunkCount) {
        log.info("[导入] upsertDocMeta 开始: file={}, docId={}, docType={}, chunkCount={}, status={}, docsTableCols.size={}",
                virtualName, docId, docType, chunkCount, status, docsTableCols.size());
        try {
            if (docsTableCols.isEmpty()) {
                log.error("[导入] knowledge_base_docs 列信息不可用，跳过文档元数据写入: {}", virtualName);
                return;
            }
            String title = virtualName.contains("/")
                    ? virtualName.substring(virtualName.lastIndexOf('/') + 1)
                    : virtualName;
            if (title.length() > 250) title = title.substring(0, 250);
            String content = fullText == null ? null
                    : (fullText.length() > 50000 ? fullText.substring(0, 50000) : fullText);
            String name1000 = virtualName.length() > 1000 ? virtualName.substring(0, 1000) : virtualName;

            // 1) 判重查询：优先 doc_id，其次 file_name
            Object existingId = null;
            if (docsTableCols.contains("doc_id")) {
                List<Object> ids = jdbcTemplate.query(
                        "SELECT id FROM knowledge_base_docs WHERE doc_id = ? LIMIT 1",
                        (rs, i) -> rs.getObject("id"), docId);
                if (!ids.isEmpty()) existingId = ids.get(0);
            }
            if (existingId == null && docsTableCols.contains("file_name")) {
                List<Object> ids = jdbcTemplate.query(
                        "SELECT id FROM knowledge_base_docs WHERE file_name = ? LIMIT 1",
                        (rs, i) -> rs.getObject("id"), name1000);
                if (!ids.isEmpty()) existingId = ids.get(0);
            }

            if (existingId != null) {
                // 2a) 已存在 -> UPDATE（company 只回填不覆盖；preserveChunkCount 时切片数只增不减）
                List<String> sets = new ArrayList<>();
                List<Object> args = new ArrayList<>();
                if (docsTableCols.contains("title")) { sets.add("title = ?"); args.add(title); }
                if (docsTableCols.contains("file_type")) { sets.add("file_type = ?"); args.add(docType); }
                if (docsTableCols.contains("chunk_count")) {
                    if (preserveChunkCount && chunkCount <= 0) {
                        // 跳过文件不覆盖原切片数
                    } else {
                        sets.add("chunk_count = ?"); args.add(chunkCount);
                    }
                }
                if (docsTableCols.contains("embedding_status")) { sets.add("embedding_status = ?"); args.add(status); }
                if (docsTableCols.contains("file_content") && content != null) { sets.add("file_content = ?"); args.add(content); }
                if (docsTableCols.contains("content") && content != null) {
                    sets.add("content = ?");
                    args.add(content.length() > 5000 ? content.substring(0, 5000) : content);
                }
                if (docsTableCols.contains("doc_id")) { sets.add("doc_id = ?"); args.add(docId); }
                if (docsTableCols.contains("company") && ctx.company != null) {
                    sets.add("company = COALESCE(company, ?)"); args.add(ctx.company);
                }
                if (docsTableCols.contains("updated_at")) { sets.add("updated_at = NOW()"); }
                if (!sets.isEmpty()) {
                    String sql = "UPDATE knowledge_base_docs SET " + String.join(", ", sets) + " WHERE id = ?";
                    args.add(existingId);
                    log.info("[导入] upsertDocMeta UPDATE SQL: {}", sql);
                    jdbcTemplate.update(sql, args.toArray());
                    log.info("[导入] upsertDocMeta UPDATE 成功: file={}, docId={}, existingId={}", virtualName, docId, existingId);
                }
                return;
            }

            // 2b) 不存在 -> INSERT（id 由 docId 哈希确定性转 uuid，避免依赖表默认值）
            String uuid = docId.replaceAll("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
            List<String> cols = new ArrayList<>();
            List<String> valExprs = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            cols.add("id"); valExprs.add("CAST(? AS uuid)"); args.add(uuid);
            if (docsTableCols.contains("doc_id")) { cols.add("doc_id"); valExprs.add("?"); args.add(docId); }
            if (docsTableCols.contains("user_id")) {
                // user_id 在 JPA 实体中 nullable=false，会话缺失时用 system 兜底，避免 NOT NULL 违规
                cols.add("user_id"); valExprs.add("?");
                args.add(ctx.userId != null ? ctx.userId : "system");
            }
            if (docsTableCols.contains("company") && ctx.company != null) { cols.add("company"); valExprs.add("?"); args.add(ctx.company); }
            if (docsTableCols.contains("title")) { cols.add("title"); valExprs.add("?"); args.add(title); }
            if (docsTableCols.contains("file_name")) { cols.add("file_name"); valExprs.add("?"); args.add(name1000); }
            if (docsTableCols.contains("file_type")) { cols.add("file_type"); valExprs.add("?"); args.add(docType); }
            if (docsTableCols.contains("file_path")) { cols.add("file_path"); valExprs.add("?"); args.add("bulk-import"); }
            if (docsTableCols.contains("status")) { cols.add("status"); valExprs.add("?"); args.add("COMPLETED"); }
            if (docsTableCols.contains("tags")) { cols.add("tags"); valExprs.add("?"); args.add("bulk-import"); }
            if (docsTableCols.contains("chunk_count")) { cols.add("chunk_count"); valExprs.add("?"); args.add(chunkCount); }
            if (docsTableCols.contains("embedding_status")) { cols.add("embedding_status"); valExprs.add("?"); args.add(status); }
            if (docsTableCols.contains("file_content") && content != null) { cols.add("file_content"); valExprs.add("?"); args.add(content); }
            // content 列（前端详情页展示）：用提取文本前 5000 字符
            if (docsTableCols.contains("content") && content != null) {
                cols.add("content"); valExprs.add("?");
                args.add(content.length() > 5000 ? content.substring(0, 5000) : content);
            }
            if (docsTableCols.contains("created_at")) { cols.add("created_at"); valExprs.add("NOW()"); }
            if (docsTableCols.contains("updated_at")) { cols.add("updated_at"); valExprs.add("NOW()"); }

            String sql = "INSERT INTO knowledge_base_docs (" + String.join(", ", cols) + ") VALUES ("
                    + String.join(", ", valExprs) + ")";
            log.info("[导入] upsertDocMeta INSERT SQL: {}", sql);
            jdbcTemplate.update(sql, args.toArray());
            log.info("[导入] upsertDocMeta INSERT 成功: file={}, docId={}", virtualName, docId);
        } catch (Exception ex) {
            log.error("[导入] 文档元数据写入失败: {} -> {}", virtualName, ex.getMessage(), ex);
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
            // 直接用 HWPF Range 逐段取文本，绕过 WordToTextConverter 的列表解析
            // （部分 .doc 的 listTables 为 null，converter 内部逐段 NPE 并向 JUL 刷 WARN 堆栈，日志无文件名）
            try (InputStream is = new BufferedInputStream(Files.newInputStream(file));
                 HWPFDocument doc = new HWPFDocument(is)) {
                StringBuilder sb = new StringBuilder();
                Range range = doc.getRange();
                for (int i = 0; i < range.numParagraphs(); i++) {
                    try {
                        String text = range.getParagraph(i).text();
                        if (text != null) {
                            // \u0007 = 表格单元格分隔符，\r = 段落符，统一清洗
                            text = text.replace('\u0007', ' ').replace('\r', ' ').trim();
                            if (!text.isBlank()) sb.append(text).append('\n');
                        }
                    } catch (Exception ignored) {
                        // 单段落损坏跳过，不影响其余段落
                    }
                }
                if (sb.length() > 0) return sb.toString();
            } catch (Exception e) {
                log.warn(".doc Range 提取失败，降级 getTextFromPieces: {} -> {}",
                        file.getFileName(), e.getMessage());
            }
            // 兜底：文本片段表直读（完全绕开文档模型，不做列表/表格解析）
            try (InputStream is = new BufferedInputStream(Files.newInputStream(file));
                 WordExtractor extractor = new WordExtractor(is)) {
                String text = extractor.getTextFromPieces();
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
        // .xlsx 本质是 zip：产品图/参考图等内嵌媒体与解析无关却占大头内存，
        // 先流式剥离 xl/media/* 与 xl/drawings/* 生成无图临时副本再解析（图片按要求忽略，只留文字）
        Path effective = file;
        boolean stripMedia = file.getFileName().toString().toLowerCase().endsWith(".xlsx");
        if (stripMedia) {
            Path stripped = stripXlsxMedia(file);
            if (stripped != null) effective = stripped;
        }
        Workbook workbook = null;
        try {
            try {
                workbook = WorkbookFactory.create(effective.toFile());
            } catch (Exception openError) {
                // 无图副本打不开（POI 校验严格等情况）→ 回退解析原文件
                if (effective != file) {
                    log.warn("无图副本解析失败，回退原文件: {} -> {}", file.getFileName(), openError.getMessage());
                    try { Files.deleteIfExists(effective); } catch (IOException ignored) {}
                    effective = file;
                    workbook = WorkbookFactory.create(file.toFile());
                } else {
                    throw openError;
                }
            }
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
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (Exception e) {
                    // 忽略关闭时的保存错误（内嵌媒体文件压缩比问题）
                    log.debug("Excel 关闭时保存失败（可忽略）: {}", e.getMessage());
                }
            }
            if (stripMedia && effective != file) {
                try { Files.deleteIfExists(effective); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 流式复制 xlsx（zip）并剔除 xl/media/* 与 xl/drawings/* 条目。
     * 图片型开发计划表（几十~几百 MB 产品图）剥离后 DOM 解析内存恒定，且只保留文字。
     * 失败返回 null（回退解析原文件）。
     */
    private Path stripXlsxMedia(Path file) {
        Path stripped = null;
        try {
            stripped = Files.createTempFile("xlsx-nomedia-", ".xlsx");
            try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                    new BufferedInputStream(Files.newInputStream(file), 256 * 1024));
                 java.util.zip.ZipOutputStream zout = new java.util.zip.ZipOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(stripped), 256 * 1024))) {
                byte[] buf = new byte[128 * 1024];
                java.util.zip.ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    String name = entry.getName();
                    // 剔除媒体与绘图层（图片本身），保留 sheet/styles/sharedStrings 等文本数据
                    if (name.startsWith("xl/media/") || name.startsWith("xl/drawings/")) {
                        continue;
                    }
                    zout.putNextEntry(new java.util.zip.ZipEntry(name));
                    int n;
                    while ((n = zin.read(buf)) > 0) {
                        zout.write(buf, 0, n);
                    }
                    zout.closeEntry();
                }
            }
            return stripped;
        } catch (Exception e) {
            log.warn("xlsx 媒体剥离失败，回退解析原文件: {} -> {}", file.getFileName(), e.getMessage());
            if (stripped != null) {
                try { Files.deleteIfExists(stripped); } catch (IOException ignored) {}
            }
            return null;
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
        int lastRow = sheet.getLastRowNum();

        // 智能表头识别：扫描前 10 行，选"非空单元格最多"的行为表头行。
        // 解决开发计划表第 1 行是合并大标题（如"智舒爽纱线成品开发计划 NO.1"）、
        // 第 2 行才是真表头（序号/人群/场景/系列/品类...）时固定取首行导致表头失效的问题
        int headerRowIdx = firstRow;
        int maxNonEmpty = -1;
        int scanEnd = Math.min(firstRow + 9, lastRow);
        for (int r = firstRow; r <= scanEnd; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            int nonEmpty = 0;
            int len = Math.max(0, row.getLastCellNum());
            for (int c = 0; c < len; c++) {
                if (!cellText(formatter, evaluator, row.getCell(c)).isEmpty()) nonEmpty++;
            }
            if (nonEmpty > maxNonEmpty) {
                maxNonEmpty = nonEmpty;
                headerRowIdx = r;
            }
        }

        // 表头行之前的内容（大标题、日期等）作为文档上下文保留在 sheet 开头
        for (int r = firstRow; r < headerRowIdx; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            List<String> cells = new ArrayList<>();
            int len = Math.max(0, row.getLastCellNum());
            for (int c = 0; c < len; c++) {
                String v = cellText(formatter, evaluator, row.getCell(c));
                if (!v.isEmpty()) cells.add(v);
            }
            if (!cells.isEmpty()) {
                sb.append("【").append(String.join(" | ", cells)).append("】\n");
            }
        }

        Row headerRow = sheet.getRow(headerRowIdx);
        List<String> headers = new ArrayList<>();
        int headerLen = 0;
        if (headerRow != null) {
            headerLen = Math.max(0, headerRow.getLastCellNum());
            for (int c = 0; c < headerLen; c++) {
                headers.add(cellText(formatter, evaluator, headerRow.getCell(c)));
            }
        }

        // 表头有效性：至少 2 个非空列才按键值对输出（避免大标题行被误判为表头）
        long nonEmptyHeaders = headers.stream().filter(h -> !h.isEmpty()).count();
        boolean hasHeader = nonEmptyHeaders >= 2;

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
            // 有表头：键值对 + 合并单元格填充 + 首列延续（数据从表头行下一行开始）
            String[] carry = new String[headers.size()];
            for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                List<String> parts = new ArrayList<>();
                boolean hasData = false;
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header.isEmpty()) continue;
                    String value = cellText(formatter, evaluator, row.getCell(c));
                    if (value.isEmpty()) {
                        // 合并单元格：区域内空格子取区域首格值（但首行不能是表头行）
                        String mergedVal = mergedValueAt(merged, sheet, formatter, evaluator, r, c, headerRowIdx);
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

    /** 取 (row, col) 所在合并区域的首格值；不在任何区域内或首行是表头行返回 null */
    private String mergedValueAt(List<CellRangeAddress> merged, Sheet sheet,
                                 DataFormatter formatter, FormulaEvaluator evaluator,
                                 int rowIdx, int colIdx, int headerRowIdx) {
        for (CellRangeAddress region : merged) {
            if (region.isInRange(rowIdx, colIdx)) {
                // 如果合并区域从表头行开始，不填充（避免用表头值覆盖数据行）
                if (region.getFirstRow() <= headerRowIdx) {
                    return null;
                }
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
        log.info("[导入] recordError 开始: taskId={}, fileName={}, errorTableCols.size={}", ctx.taskId, fileName, errorTableCols.size());
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
            if (!errorTableCols.contains("task_id") || !errorTableCols.contains("error_msg")) {
                log.error("[导入] knowledge_import_error 缺少必需列 task_id/error_msg，跳过落库: {} -> {} (实际列: {})", fileName, msg, errorTableCols);
                return;
            }
            String name2048 = fileName != null && fileName.length() > 2000 ? fileName.substring(0, 2000) : fileName;
            boolean hasFileName = errorTableCols.contains("file_name");
            // created_at 列存在则显式写入（手建表可能 NOT NULL 无默认值）
            String createdExpr = errorTableCols.contains("created_at") ? ", created_at" : "";
            String createdVal = errorTableCols.contains("created_at") ? ", NOW()" : "";
            if (hasFileName) {
                jdbcTemplate.update("INSERT INTO knowledge_import_error (task_id, file_name, error_msg"
                                + createdExpr + ") VALUES (?,?,?" + createdVal + ")",
                        ctx.taskId, name2048, msg);
            } else {
                jdbcTemplate.update("INSERT INTO knowledge_import_error (task_id, error_msg"
                                + createdExpr + ") VALUES (?,?" + createdVal + ")",
                        ctx.taskId, msg);
            }
        } catch (Exception e) {
            log.error("记录导入错误失败: {}", e.getMessage(), e);
        }
    }

    /** 每处理 10 个文件持久化一次进度（前端轮询更快看到中间状态，断电可追） */
    private void maybePersistProgress(ImportContext ctx) {
        if (ctx.sinceLastPersist.incrementAndGet() >= 10) {
            ctx.sinceLastPersist.set(0);
            persistTask(ctx.progress, "RUNNING", null);
        }
    }

    private void persistTask(ImportTaskProgress p, String status, String errorMsg) {
        log.info("[导入] persistTask 开始: taskId={}, status={}, taskTableCols.size={}", p.taskId, status, taskTableCols.size());
        if (taskTableCols.isEmpty()) {
            log.error("[导入] knowledge_import_task 列信息不可用，跳过进度持久化 taskId={}", p.taskId);
            return;
        }
        try {
            List<String> sets = new ArrayList<>();
            List<Object> args = new ArrayList<>();
            sets.add("status = ?"); args.add(status);
            if (taskTableCols.contains("total_files")) { sets.add("total_files = ?"); args.add(p.totalFiles); }
            if (taskTableCols.contains("processed_files")) { sets.add("processed_files = ?"); args.add(p.processedFiles); }
            if (taskTableCols.contains("failed_files")) { sets.add("failed_files = ?"); args.add(p.failedFiles); }
            if (taskTableCols.contains("total_chunks")) { sets.add("total_chunks = ?"); args.add(p.totalChunks); }
            if (taskTableCols.contains("skipped_files")) { sets.add("skipped_files = ?"); args.add(p.skippedFiles); }
            if (taskTableCols.contains("error_msg")) { sets.add("error_msg = ?"); args.add(errorMsg); }
            if (taskTableCols.contains("finished_at")
                    && ("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status))) {
                sets.add("finished_at = NOW()");
            }
            String sql = "UPDATE knowledge_import_task SET " + String.join(", ", sets) + " WHERE id = ?";
            args.add(p.taskId);
            jdbcTemplate.update(sql, args.toArray());
        } catch (Exception e) {
            log.error("持久化导入任务状态失败 taskId={} status={}: {}", p.taskId, status, e.getMessage(), e);
        }
    }

    // ========== 进度查询 / 取消 ==========

    public Map<String, Object> getProgress(long taskId) {
        ImportTaskProgress p = progressMap.get(taskId);
        if (p != null) {
            return p.toMap();
        }
        // 已重启或不在内存中，查 PG（DB 列为 snake_case，转 camelCase 与内存 toMap 对齐）
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM knowledge_import_task WHERE id = ?", taskId);
            if (!rows.isEmpty()) {
                return camelRow(rows.get(0));
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
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM knowledge_import_task ORDER BY id DESC LIMIT ?", Math.max(1, limit));
            List<Map<String, Object>> out = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                out.add(camelRow(row));
            }
            return out;
        } catch (Exception e) {
            log.error("查询导入任务列表失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /** DB snake_case 行转 camelCase（与 ImportTaskProgress.toMap 键名对齐，供前端直接使用） */
    private Map<String, Object> camelRow(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> {
            StringBuilder sb = new StringBuilder();
            for (String part : k.toLowerCase().split("_")) {
                if (part.isEmpty()) continue;
                if (sb.length() == 0) sb.append(part);
                else sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
            out.put(sb.toString(), v);
        });
        return out;
    }

    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdownNow();
        writerExecutor.shutdownNow();
    }
}
