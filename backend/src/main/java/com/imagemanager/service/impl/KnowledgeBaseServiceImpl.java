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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseDocRepository docRepository;
    private final KnowledgeBaseCategoryRepository categoryRepository;
    private final FileStorageService fileStorageService;
    private final DocumentParserService documentParserService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.minimax.api-key:}")
    private String minimaxApiKey;

    @Value("${app.minimax.embedding.base-url:https://api.minimaxi.com/v1/embeddings}")
    private String minimaxEmbeddingUrl;

    @Value("${app.minimax.embedding.model:embo-01}")
    private String minimaxEmbeddingModel;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Override
    @Transactional
    public KnowledgeBaseDoc uploadDocument(MultipartFile file, String title, UUID categoryId, List<String> tags, String userId) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
            }

            String storagePath = "knowledge/" + userId + "/" + UUID.randomUUID() + "." + extension;
            String fileUrl = fileStorageService.uploadFile(file, storagePath);
            String fileType = determineFileType(extension);

            KnowledgeBaseDoc doc = new KnowledgeBaseDoc();
            doc.setId(UUID.randomUUID());
            doc.setTitle(title != null && !title.isEmpty() ? title : originalFilename);
            doc.setFileName(originalFilename);
            doc.setFilePath(fileUrl);
            doc.setFileType(fileType);
            doc.setFileSize(file.getSize());
            doc.setCategoryId(categoryId);
            doc.setTags(tags);
            doc.setUserId(userId);
            doc.setEmbeddingStatus("PENDING");
            doc.setChunkCount(0);
            doc.setCreatedAt(LocalDateTime.now());
            doc.setUpdatedAt(LocalDateTime.now());

            doc = docRepository.save(doc);

            // 异步向量化
            final UUID docId = doc.getId();
            executorService.execute(() -> processEmbedding(docId, file, fileType, userId));

            return doc;
        } catch (Exception e) {
            log.error("知识库文件上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    private void updateDocEmbeddingStatus(UUID docId, int chunkCount, String status) {
        try {
            KnowledgeBaseDoc d = docRepository.findById(docId).orElse(null);
            if (d != null) {
                d.setChunkCount(chunkCount);
                d.setEmbeddingStatus(status);
                docRepository.save(d);
            }
        } catch (Exception e) {
            log.error("更新文档 {} 向量化状态失败: {}", docId, e.getMessage());
        }
    }

    private void processEmbedding(UUID docId, MultipartFile file, String fileType, String userId) {
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

            // 保存提取的文本
            KnowledgeBaseDoc doc = docRepository.findById(docId).orElse(null);
            if (doc != null) {
                doc.setFileContent(text.substring(0, Math.min(text.length(), 50000)));
                doc.setEmbeddingStatus("PROCESSING");
                docRepository.save(doc);
            }

            // 切片
            List<String> chunks = documentParserService.chunkText(text, 800, 100);
            int successCount = 0;

            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                if (chunk.trim().isEmpty()) continue;

                float[] embedding = getEmbedding(chunk);
                if (embedding == null || embedding.length == 0) {
                    log.warn("文档 {} 切片 {} 向量化失败", docId, i);
                    continue;
                }

                String vectorStr = arrayToVectorString(embedding);
                // 必须用 JdbcTemplate + CAST(? AS vector) 插入，因为 embedding 列是 pgvector 类型，
                // JPA save() 会把 String 参数绑定为 varchar 导致类型不匹配
                jdbcTemplate.update(
                        "INSERT INTO knowledge_embeddings (id, card_id, embedding, embedding_model, chunk_text, chunk_index, source_type, source_doc_id, created_at) " +
                                "VALUES (?::uuid, NULL, CAST(? AS vector), ?, ?, ?, ?, ?, NOW())",
                        UUID.randomUUID().toString(), vectorStr, minimaxEmbeddingModel, chunk, i, "KNOWLEDGE_BASE", docId.toString()
                );
                successCount++;
            }

            updateDocEmbeddingStatus(docId, successCount, successCount > 0 ? "COMPLETED" : "FAILED");
            log.info("知识库文档 {} 向量化完成: {}/{} 切片成功", docId, successCount, chunks.size());
        } catch (Exception e) {
            log.error("知识库文档 {} 向量化失败: {}", docId, e.getMessage(), e);
            updateDocEmbeddingStatus(docId, 0, "FAILED");
        }
    }

    private boolean isTextExtractable(String fileType) {
        return "pdf".equals(fileType) || "word".equals(fileType) || "txt".equals(fileType)
                || "markdown".equals(fileType) || "excel".equals(fileType) || "ppt".equals(fileType);
    }

    @Override
    public Page<KnowledgeBaseDoc> getDocuments(String userId, Pageable pageable) {
        return docRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public List<KnowledgeBaseDoc> getDocumentsByCategory(String userId, UUID categoryId) {
        return docRepository.findByUserIdAndCategoryIdOrderByCreatedAtDesc(userId, categoryId);
    }

    @Override
    @Transactional
    public void deleteDocument(UUID id, String userId) {
        KnowledgeBaseDoc doc = docRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("文档不存在或无权限"));

        // 删除存储的文件
        try {
            fileStorageService.deleteFile(doc.getFilePath());
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
    public KnowledgeBaseDoc getDocumentDetail(UUID id, String userId) {
        return docRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("文档不存在或无权限"));
    }

    @Override
    public KnowledgeBaseCategory createCategory(String name, String description, UUID parentId, String userId) {
        KnowledgeBaseCategory category = new KnowledgeBaseCategory();
        category.setId(UUID.randomUUID());
        category.setName(name);
        category.setDescription(description);
        category.setParentId(parentId);
        category.setUserId(userId);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    @Override
    public List<KnowledgeBaseCategory> getCategories(String userId) {
        return categoryRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public void deleteCategory(UUID id, String userId) {
        long docCount = docRepository.countByCategoryId(id);
        if (docCount > 0) {
            throw new RuntimeException("该分类下存在文档，无法删除");
        }

        KnowledgeBaseCategory category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RuntimeException("分类不存在或无权限"));
        categoryRepository.delete(category);
    }

    @Override
    public long getDocumentCount(String userId) {
        return docRepository.countByUserId(userId);
    }

    @Override
    public List<MemorySearchResult> search(String query, double minScore, int limit, String userId) {
        try {
            float[] queryEmbedding = getEmbedding(query);
            if (queryEmbedding == null || queryEmbedding.length == 0) {
                return Collections.emptyList();
            }

            String vectorStr = arrayToVectorString(queryEmbedding);

            String sql = "SELECT e.id, d.title, e.chunk_text, e.source_doc_id, " +
                    "d.file_name, e.chunk_index, e.created_at, " +
                    "1 - (e.embedding <=> '" + vectorStr + "'::vector) AS score " +
                    "FROM knowledge_embeddings e " +
                    "JOIN knowledge_base_docs d ON e.source_doc_id = d.id::text " +
                    "WHERE e.source_type = 'KNOWLEDGE_BASE' " +
                    "AND d.user_id = ? " +
                    "AND 1 - (e.embedding <=> '" + vectorStr + "'::vector) >= ? " +
                    "ORDER BY e.embedding <=> '" + vectorStr + "'::vector " +
                    "LIMIT ?";

            return jdbcTemplate.query(sql, (PreparedStatement ps) -> {
                ps.setString(1, userId);
                ps.setDouble(2, minScore);
                ps.setInt(3, limit);
            }, (rs, rowNum) -> MemorySearchResult.builder()
                    .id(UUID.fromString(rs.getString("id")))
                    .title(rs.getString("title"))
                    .content(rs.getString("chunk_text"))
                    .domainCode("knowledge_base")
                    .domainName("知识库")
                    .source(rs.getString("file_name"))
                    .confidence("high")
                    .createdAt(rs.getTimestamp("created_at") != null ?
                            rs.getTimestamp("created_at").toLocalDateTime() : null)
                    .chunkText(rs.getString("chunk_text"))
                    .score(rs.getDouble("score"))
                    .build()
            );
        } catch (Exception e) {
            log.error("知识库向量搜索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
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

    // ========== MiniMax Embedding ==========

    private float[] getEmbedding(String text) {
        try {
            String apiKey = minimaxApiKey;
            if (apiKey == null || apiKey.isEmpty()) {
                apiKey = System.getenv("MINIMAX_API_KEY");
            }
            if (apiKey == null || apiKey.isEmpty()) {
                log.warn("未配置MiniMax API密钥, 跳过向量化");
                return null;
            }

            String url = minimaxEmbeddingUrl;
            Map<String, Object> body = new HashMap<>();
            body.put("model", minimaxEmbeddingModel);
            body.put("texts", new String[]{text});
            body.put("type", "db");

            String jsonBody = objectMapper.writeValueAsString(body);
            String response = doPost(url, jsonBody, apiKey);
            JsonNode root = objectMapper.readTree(response);

            if (root.has("vectors") && root.get("vectors").isArray() && root.get("vectors").size() > 0) {
                JsonNode embeddingNode = root.get("vectors").get(0);
                if (embeddingNode != null && embeddingNode.isArray()) {
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    return embedding;
                }
            }

            // 兜底尝试 OpenAI 兼容格式
            body.remove("texts");
            body.remove("type");
            body.put("input", text);
            response = doPost(url, objectMapper.writeValueAsString(body), apiKey);
            root = objectMapper.readTree(response);
            if (root.has("data") && root.get("data").isArray() && root.get("data").size() > 0) {
                JsonNode embeddingNode = root.get("data").get(0).get("embedding");
                if (embeddingNode != null && embeddingNode.isArray()) {
                    float[] embedding = new float[embeddingNode.size()];
                    for (int i = 0; i < embeddingNode.size(); i++) {
                        embedding[i] = (float) embeddingNode.get(i).asDouble();
                    }
                    return embedding;
                }
            }

            log.warn("MiniMax Embedding返回异常: {}", response);
            return null;
        } catch (Exception e) {
            log.error("获取Embedding失败: {}", e.getMessage());
            return null;
        }
    }

    private String doPost(String urlStr, String jsonBody, String apiKey) throws Exception {
        URI uri = URI.create(urlStr);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);

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
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
