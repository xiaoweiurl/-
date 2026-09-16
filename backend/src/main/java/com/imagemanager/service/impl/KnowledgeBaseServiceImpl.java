package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.entity.KnowledgeBaseCategory;
import com.imagemanager.entity.KnowledgeBaseDoc;
import com.imagemanager.repository.KnowledgeBaseCategoryRepository;
import com.imagemanager.repository.KnowledgeBaseDocRepository;
import com.imagemanager.service.DocumentParserService;
import com.imagemanager.service.FileStorageService;
import com.imagemanager.service.KnowledgeBaseService;
import com.imagemanager.service.MilvusService;
import com.imagemanager.util.KeywordExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseDocRepository docRepository;
    private final KnowledgeBaseCategoryRepository categoryRepository;
    private final FileStorageService localFileStorageService;
    private final DocumentParserService documentParserService;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final MilvusService milvusService;

    public KnowledgeBaseServiceImpl(
            KnowledgeBaseDocRepository docRepository,
            KnowledgeBaseCategoryRepository categoryRepository,
            @Qualifier("localFileStorageService") FileStorageService localFileStorageService,
            DocumentParserService documentParserService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            MilvusService milvusService) {
        this.docRepository = docRepository;
        this.categoryRepository = categoryRepository;
        this.localFileStorageService = localFileStorageService;
        this.documentParserService = documentParserService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.milvusService = milvusService;
    }

    @Value("${app.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${app.ollama.embedding-model:bge-m3}")
    private String ollamaEmbeddingModel;

    @Value("${app.ollama.timeout:60000}")
    private int ollamaTimeout;

    private final ObjectMapper objectMapper = new ObjectMapper();
    // 有界线程池，防止无限创建线程导致OOM
    private final ExecutorService executorService = new ThreadPoolExecutor(
            5, 10, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            r -> { Thread t = new Thread(r, "kb-embedding-"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Override
    @Transactional
    public KnowledgeBaseDoc uploadDocument(MultipartFile file, String title, UUID categoryId, List<String> tags, String userId, String company) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            }

            String storagePath = "knowledge/" + company + "/" + UUID.randomUUID() + "." + extension;
            String fileUrl = localFileStorageService.uploadFile(file, storagePath);
            String fileType = determineFileType(extension);

            KnowledgeBaseDoc doc = new KnowledgeBaseDoc();
            doc.setId(UUID.randomUUID());
            doc.setTitle(title != null && !title.isEmpty() ? title : originalFilename);
            doc.setFileName(originalFilename);
            doc.setFilePath(fileUrl);
            doc.setFileType(fileType);
            doc.setFileSize(file.getSize());
            doc.setCategoryId(categoryId);
            doc.setTags(tags != null ? String.join(",", tags) : null);
            doc.setUserId(userId);
            doc.setCompany(company);
            doc.setEmbeddingStatus("PENDING");
            doc.setChunkCount(0);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());

            doc = docRepository.save(doc);

            // 异步向量化
            final UUID docId = doc.getId();
            final String docCompany = company;
            executorService.execute(() -> processEmbedding(docId, file, fileType, userId, docCompany));

            return doc;
        } catch (Exception e) {
            log.error("知识库文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public KnowledgeBaseDoc createTextDocument(String title, String content, UUID categoryId, String userId, String company) {
        try {
            KnowledgeBaseDoc doc = new KnowledgeBaseDoc();
            doc.setId(UUID.randomUUID());
            doc.setTitle(title);
            doc.setFileName(title + ".txt");
            doc.setFileType("txt");
            doc.setFileSize((long) content.length());
            doc.setCategoryId(categoryId);
            doc.setUserId(userId);
            doc.setCompany(company);
            doc.setFileContent(content.substring(0, Math.min(content.length(), 50000)));
            doc.setEmbeddingStatus("PENDING");
            doc.setChunkCount(0);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());

            doc = docRepository.save(doc);

            // 异步向量化
            final UUID docId = doc.getId();
            final String docCompany = company;
            final String docContent = content;
            executorService.execute(() -> processEmbeddingFromText(docId, docContent, docCompany));

            return doc;
        } catch (Exception e) {
            log.error("创建文本文档失败: {}", e.getMessage(), e);
            throw new RuntimeException("创建文本文档失败: " + e.getMessage());
        }
    }

    /**
     * 从文本内容向量化（用于文本/URL类型的文档）
     */
    private void processEmbeddingFromText(UUID docId, String text, String company) {
        try {
            if (text == null || text.trim().isEmpty()) {
                updateDocEmbeddingStatus(docId, 0, "EMPTY");
                return;
            }

            // 更新状态为处理中
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            tx.execute(status -> {
                KnowledgeBaseDoc doc = docRepository.findById(docId).orElse(null);
                if (doc != null) {
                    doc.setEmbeddingStatus("PROCESSING");
                    docRepository.save(doc);
                }
                return null;
            });

            // 切片
            List<String> chunks = documentParserService.chunkText(text, 800, 100);
            int successCount = 0;
            int failCount = 0;
            String firstError = null;

            log.info("文本文档向量化开始: docId={}, 切片数={}, embedding模型={}", docId, chunks.size(), ollamaEmbeddingModel);

            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                final int chunkIndex = i;
                if (chunk.trim().isEmpty()) continue;

                float[] embedding = getEmbedding(chunk);
                if (embedding == null || embedding.length == 0) {
                    failCount++;
                    if (firstError == null) firstError = "embedding返回null";
                    log.warn("文档 {} 切片 {} 向量化失败", docId, chunkIndex);
                    continue;
                }

                String vectorStr = arrayToVectorString(embedding);
                tx.execute(status -> {
                    jdbcTemplate.update(
                            "INSERT INTO knowledge_embeddings (id, card_id, embedding, embedding_model, chunk_text, chunk_index, source_type, source_doc_id, company, created_at) " +
                                    "VALUES (?::uuid, NULL, CAST(? AS vector), ?, ?, ?, ?, ?, ?, NOW())",
                            UUID.randomUUID().toString(), vectorStr, ollamaEmbeddingModel, chunk, chunkIndex, "KNOWLEDGE_BASE", docId.toString(), company
                    );
                    return null;
                });
                
                // 写入 Milvus（向量检索）
                if (milvusService != null && milvusService.isEnabled()) {
                    try {
                        milvusService.insertChunk(docId.toString(), null, "KNOWLEDGE_BASE", chunkIndex, chunk, embedding);
                    } catch (Exception e) {
                        log.warn("Milvus插入失败: docId={}, chunkIndex={}, error={}", docId, chunkIndex, e.getMessage());
                    }
                }
                
                successCount++;
            }

            updateDocEmbeddingStatus(docId, successCount, successCount > 0 ? "COMPLETED" : "FAILED");
            log.info("文本文档向量化结束: docId={}, 成功={}, 失败={}", docId, successCount, failCount);
            if (successCount == 0) {
                log.error("文本文档向量化全部失败: docId={}, 首个错误={}, embedding模型={}, Ollama地址={}", docId, firstError, ollamaEmbeddingModel, ollamaBaseUrl);
            }
        } catch (Exception e) {
            log.error("文本文档 {} 向量化失败: {}", docId, e.getMessage(), e);
            updateDocEmbeddingStatus(docId, 0, "FAILED");
        }
    }

    private void updateDocEmbeddingStatus(UUID docId, int chunkCount, String status) {
        try {
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            tx.execute(status2 -> {
                jdbcTemplate.update("UPDATE knowledge_base_docs SET embedding_status = ?, chunk_count = ?, updated_at = NOW() WHERE id = ?::uuid",
                        status, chunkCount, docId.toString());
                return null;
            });
        } catch (Exception e) {
            log.error("更新文档 {} 向量化状态失败: {}", docId, e.getMessage());
        }
    }

    private void processEmbedding(UUID docId, MultipartFile file, String fileType, String userId, String company) {
        try {
            // 只有文本类文件才提取内容向量化
            if (!isTextExtractable(fileType)) {
                updateDocEmbeddingStatus(docId, 0, "SKIPPED");
                return;
            }

            String text = documentParserService.parseDocument(file);
            if (text == null || text.trim().isEmpty()) {
                updateDocEmbeddingStatus(docId, 0, "EMPTY");
                return;
            }

            log.info("知识库文档解析结果: docId={}, textLength={}, preview={}", 
                docId, text.length(), text.substring(0, Math.min(text.length(), 200)).replace("\n", "\\n"));

            // 保存提取的文本
            // Update file content and status via direct SQL for reliability
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            tx.execute(status -> {
                jdbcTemplate.update("UPDATE knowledge_base_docs SET file_content = ?, embedding_status = 'PROCESSING', updated_at = NOW() WHERE id = ?::uuid",
                        text, docId.toString());
                return null;
            });

            // 切片
            List<String> chunks = documentParserService.chunkText(text, 800, 100);
            int successCount = 0;
            int failCount = 0;
            String firstError = null;

            log.info("知识库文档向量化开始: docId={}, 切片数={}, embedding模型={}", docId, chunks.size(), ollamaEmbeddingModel);

            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                final int chunkIndex = i;
                if (chunk.trim().isEmpty()) continue;

                float[] embedding = getEmbedding(chunk);
                if (embedding == null || embedding.length == 0) {
                    failCount++;
                    if (firstError == null) firstError = "embedding返回null";
                    log.warn("文档 {} 切片 {} 向量化失败", docId, chunkIndex);
                    continue;
                }

                String vectorStr = arrayToVectorString(embedding);
                tx.execute(status -> {
                    jdbcTemplate.update(
                            "INSERT INTO knowledge_embeddings (id, card_id, embedding, embedding_model, chunk_text, chunk_index, source_type, source_doc_id, company, created_at) " +
                                    "VALUES (?::uuid, NULL, CAST(? AS vector), ?, ?, ?, ?, ?, ?, NOW())",
                            UUID.randomUUID().toString(), vectorStr, ollamaEmbeddingModel, chunk, chunkIndex, "KNOWLEDGE_BASE", docId.toString(), company
                    );
                    return null;
                });
                
                // 写入 Milvus（向量检索）
                if (milvusService != null && milvusService.isEnabled()) {
                    try {
                        milvusService.insertChunk(docId.toString(), null, "KNOWLEDGE_BASE", chunkIndex, chunk, embedding);
                    } catch (Exception e) {
                        log.warn("Milvus插入失败: docId={}, chunkIndex={}, error={}", docId, chunkIndex, e.getMessage());
                    }
                }
                
                successCount++;
            }

            updateDocEmbeddingStatus(docId, successCount, successCount > 0 ? "COMPLETED" : "FAILED");
            log.info("知识库文档向量化结束: docId={}, 成功={}, 失败={}", docId, successCount, failCount);
            if (successCount == 0) {
                log.error("知识库文档向量化全部失败: docId={}, 首个错误={}, embedding模型={}, Ollama地址={}", docId, firstError, ollamaEmbeddingModel, ollamaBaseUrl);
            }
        } catch (Exception e) {
            log.error("知识库文档 {} 向量化失败: {}", docId, e.getMessage(), e);
            updateDocEmbeddingStatus(docId, 0, "FAILED");
        }
    }

    /**
     * 重试向量化：基于已提取的 fileContent 重新切片和向量化，不需要重新上传文件
     */
    private void processEmbeddingRetry(UUID docId) {
        try {
            KnowledgeBaseDoc doc = docRepository.findById(docId).orElse(null);
            if (doc == null) {
                log.warn("重试向量化: 文档 {} 不存在", docId);
                return;
            }

            String text = doc.getFileContent();
            if (text == null || text.trim().isEmpty()) {
                updateDocEmbeddingStatus(docId, 0, "FAILED");
                log.warn("重试向量化: 文档 {} 无文本内容", docId);
                return;
            }

            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            tx.execute(status -> {
                doc.setEmbeddingStatus("PROCESSING");
                docRepository.save(doc);
                // 先删除旧的向量记录
                jdbcTemplate.update("DELETE FROM knowledge_embeddings WHERE source_type = 'KNOWLEDGE_BASE' AND source_doc_id = ?::uuid", docId.toString());
                // Milvus 双删
                if (milvusService != null && milvusService.isEnabled()) {
                    milvusService.deleteByDocId(docId.toString());
                }
                return null;
            });

            // 切片
            List<String> chunks = documentParserService.chunkText(text, 800, 100);
            int successCount = 0;
            String docCompany = doc.getCompany();

            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                final int chunkIndex = i;
                if (chunk.trim().isEmpty()) continue;

                float[] embedding = getEmbedding(chunk);
                if (embedding == null || embedding.length == 0) {
                    log.warn("文档 {} 切片 {} 向量化失败", docId, chunkIndex);
                    continue;
                }

                String vectorStr = arrayToVectorString(embedding);
                tx.execute(status -> {
                    jdbcTemplate.update(
                            "INSERT INTO knowledge_embeddings (id, card_id, embedding, embedding_model, chunk_text, chunk_index, source_type, source_doc_id, company, created_at) " +
                                    "VALUES (?::uuid, NULL, CAST(? AS vector), ?, ?, ?, ?, ?, ?, NOW())",
                            UUID.randomUUID().toString(), vectorStr, ollamaEmbeddingModel, chunk, chunkIndex, "KNOWLEDGE_BASE", docId.toString(), docCompany
                    );
                    return null;
                });
                
                // 写入 Milvus（向量检索）
                if (milvusService != null && milvusService.isEnabled()) {
                    try {
                        milvusService.insertChunk(docId.toString(), null, "KNOWLEDGE_BASE", chunkIndex, chunk, embedding);
                    } catch (Exception e) {
                        log.warn("Milvus插入失败: docId={}, chunkIndex={}, error={}", docId, chunkIndex, e.getMessage());
                    }
                }
                
                successCount++;
            }

            updateDocEmbeddingStatus(docId, successCount, successCount > 0 ? "COMPLETED" : "FAILED");
            log.info("知识库文档 {} 重试向量化完成: {}/{} 切片成功", docId, successCount, chunks.size());
        } catch (Exception e) {
            log.error("知识库文档 {} 重试向量化失败: {}", docId, e.getMessage(), e);
            updateDocEmbeddingStatus(docId, 0, "FAILED");
        }
    }

    private boolean isTextExtractable(String fileType) {
        return "pdf".equals(fileType) || "word".equals(fileType) || "txt".equals(fileType)
                || "markdown".equals(fileType) || "excel".equals(fileType) || "ppt".equals(fileType);
    }

    @Override
    public Page<KnowledgeBaseDoc> getDocuments(String company, Pageable pageable) {
        return docRepository.findByCompanyOrderByCreatedAtDesc(company, pageable);
    }

    @Override
    public Page<KnowledgeBaseDoc> searchDocuments(String company, String keyword, Pageable pageable) {
        return docRepository.searchByKeyword(company, keyword, pageable);
    }

    @Override
    public List<KnowledgeBaseDoc> getDocumentsByCategory(String company, UUID categoryId) {
        return docRepository.findByCompanyAndCategoryIdOrderByCreatedAtDesc(company, categoryId);
    }

    @Override
    @Transactional
    public void deleteDocument(UUID id, String company) {
        KnowledgeBaseDoc doc = docRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> new RuntimeException("文档不存在或无权限"));

        // 删除存储的文件
        try {
            localFileStorageService.deleteFile(doc.getFilePath());
        } catch (Exception e) {
            log.warn("删除知识库文件失败: {}", e.getMessage());
        }

        // 删除对应的向量记录
        try {
            jdbcTemplate.update("DELETE FROM knowledge_embeddings WHERE source_type = 'KNOWLEDGE_BASE' AND source_doc_id = ?", id.toString());
            log.info("删除知识库文档 {} 对应的向量记录", id);
            // Milvus 双删
            if (milvusService != null && milvusService.isEnabled()) {
                milvusService.deleteByDocId(id.toString());
            }
        } catch (Exception e) {
            log.warn("删除知识库向量记录失败: {}", e.getMessage());
        }

        docRepository.delete(doc);
    }

    @Override
    public KnowledgeBaseDoc getDocumentDetail(UUID id, String company) {
        return docRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> new RuntimeException("文档不存在或无权限"));
    }

    @Override
    public KnowledgeBaseCategory createCategory(String name, String description, UUID parentId, String userId, String company) {
        KnowledgeBaseCategory category = new KnowledgeBaseCategory();
        category.setId(UUID.randomUUID());
        category.setName(name);
        category.setDescription(description);
        category.setParentId(parentId);
        category.setUserId(userId);
        category.setCompany(company);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    @Override
    public List<KnowledgeBaseCategory> getCategories(String company) {
        return categoryRepository.findByCompanyOrderByCreatedAtDesc(company);
    }

    @Override
    public void deleteCategory(UUID id, String company) {
        long docCount = docRepository.countByCompanyAndCategoryId(company, id);
        if (docCount > 0) {
            throw new RuntimeException("该分类下存在文档，无法删除");
        }

        KnowledgeBaseCategory category = categoryRepository.findByIdAndCompany(id, company)
                .orElseThrow(() -> new RuntimeException("分类不存在或无权限"));
        categoryRepository.delete(category);
    }

    @Override
    public long getDocumentCount(String company) {
        return docRepository.countByCompany(company);
    }

    @Override
    public KnowledgeBaseDoc getDocumentById(UUID id, String company) {
        var doc = docRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("文档不存在"));
        if (!company.equals(doc.getCompany())) {
            throw new RuntimeException("无权访问此文档");
        }
        return doc;
    }

    @Override
    public List<MemorySearchResult> search(String query, double minScore, int limit, String company) {
        try {
            log.info("知识库搜索: query='{}', minScore={}, limit={}, company='{}'", query, minScore, limit, company);
            
            // ====== Milvus 检索分支（启用时优先走 Milvus） ======
            if (milvusService != null && milvusService.isEnabled()) {
                try {
                    float[] queryEmbedding = getEmbedding(query);
                    if (queryEmbedding != null && queryEmbedding.length > 0) {
                        List<MilvusService.MilvusSearchResult> milvusResults = milvusService.search(queryEmbedding, limit);
                        if (!milvusResults.isEmpty()) {
                            List<MemorySearchResult> results = new ArrayList<>();
                            for (MilvusService.MilvusSearchResult mr : milvusResults) {
                                if (mr.score >= minScore) {
                                    MemorySearchResult r = new MemorySearchResult();
                                    r.setContent(mr.content);
                                    r.setScore((double) mr.score);
                                    // doc_id 兼容两种格式：标准 UUID（常规知识文档）与 32 位 hex（业务员资料批量导入，SHA-256 前 32 位，直写 Milvus 不落 PG）
                                    r.setSourceDocId(tryParseUuid(mr.docId));
                                    r.setSource("KNOWLEDGE_BASE");
                                    r.setDomainCode("knowledge_base");
                                    r.setDomainName("知识库");
                                    r.setConfidence(mr.score >= 0.75f ? "high" : "medium");
                                    results.add(r);
                                }
                            }
                            log.info("知识库搜索: Milvus检索返回{}条结果", results.size());
                            return results;
                        }
                    }
                } catch (Exception milvusEx) {
                    log.warn("知识库搜索: Milvus检索失败,降级到pgvector: {}", milvusEx.getMessage());
                }
            }
            
            // ====== 原有 pgvector 混合检索逻辑 ======
            // Step 1: 从查询中提取关键词（去除停用词、保留核心名词）
            List<String> keywords = extractKeywords(query);
            log.info("知识库搜索: 提取关键词={}", keywords);
            
            // Step 1.5: 关键词诊断 — 用 EXISTS 代替 COUNT(*)，避免全表扫描
            try {
                for (String kw : keywords) {
                    if (kw.length() >= 2 && kw.length() <= 15) {
                        Boolean embExists = jdbcTemplate.queryForObject(
                            "SELECT EXISTS(SELECT 1 FROM knowledge_embeddings WHERE source_type = 'KNOWLEDGE_BASE' AND chunk_text ILIKE ? LIMIT 1)",
                            Boolean.class, "%" + kw + "%");
                        log.info("知识库搜索诊断: 关键词'{}' → embeddings表{}数据", kw, Boolean.TRUE.equals(embExists) ? "有" : "无");
                    }
                }
            } catch (Exception diagEx) {
                log.warn("知识库搜索关键词诊断失败: {}", diagEx.getMessage());
            }
            
            // ====== Step 1.6: 货号优先检索 ======
            // 当查询中包含货号/产品编码（字母+数字混合，如M1TT403）时，直接用关键词精确搜索
            // 这样即使向量相似度很低，也能通过货号关键词找到对应数据
            List<String> productCodes = new ArrayList<>();
            for (String kw : keywords) {
                if (kw.matches("[A-Za-z][A-Za-z0-9]{2,}") && kw.matches(".*\\d.*") && kw.length() >= 4) {
                    productCodes.add(kw);
                }
            }
            if (!productCodes.isEmpty()) {
                log.info("知识库搜索: 检测到货号关键词{}, 优先执行关键词精确搜索", productCodes);
                try {
                    List<MemorySearchResult> keywordResults = keywordSearchFallback(productCodes, company, limit);
                    if (!keywordResults.isEmpty()) {
                        log.info("知识库搜索: 货号关键词搜索成功, 返回{}条结果", keywordResults.size());
                        return keywordResults;
                    }
                    log.info("知识库搜索: 货号关键词搜索无结果, 尝试直接SQL搜索");
                } catch (Exception kwEx) {
                    log.error("知识库搜索: 货号关键词搜索异常: {}", kwEx.getMessage(), kwEx);
                }
                // 兜底：直接查 knowledge_embeddings 表，不加任何JOIN，确保能找到数据
                try {
                    List<MemorySearchResult> directResults = directKeywordSearch(productCodes, company, limit);
                    if (!directResults.isEmpty()) {
                        log.info("知识库搜索: 直接SQL搜索成功, 返回{}条结果", directResults.size());
                        return directResults;
                    }
                    log.info("知识库搜索: 直接SQL搜索也无结果");
                } catch (Exception directEx) {
                    log.error("知识库搜索: 直接SQL搜索异常: {}", directEx.getMessage(), directEx);
                }
            }
            
            // Step 2: 关键词SQL预过滤 — 使用 tsvector 全文搜索代替 ILIKE（有索引时快100倍+）
            String keywordFilter = "";
            if (!keywords.isEmpty()) {
                // 构建 tsquery 全文搜索条件（利用 GIN 索引）
                // 同时保留 ILIKE 作为 fallback（兼容 search_vector 列未填充的旧数据）
                StringBuilder ftsConditions = new StringBuilder();
                for (int i = 0; i < keywords.size(); i++) {
                    if (i > 0) ftsConditions.append(" OR ");
                    // 优先用 tsvector 全文搜索（有索引），fallback 到 ILIKE（兼容旧数据）
                    ftsConditions.append("(e.search_vector @@ plainto_tsquery('simple', ?) OR e.chunk_text ILIKE ?)");
                }
                keywordFilter = " AND (" + ftsConditions + ") ";
            }
            
            float[] queryEmbedding = getEmbedding(query);
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                log.warn("知识库搜索: 获取查询Embedding失败, 尝试纯关键词搜索");
                return keywordSearchFallback(keywords, company, limit);
            }
            log.info("知识库搜索: 获取查询Embedding成功, 维度={}", queryEmbedding.length);

            String vectorStr = arrayToVectorString(queryEmbedding);

            // Step 3: 诊断信息（轻量级，只查一次）
            try {
                Integer totalEmbeddings = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM knowledge_embeddings WHERE source_type = 'KNOWLEDGE_BASE'", Integer.class);
                log.info("知识库搜索诊断: KNOWLEDGE_BASE总记录={}", totalEmbeddings);
            } catch (Exception diagEx) {
                log.warn("知识库搜索诊断查询失败: {}", diagEx.getMessage());
            }

            // Step 4: 混合检索SQL — CTE先过滤关键词候选集，再计算向量距离
            // 【优化】使用CTE分两步：1) 关键词过滤缩小候选集 2) 只对候选集计算向量距离
            // 【重要】使用 LEFT JOIN 而非 INNER JOIN，避免 knowledge_base_docs 记录缺失时向量数据被过滤
            int candidateLimit = Math.max(limit * 3, 30);
            String sql = "WITH candidates AS (" +
                    "SELECT e.id, e.chunk_text, e.source_doc_id, e.chunk_index, e.created_at, " +
                    "e.embedding <=> CAST(? AS vector) AS distance " +
                    "FROM knowledge_embeddings e " +
                    "WHERE e.source_type = 'KNOWLEDGE_BASE' " +
                    "AND (e.company = ? OR e.company IS NULL) " +
                    keywordFilter +
                    "AND 1 - (e.embedding <=> CAST(? AS vector)) >= ? " +
                    "ORDER BY distance " +
                    "LIMIT ?" +
                    ") " +
                    "SELECT c.id, COALESCE(d.title, '') AS title, c.chunk_text, c.source_doc_id, " +
                    "COALESCE(d.file_name, '') AS file_name, COALESCE(cat.name,'') AS category, c.chunk_index, c.created_at, " +
                    "1 - c.distance AS score " +
                    "FROM candidates c " +
                    "LEFT JOIN knowledge_base_docs d ON c.source_doc_id = d.id::text " +
                    "LEFT JOIN knowledge_base_categories cat ON d.category_id = cat.id " +
                    "ORDER BY c.distance";

            // Step 5: 如果关键词过滤后结果太少，降级到纯向量搜索
            List<MemorySearchResult> hybridResults = executeHybridSearch(sql, vectorStr, keywords, company, minScore, candidateLimit);
            log.info("知识库搜索: 混合检索返回{}条结果", hybridResults.size());
            
            if (hybridResults.size() < 3 && !keywords.isEmpty()) {
                // 关键词过滤太严格，降级为纯向量搜索（去掉关键词条件）
                log.info("知识库搜索: 关键词过滤结果不足({}条<3), 降级为纯向量搜索", hybridResults.size());
                String pureVectorSql = "WITH candidates AS (" +
                        "SELECT e.id, e.chunk_text, e.source_doc_id, e.chunk_index, e.created_at, " +
                        "e.embedding <=> CAST(? AS vector) AS distance " +
                        "FROM knowledge_embeddings e " +
                        "WHERE e.source_type = 'KNOWLEDGE_BASE' " +
                        "AND (e.company = ? OR e.company IS NULL) " +
                        "AND 1 - (e.embedding <=> CAST(? AS vector)) >= ? " +
                        "ORDER BY distance " +
                        "LIMIT ?" +
                        ") " +
                        "SELECT c.id, COALESCE(d.title, '') AS title, c.chunk_text, c.source_doc_id, " +
                        "COALESCE(d.file_name, '') AS file_name, COALESCE(cat.name,'') AS category, c.chunk_index, c.created_at, " +
                        "1 - c.distance AS score " +
                        "FROM candidates c " +
                        "LEFT JOIN knowledge_base_docs d ON c.source_doc_id = d.id::text " +
                        "LEFT JOIN knowledge_base_categories cat ON d.category_id = cat.id " +
                        "ORDER BY c.distance";
                hybridResults = executePureVectorSearch(pureVectorSql, vectorStr, company, minScore, candidateLimit);
            }
            
            // Step 6: 智能截断 — 按score分层，保留高质量结果
            List<MemorySearchResult> finalResults = new ArrayList<>();
            for (MemorySearchResult result : hybridResults) {
                double score = result.getScore();
                String content = result.getContent();
                int maxLen;
                if (score >= 0.7) {
                    maxLen = 2000;  // 高相关度：保留完整内容
                } else if (score >= 0.5) {
                    maxLen = 1200;  // 中等相关度：保留大部分
                } else {
                    maxLen = 600;   // 低相关度：精简摘要
                }
                if (content != null && content.length() > maxLen) {
                    result.setContent(content.substring(0, maxLen) + "...");
                }
                finalResults.add(result);
            }
            
            // 最终取topN结果（不超过请求的limit）
            if (finalResults.size() > limit) {
                finalResults = finalResults.subList(0, limit);
            }
            
            // Step 7: 向量搜索结果为空时，降级到纯关键词搜索
            if (finalResults.isEmpty() && !keywords.isEmpty()) {
                log.info("知识库搜索: 向量搜索结果为空, 降级到纯关键词搜索, keywords={}", keywords);
                List<MemorySearchResult> keywordResults = keywordSearchFallback(keywords, company, limit);
                if (!keywordResults.isEmpty()) {
                    log.info("知识库搜索: 关键词搜索兜底返回{}条结果", keywordResults.size());
                    return keywordResults;
                }
                // Step 7.1: 最终兜底 — 零JOIN直接搜索
                log.warn("知识库搜索: 关键词搜索也为空, 启用直接SQL兜底搜索");
                List<MemorySearchResult> directResults = directKeywordSearch(keywords, company, limit);
                if (!directResults.isEmpty()) {
                    log.info("知识库搜索: 直接SQL兜底返回{}条结果", directResults.size());
                    return directResults;
                }
            }
            
            log.info("知识库搜索: 最终返回{}条结果(混合检索+智能截断)", finalResults.size());
            return finalResults;
        } catch (Exception e) {
            log.error("知识库向量搜索失败: {}", e.getMessage());
            // 最终降级：尝试纯关键词搜索
            List<String> keywords = extractKeywords(query);
            if (!keywords.isEmpty()) {
                List<MemorySearchResult> kwResults = keywordSearchFallback(keywords, company, limit);
                if (!kwResults.isEmpty()) return kwResults;
                // 最终兜底
                List<MemorySearchResult> directResults = directKeywordSearch(keywords, company, limit);
                if (!directResults.isEmpty()) return directResults;
            }
            return Collections.emptyList();
        }
    }

    /**
     * 兼容解析 UUID：
     * 1. 标准 8-4-4-4-12 带连字符格式（常规知识文档，PG uuid 主键）
     * 2. 32 位无连字符 hex（业务员资料批量导入的 doc_id = 文件内容 SHA-256 前 32 位，直写 Milvus 不落 PG；
     *    Java UUID.fromString 不识别该格式会抛 IllegalArgumentException，PG uuid 列则可自动补连字符）
     * 解析失败返回 null，不抛异常（避免整体检索路径降级）
     */
    private static UUID tryParseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        try {
            return UUID.fromString(t);
        } catch (IllegalArgumentException ignore) {
            if (t.matches("[0-9a-fA-F]{32}")) {
                return UUID.fromString(
                        t.substring(0, 8) + "-" + t.substring(8, 12) + "-" + t.substring(12, 16)
                                + "-" + t.substring(16, 20) + "-" + t.substring(20, 32));
            }
            return null;
        }
    }

    /**
     * 从查询中提取核心关键词（去除停用词、保留名词/品牌名/品类名）
     */
    private List<String> extractKeywords(String query) {
        // 委托给统一的关键词提取器（内置行业词典 + 正向最大匹配分词）
        return KeywordExtractor.extractKeywords(query);
    }
    
    /**
     * 执行混合检索SQL（CTE关键词预过滤 + 向量排序）
     * 向量通过参数绑定传递，避免超长SQL导致JDBC解析失败
     */
    private List<MemorySearchResult> executeHybridSearch(String sql, String vectorStr, List<String> keywords, 
            String company, double minScore, int candidateLimit) {
        try {
            // CTE SQL 参数顺序：
            // CTE内: vectorStr(distance), company, keywords×2(tsquery+ILIKE), vectorStr(WHERE score), minScore, candidateLimit
            return jdbcTemplate.query(sql, (PreparedStatement ps) -> {
                int idx = 1;
                ps.setString(idx++, vectorStr);   // 1: CTE SELECT distance
                ps.setString(idx++, company);     // 2: CTE e.company
                for (String kw : keywords) {
                    ps.setString(idx++, kw);           // tsquery 全文搜索（plainto_tsquery 接受原始文本）
                    ps.setString(idx++, "%" + kw + "%"); // ILIKE fallback
                }
                ps.setString(idx++, vectorStr);   // CTE WHERE score condition
                ps.setDouble(idx++, minScore);     // minScore threshold
                ps.setInt(idx++, candidateLimit); // LIMIT
            }, (rs, rowNum) -> MemorySearchResult.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .title(rs.getString("title"))
                    .content(rs.getString("chunk_text"))
                    .domainCode("knowledge_base")
                    .domainName("知识库")
                    .source(rs.getString("file_name") + " [分类:" + rs.getString("category") + "]")
                    .confidence("high")
                    .createdAt(rs.getTimestamp("created_at") != null ?
                            rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .chunkText(rs.getString("chunk_text"))
                    .score(rs.getDouble("score"))
                    .build()
            );
        } catch (Exception e) {
            log.warn("混合检索SQL执行失败", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 纯向量搜索（关键词过滤结果不足时降级）
     * 使用CTE优化：先计算向量距离+过滤，再JOIN获取元数据
     */
    private List<MemorySearchResult> executePureVectorSearch(String sql, String vectorStr,
            String company, double minScore, int candidateLimit) {
        try {
            // CTE SQL 参数顺序：vectorStr(distance), company, vectorStr(WHERE score), minScore, candidateLimit
            return jdbcTemplate.query(sql, (PreparedStatement ps) -> {
                int idx = 1;
                ps.setString(idx++, vectorStr);   // CTE: distance calculation
                ps.setString(idx++, company);      // CTE: company filter
                ps.setString(idx++, vectorStr);   // CTE: WHERE score >= minScore
                ps.setDouble(idx++, minScore);     // minScore threshold
                ps.setInt(idx++, candidateLimit); // LIMIT
            }, (rs, rowNum) -> MemorySearchResult.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .title(rs.getString("title"))
                    .content(rs.getString("chunk_text"))
                    .domainCode("knowledge_base")
                    .domainName("知识库")
                    .source(rs.getString("file_name") + " [分类:" + rs.getString("category") + "]")
                    .confidence("high")
                    .createdAt(rs.getTimestamp("created_at") != null ?
                            rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .chunkText(rs.getString("chunk_text"))
                    .score(rs.getDouble("score"))
                    .build()
            );
        } catch (Exception e) {
            log.warn("纯向量搜索SQL执行失败", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 直接SQL搜索（零JOIN兜底方案）
     * 只查 knowledge_embeddings 表，不加任何JOIN，确保数据能被找到
     */
    private List<MemorySearchResult> directKeywordSearch(List<String> keywords, String company, int limit) {
        if (keywords.isEmpty()) return Collections.emptyList();
        
        List<MemorySearchResult> results = new ArrayList<>();
        
        try {
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT id, chunk_text, source_doc_id, chunk_index, created_at ");
            sql.append("FROM knowledge_embeddings ");
            sql.append("WHERE source_type = 'KNOWLEDGE_BASE' ");
            
            List<Object> params = new ArrayList<>();
            
            // company过滤：有company就精确匹配+NULL兼容，无company就不过滤
            if (company != null && !company.trim().isEmpty()) {
                sql.append("AND (company = ? OR company IS NULL OR company = '') ");
                params.add(company);
            }
            
            // 关键词搜索：优先 tsvector 全文搜索（有GIN索引），fallback 到 ILIKE
            sql.append("AND (");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("search_vector @@ plainto_tsquery('simple', ?) OR chunk_text ILIKE ?");
                params.add(keywords.get(i));
                params.add("%" + keywords.get(i) + "%");
            }
            sql.append(") ");
            
            sql.append("ORDER BY created_at DESC LIMIT ?");
            params.add(limit);
            
            log.info("直接SQL搜索: SQL={}, params={}", sql.toString(), params);
            
            results = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
                MemorySearchResult r = new MemorySearchResult();
                r.setId(tryParseUuid(rs.getString("id")));
                r.setContent(rs.getString("chunk_text"));
                r.setChunkText(rs.getString("chunk_text"));
                r.setSourceDocId(tryParseUuid(rs.getString("source_doc_id")));
                r.setScore(0.8); // 关键词精确匹配，给高分
                r.setSource("KNOWLEDGE_BASE");
                r.setDomainCode("knowledge_base");
                r.setDomainName("知识库");
                r.setConfidence("high");
                Timestamp ts = rs.getTimestamp("created_at");
                if (ts != null) {
                    r.setCreatedAt(ts.toLocalDateTime());
                }
                return r;
            }, params.toArray());
            
            log.info("直接SQL搜索: 找到{}条结果", results.size());
        } catch (Exception e) {
            log.error("直接SQL搜索异常: {}", e.getMessage(), e);
        }
        
        return results;
    }

    /**
     * 纯关键词搜索降级（Embedding失败时的兜底方案）
     * 同时搜索 embeddings.chunk_text 和 docs.file_content，确保表格数据也能命中
     */
    private List<MemorySearchResult> keywordSearchFallback(List<String> keywords, String company, int limit) {
        if (keywords.isEmpty()) return Collections.emptyList();
        
        List<MemorySearchResult> results = new ArrayList<>();
        Set<String> seenDocIds = new HashSet<>();
        
        // 第一轮：搜索 embeddings 表的 chunk_text（使用 tsvector 全文搜索 + ILIKE fallback）
        try {
            StringBuilder whereClause = new StringBuilder();
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) whereClause.append(" OR ");
                whereClause.append("e.search_vector @@ plainto_tsquery('simple', ?) OR e.chunk_text ILIKE ?");
            }
            String sql = "SELECT e.id, COALESCE(d.title, '') AS title, e.chunk_text, e.source_doc_id, " +
                    "COALESCE(d.file_name, '') AS file_name, COALESCE(c.name,'') AS category, e.chunk_index, e.created_at " +
                    "FROM knowledge_embeddings e " +
                    "LEFT JOIN knowledge_base_docs d ON e.source_doc_id = d.id::text " +
                    "LEFT JOIN knowledge_base_categories c ON d.category_id = c.id " +
                    "WHERE e.source_type = 'KNOWLEDGE_BASE' " +
                    "AND (e.company = ? OR e.company IS NULL) " +
                    "AND (" + whereClause + ") " +
                    "ORDER BY e.created_at DESC LIMIT ?";
            
            List<MemorySearchResult> embeddingResults = jdbcTemplate.query(sql, (PreparedStatement ps) -> {
                int idx = 1;
                ps.setString(idx++, company);
                for (String kw : keywords) {
                    ps.setString(idx++, kw);              // tsquery 全文搜索
                    ps.setString(idx++, "%" + kw + "%");  // ILIKE fallback
                }
                ps.setInt(idx++, limit);
            }, (rs, rowNum) -> MemorySearchResult.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .title(rs.getString("title"))
                    .content(rs.getString("chunk_text"))
                    .domainCode("knowledge_base")
                    .domainName("知识库")
                    .source(rs.getString("file_name") + " [分类:" + rs.getString("category") + "]")
                    .confidence("medium")
                    .createdAt(rs.getTimestamp("created_at") != null ?
                            rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .chunkText(rs.getString("chunk_text"))
                    .score(0.5)
                    .build()
            );
            
            for (MemorySearchResult r : embeddingResults) {
                results.add(r);
                if (r.getContent() != null) {
                    seenDocIds.add(r.getContent().hashCode() + "_" + r.getSource());
                }
            }
            log.info("关键词搜索embeddings表返回{}条结果", embeddingResults.size());
        } catch (Exception e) {
            log.warn("关键词搜索embeddings表失败", e);
        }
        
        // 第二轮：直接搜索 docs 表的 file_content（兜底，当embeddings切片丢失信息时）
        if (results.isEmpty()) {
            try {
                StringBuilder docWhereClause = new StringBuilder();
                for (int i = 0; i < keywords.size(); i++) {
                    if (i > 0) docWhereClause.append(" OR ");
                    docWhereClause.append("d.file_content ILIKE ? OR d.title ILIKE ? OR d.file_name ILIKE ?");
                }
                String docSql = "SELECT d.id, d.title, d.file_content, " +
                        "d.file_name, COALESCE(c.name,'') AS category, d.created_at " +
                        "FROM knowledge_base_docs d " +
                        "LEFT JOIN knowledge_base_categories c ON d.category_id = c.id " +
                        "WHERE (d.company = ? OR d.company IS NULL) " +
                        "AND (" + docWhereClause + ") " +
                        "ORDER BY d.created_at DESC LIMIT ?";
                
                results = jdbcTemplate.query(docSql, (PreparedStatement ps) -> {
                    int idx = 1;
                    ps.setString(idx++, company);
                    for (String kw : keywords) {
                        String likePattern = "%" + kw + "%";
                        ps.setString(idx++, likePattern);  // file_content
                        ps.setString(idx++, likePattern);  // title
                        ps.setString(idx++, likePattern);  // file_name
                    }
                    ps.setInt(idx++, limit);
                }, (rs, rowNum) -> {
                    String fileContent = rs.getString("file_content");
                    // 截取前1500字符作为内容（避免过长）
                    String displayContent = fileContent != null && fileContent.length() > 1500 
                            ? fileContent.substring(0, 1500) + "..." : fileContent;
                    return MemorySearchResult.builder()
                        .id(UUID.fromString(rs.getString("id")))
                        .title(rs.getString("title"))
                        .content(displayContent)
                        .domainCode("knowledge_base")
                        .domainName("知识库")
                        .source(rs.getString("file_name") + " [分类:" + rs.getString("category") + "]")
                        .confidence("low")
                        .createdAt(rs.getTimestamp("created_at") != null ?
                                rs.getTimestamp("created_at").toLocalDateTime() : null)
                        .chunkText(displayContent)
                        .score(0.3)  // 文档级搜索分数较低
                        .build();
                });
                log.info("关键词搜索docs表兜底返回{}条结果", results.size());
            } catch (Exception e) {
                log.error("关键词搜索docs表兜底失败", e);
            }
        }
        
        return results;
    }

    @Override
    public void retryEmbedding(String docId, String company) {
        var docOpt = docRepository.findById(UUID.fromString(docId));
        if (docOpt.isEmpty()) {
            throw new RuntimeException("文档不存在");
        }
        var doc = docOpt.get();
        if (!company.equals(doc.getCompany())) {
            throw new RuntimeException("无权操作此文档");
        }
        if (!"FAILED".equals(doc.getEmbeddingStatus()) && !"PENDING".equals(doc.getEmbeddingStatus())) {
            throw new RuntimeException("只有失败或等待中的文档可以重新处理");
        }
        // Reset status and re-process
        doc.setEmbeddingStatus("PENDING");
        docRepository.save(doc);
        final UUID docUuid = doc.getId();
        executorService.execute(() -> processEmbeddingRetry(docUuid));
        log.info("触发重新向量化, docId={}", docId);
    }

    private String determineFileType(String extension) {
        return switch (extension.toLowerCase()) {
            case "pdf" -> "pdf";
            case "doc", "docx" -> "word";
            case "xls", "xlsx", "csv" -> "excel";
            case "ppt", "pptx" -> "ppt";
            case "txt", "text" -> "txt";
            case "md", "markdown" -> "markdown";
            case "zip", "rar", "7z" -> "archive";
            case "jpg", "jpeg", "png", "gif", "webp" -> "image";
            default -> "other";
        };
    }

    // ========== Ollama Embedding ==========

    private float[] getEmbedding(String text) {
        try {
            String url = ollamaBaseUrl + "/api/embed";

            Map<String, Object> body = new HashMap<>();
            body.put("model", ollamaEmbeddingModel);
            body.put("input", text);

            String jsonBody = objectMapper.writeValueAsString(body);
            String response = doPost(url, jsonBody, null);
            JsonNode root = objectMapper.readTree(response);

            // Ollama /api/embed 返回 embeddings（复数，二维数组），/api/embeddings 返回 embedding（单数）
            JsonNode embeddingNode = null;
            if (root.has("embeddings") && root.get("embeddings").isArray() && root.get("embeddings").size() > 0) {
                embeddingNode = root.get("embeddings").get(0);
            } else if (root.has("embedding") && root.get("embedding").isArray()) {
                embeddingNode = root.get("embedding");
            }
            if (embeddingNode != null) {
                float[] embedding = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = (float) embeddingNode.get(i).asDouble();
                }
                log.info("Ollama embedding成功: model={}, 维度={}", ollamaEmbeddingModel, embedding.length);
                return embedding;
            }

            log.warn("Ollama Embedding返回格式异常, 完整响应: {}", response);
            return null;
        } catch (Exception e) {
            log.error("获取embedding异常(Ollama): model={}, url={}, error={}", ollamaEmbeddingModel, ollamaBaseUrl + "/api/embed", e.getMessage());
            return null;
        }
    }

    private String doPost(String urlStr, String jsonBody, String apiKey) throws Exception {
        URI uri = URI.create(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(ollamaTimeout);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                responseCode >= 200 && responseCode < 300 ? conn.getInputStream() : conn.getErrorStream(),
                StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        if (responseCode < 200 || responseCode >= 300) {
            throw new RuntimeException("HTTP " + responseCode + ": " + response);
        }
        return response.toString();
    }

    private String arrayToVectorString(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            // 使用BigDecimal避免科学计数法（如3.4267126E-4），
            // PostgreSQL的::vector类型转换不支持科学计数法，必须是标准十进制小数
            // pgvector格式: [0.1,0.2,...] 方括号
            sb.append(new java.math.BigDecimal(String.valueOf(array[i])).toPlainString());
        }
        sb.append("]");
        return sb.toString();
    }
}
