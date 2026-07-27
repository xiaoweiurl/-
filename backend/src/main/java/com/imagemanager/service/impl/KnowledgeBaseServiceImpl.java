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

    public KnowledgeBaseServiceImpl(
            KnowledgeBaseDocRepository docRepository,
            KnowledgeBaseCategoryRepository categoryRepository,
            @Qualifier("localFileStorageService") FileStorageService localFileStorageService,
            DocumentParserService documentParserService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.docRepository = docRepository;
        this.categoryRepository = categoryRepository;
        this.localFileStorageService = localFileStorageService;
        this.documentParserService = documentParserService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
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
                        text.substring(0, Math.min(text.length(), 50000)), docId.toString());
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
            
            // ====== 混合检索(Hybrid Search): 关键词预过滤 + 向量语义搜索 ======
            // Step 1: 从查询中提取关键词（去除停用词、保留核心名词）
            List<String> keywords = extractKeywords(query);
            log.info("知识库搜索: 提取关键词={}", keywords);
            
            // Step 1.5: 关键词诊断 — 直接用SQL检查数据是否存在
            try {
                for (String kw : keywords) {
                    if (kw.length() >= 2 && kw.length() <= 10) {
                        Integer cnt = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM knowledge_base_docs WHERE file_content ILIKE ?",
                            Integer.class, "%" + kw + "%");
                        Integer embCnt = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM knowledge_embeddings WHERE source_type = 'KNOWLEDGE_BASE' AND chunk_text ILIKE ?",
                            Integer.class, "%" + kw + "%");
                        log.info("知识库搜索诊断: 关键词'{}' → docs表匹配{}, embeddings表匹配{}", kw, cnt, embCnt);
                    }
                }
            } catch (Exception diagEx) {
                log.warn("知识库搜索关键词诊断失败: {}", diagEx.getMessage());
            }
            
            // Step 2: 关键词SQL预过滤 — 先缩小候选集范围
            String keywordFilter = "";
            if (!keywords.isEmpty()) {
                // 构建关键词LIKE条件：chunk_text / title / file_name / file_content 包含任一关键词
                StringBuilder likeConditions = new StringBuilder();
                for (int i = 0; i < keywords.size(); i++) {
                    if (i > 0) likeConditions.append(" OR ");
                    likeConditions.append("e.chunk_text ILIKE ? OR d.title ILIKE ? OR d.file_name ILIKE ? OR d.file_content ILIKE ?");
                }
                keywordFilter = " AND (" + likeConditions + ") ";
            }
            
            float[] queryEmbedding = getEmbedding(query);
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                log.warn("知识库搜索: 获取查询Embedding失败, 尝试纯关键词搜索");
                return keywordSearchFallback(keywords, company, limit);
            }
            log.info("知识库搜索: 获取查询Embedding成功, 维度={}", queryEmbedding.length);

            String vectorStr = arrayToVectorString(queryEmbedding);

            // Step 3: 诊断信息
            try {
                Integer totalEmbeddings = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM knowledge_embeddings WHERE source_type = 'KNOWLEDGE_BASE'", Integer.class);
                Integer matchingCompany = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM knowledge_embeddings WHERE source_type = 'KNOWLEDGE_BASE' AND (company = ? OR company IS NULL)",
                        Integer.class, company);
                log.info("知识库搜索诊断: 总KNOWLEDGE_BASE记录={}, 匹配company='{}'的={}",
                        totalEmbeddings, company, matchingCompany);
            } catch (Exception diagEx) {
                log.warn("知识库搜索诊断查询失败: {}", diagEx.getMessage());
            }

            // Step 4: 混合检索SQL — 关键词预过滤 + 向量排序 + 增大limit
            // 使用参数绑定传递向量，避免超长SQL导致JDBC解析失败
            int candidateLimit = Math.max(limit * 3, 30);
            String sql = "SELECT e.id, d.title, e.chunk_text, e.source_doc_id, " +
                    "d.file_name, d.category, e.chunk_index, e.created_at, " +
                    "1 - (e.embedding <=> ?::vector) AS score " +
                    "FROM knowledge_embeddings e " +
                    "JOIN knowledge_base_docs d ON e.source_doc_id = d.id::text " +
                    "WHERE e.source_type = 'KNOWLEDGE_BASE' " +
                    "AND (e.company = ? OR e.company IS NULL) " +
                    "AND (d.company = ? OR d.company IS NULL) " +
                    keywordFilter +
                    "AND 1 - (e.embedding <=> ?::vector) >= ? " +
                    "ORDER BY e.embedding <=> ?::vector " +
                    "LIMIT ?";

            // Step 5: 如果关键词过滤后结果太少，降级到纯向量搜索
            List<MemorySearchResult> hybridResults = executeHybridSearch(sql, vectorStr, keywords, company, minScore, candidateLimit);
            log.info("知识库搜索: 混合检索返回{}条结果", hybridResults.size());
            
            if (hybridResults.size() < 3 && !keywords.isEmpty()) {
                // 关键词过滤太严格，降级为纯向量搜索（去掉关键词条件）
                log.info("知识库搜索: 关键词过滤结果不足({}条<3), 降级为纯向量搜索", hybridResults.size());
                String pureVectorSql = "SELECT e.id, d.title, e.chunk_text, e.source_doc_id, " +
                        "d.file_name, d.category, e.chunk_index, e.created_at, " +
                        "1 - (e.embedding <=> ?::vector) AS score " +
                        "FROM knowledge_embeddings e " +
                        "JOIN knowledge_base_docs d ON e.source_doc_id = d.id::text " +
                        "WHERE e.source_type = 'KNOWLEDGE_BASE' " +
                        "AND (e.company = ? OR e.company IS NULL) " +
                        "AND (d.company = ? OR d.company IS NULL) " +
                        "AND 1 - (e.embedding <=> ?::vector) >= ? " +
                        "ORDER BY e.embedding <=> ?::vector " +
                        "LIMIT ?";
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
            }
            
            log.info("知识库搜索: 最终返回{}条结果(混合检索+智能截断)", finalResults.size());
            return finalResults;
        } catch (Exception e) {
            log.error("知识库向量搜索失败: {}", e.getMessage());
            // 最终降级：尝试纯关键词搜索
            if (!keywords.isEmpty()) {
                return keywordSearchFallback(keywords, company, limit);
            }
            return Collections.emptyList();
        }
    }
    
    /**
     * 从查询中提取核心关键词（去除停用词、保留名词/品牌名/品类名）
     */
    private List<String> extractKeywords(String query) {
        // 中文停用词列表（只保留虚词/疑问词，保留业务关键词！）
        Set<String> stopWords = Set.of(
            "的", "了", "是", "在", "有", "和", "与", "或", "不", "也", "都",
            "就", "要", "会", "能", "这", "那", "什么", "怎么", "如何", "为什么",
            "哪个", "多少", "哪些", "请", "帮", "告诉我", "查询", "查", "看",
            "给", "让", "把", "被", "从", "到", "对", "为", "以", "于",
            "可以", "应该", "需要", "目前", "现在", "最新", "最近", "所有", "全部",
            "比较", "分析", "统计", "列出", "展示", "显示", "计算", "得出",
            "帮我", "问下", "请问", "我想", "知道"
            // 注意：不包含业务关键词（面料/原料/供应商/采购/成本/价格/报价等）
        );
        
        List<String> allTokens = new ArrayList<>();
        
        // 先保留原始查询（去掉末尾标点）作为完整匹配关键词
        String cleanedQuery = query.replaceAll("[\\s,，、；;！!？?。.：:\"\"''（）()\\[\\]\\{\\}]+$", "");
        if (cleanedQuery.length() >= 2 && !stopWords.contains(cleanedQuery)) {
            allTokens.add(cleanedQuery);
        }
        
        // 按空格、逗号、顿号、斜杠、连字符等分隔
        // 注意：加入 / 和 - 让 "FAST/28G" 拆出 "FAST" 和 "28G"
        String[] parts = query.split("[\\s,，、；;！!？?。.：:\"\"''（）()\\[\\]\\{\\}/\\-_]+");
        for (String part : parts) {
            // 保留完整的词（不分拆），用于精确匹配
            if (part.length() >= 2 && !stopWords.contains(part)) {
                allTokens.add(part);
            }
            // 长词再拆分为2-5字的子词（匹配知识库切片中的片段）
            if (part.length() >= 4) {
                for (int len = 2; len <= Math.min(5, part.length() - 1); len++) {
                    for (int i = 0; i <= part.length() - len; i++) {
                        String sub = part.substring(i, i + len);
                        if (!stopWords.contains(sub) && sub.length() >= 2) {
                            allTokens.add(sub);
                        }
                    }
                }
            }
        }
        
        // 保留完整词优先 + 子词补充
        Set<String> unique = new LinkedHashSet<>(allTokens);
        List<String> result = new ArrayList<>(unique);
        // 限制关键词数量（太多会导致SQL太复杂），但增加到10个
        if (result.size() > 10) {
            result = result.subList(0, 10);
        }
        return result;
    }
    
    /**
     * 执行混合检索SQL（关键词预过滤 + 向量排序）
     * 向量通过参数绑定传递，避免超长SQL导致JDBC解析失败
     */
    private List<MemorySearchResult> executeHybridSearch(String sql, String vectorStr, List<String> keywords, 
            String company, double minScore, int candidateLimit) {
        try {
            // 构建 PreparedStatement 参数：
            // SQL中?参数顺序：company×2, keywords×4, vector×3, minScore, candidateLimit
            return jdbcTemplate.query(sql, (PreparedStatement ps) -> {
                int idx = 1;
                ps.setString(idx++, company);
                ps.setString(idx++, company);
                for (String kw : keywords) {
                    String likePattern = "%" + kw + "%";
                    ps.setString(idx++, likePattern);  // chunk_text ILIKE
                    ps.setString(idx++, likePattern);  // title ILIKE
                    ps.setString(idx++, likePattern);  // file_name ILIKE
                    ps.setString(idx++, likePattern);  // file_content ILIKE
                }
                ps.setString(idx++, vectorStr);   // SELECT score
                ps.setDouble(idx++, minScore);     // WHERE score >= minScore
                ps.setString(idx++, vectorStr);   // ORDER BY
                ps.setInt(idx++, candidateLimit);
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
            log.warn("混合检索SQL执行失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 纯向量搜索（关键词过滤结果不足时降级）
     * 向量通过参数绑定传递，避免超长SQL导致JDBC解析失败
     */
    private List<MemorySearchResult> executePureVectorSearch(String sql, String vectorStr,
            String company, double minScore, int candidateLimit) {
        try {
            return jdbcTemplate.query(sql, (PreparedStatement ps) -> {
                int idx = 1;
                ps.setString(idx++, vectorStr);   // SELECT score
                ps.setString(idx++, company);      // WHERE company
                ps.setString(idx++, company);      // WHERE company
                ps.setString(idx++, vectorStr);   // WHERE score >= minScore
                ps.setDouble(idx++, minScore);     // minScore
                ps.setString(idx++, vectorStr);   // ORDER BY
                ps.setInt(idx++, candidateLimit);
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
            log.warn("纯向量搜索SQL执行失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    /**
     * 纯关键词搜索降级（Embedding失败时的兜底方案）
     * 同时搜索 embeddings.chunk_text 和 docs.file_content，确保表格数据也能命中
     */
    private List<MemorySearchResult> keywordSearchFallback(List<String> keywords, String company, int limit) {
        if (keywords.isEmpty()) return Collections.emptyList();
        
        List<MemorySearchResult> results = new ArrayList<>();
        Set<String> seenDocIds = new HashSet<>();
        
        // 第一轮：搜索 embeddings 表的 chunk_text
        try {
            StringBuilder whereClause = new StringBuilder();
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) whereClause.append(" OR ");
                whereClause.append("e.chunk_text ILIKE ? OR d.title ILIKE ? OR d.file_name ILIKE ? OR d.file_content ILIKE ?");
            }
            String sql = "SELECT e.id, d.title, e.chunk_text, e.source_doc_id, " +
                    "d.file_name, d.category, e.chunk_index, e.created_at " +
                    "FROM knowledge_embeddings e " +
                    "JOIN knowledge_base_docs d ON e.source_doc_id = d.id::text " +
                    "WHERE e.source_type = 'KNOWLEDGE_BASE' " +
                    "AND (e.company = ? OR e.company IS NULL) " +
                    "AND (d.company = ? OR d.company IS NULL) " +
                    "AND (" + whereClause + ") " +
                    "ORDER BY e.created_at DESC LIMIT ?";
            
            List<MemorySearchResult> embeddingResults = jdbcTemplate.query(sql, (PreparedStatement ps) -> {
                int idx = 1;
                ps.setString(idx++, company);
                ps.setString(idx++, company);
                for (String kw : keywords) {
                    String likePattern = "%" + kw + "%";
                    ps.setString(idx++, likePattern);  // chunk_text
                    ps.setString(idx++, likePattern);  // title
                    ps.setString(idx++, likePattern);  // file_name
                    ps.setString(idx++, likePattern);  // file_content
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
            log.warn("关键词搜索embeddings表失败: {}", e.getMessage());
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
                        "d.file_name, d.category, d.created_at " +
                        "FROM knowledge_base_docs d " +
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
                log.error("关键词搜索docs表兜底失败: {}", e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * 智能截断 — 按score分层保留高质量结果
     * 高分(>=0.7)全保留, 中分(0.5-0.7)最多5条, 低分(<0.5)最多3条
     */
    private List<MemorySearchResult> smartTruncate(List<MemorySearchResult> results, int limit) {
        if (results.size() <= limit) return results;
        
        List<MemorySearchResult> highScore = new ArrayList<>();
        List<MemorySearchResult> midScore = new ArrayList<>();
        List<MemorySearchResult> lowScore = new ArrayList<>();
        
        for (MemorySearchResult r : results) {
            double s = r.getScore() != null ? r.getScore() : 0;
            if (s >= 0.7) highScore.add(r);
            else if (s >= 0.5) midScore.add(r);
            else lowScore.add(r);
        }
        
        List<MemorySearchResult> finalResults = new ArrayList<>(highScore);
        // 中分最多补5条
        int midCount = Math.min(5, midScore.size());
        finalResults.addAll(midScore.subList(0, midCount));
        // 低分最多补3条
        int lowCount = Math.min(3, lowScore.size());
        finalResults.addAll(lowScore.subList(0, lowCount));
        
        // 最终不超过limit
        if (finalResults.size() > limit) {
            finalResults = finalResults.subList(0, limit);
        }
        
        log.info("智能截断: 总{}条 → 高分{} 中分{} 低分{} → 最终{}条",
                results.size(), highScore.size(), midScore.size(), lowScore.size(), finalResults.size());
        return finalResults;
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
