package com.imagemanager.tools;

import com.imagemanager.cache.LlmCacheService;
import com.imagemanager.dto.MemorySearchResult;
import com.imagemanager.service.KnowledgeBaseService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 供应链 AI 工具集
 * 
 * 通过 LangChain4j @Tool 注解暴露给大模型自动调用：
 * 1. searchByVector - 向量语义检索：用 embedding 相似度搜索 knowledge_embeddings 表的 chunk_text
 * 2. queryDatabase - Text-to-SQL：大模型生成 SQL 查询 knowledge_base_docs 表的文本字段
 * 大模型会根据问题类型自动选择工具（可两个都调用）
 * 
 * 安全策略：
 * - 只允许 SELECT 语句
 * - 自动注入 LIMIT 100
 * - 强制 company 过滤
 * - 5 秒查询超时（由数据库连接池控制）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplyChainTools {

    private final JdbcTemplate jdbcTemplate;
    private final KnowledgeBaseService knowledgeBaseService;
    private final LlmCacheService llmCacheService;

    /** ThreadLocal 存储当前请求的 userId，用于缓存隔离 */
    private static final ThreadLocal<String> currentUserId = new ThreadLocal<>();

    /**
     * 供应链核心表的 Schema 元数据
     * 大模型基于此生成正确的 SQL
     */
    private static final String SCHEMA_CONTEXT = """
            ## 知识库数据库表结构（PostgreSQL）
            
            注意：所有业务数据（供应链报价、采购、生产计划等）都已上传为文档并自动向量化存储。
            请基于以下两张表查询数据，不要查询其他不存在的表。

            ### 1. knowledge_base_docs - 知识库文档表
            存储上传的文档元数据和提取的文本内容。
            - id (VARCHAR, 主键, 文档ID)
            - title (VARCHAR, 文档标题)
            - content (TEXT, 提取的完整文本内容)
            - file_content (TEXT, 原始文本内容备份)
            - file_type (VARCHAR, 文件类型: pdf/word/excel/txt/markdown)
            - category_id (VARCHAR, 分类ID)
            - company (VARCHAR, 公司名, 多租户隔离)
            - chunk_count (INTEGER, 切片数量)
            - embedding_status (VARCHAR, 向量化状态: COMPLETED/PROCESSING/PENDING/FAILED)
            - keywords (VARCHAR, 关键词)
            - status (INTEGER, 状态: 0=正常 1=已删除)
            - created_at (TIMESTAMP, 创建时间)
            - updated_at (TIMESTAMP, 更新时间)

            ### 2. knowledge_embeddings - 向量嵌入表
            存储文档切片的向量嵌入和文本片段，每行是一个文档切片。
            - id (UUID, 主键)
            - card_id (UUID, 关联知识卡片ID，可为空)
            - source_type (VARCHAR, 来源类型: KNOWLEDGE_BASE=知识库 MEMORY=记忆库)
            - source_doc_id (VARCHAR, 源文档ID, 关联 knowledge_base_docs.id)
            - chunk_index (INTEGER, 切片序号, 从0开始)
            - chunk_text (TEXT, 切片文本内容, 约800字/片)
            - embedding (vector(1024), 向量嵌入, 不要在SQL中SELECT此字段)
            - embedding_model (VARCHAR, 向量模型名)
            - company (VARCHAR, 公司名, 多租户隔离)
            - created_at (TIMESTAMP, 创建时间)

            ### 3. knowledge_base_categories - 知识库分类表
            - id (VARCHAR, 主键)
            - name (VARCHAR, 分类名称)
            - description (TEXT, 分类描述)
            - company (VARCHAR, 公司名)
            - created_at (TIMESTAMP, 创建时间)

            ## 查询规则
            1. 查询时必须加 WHERE company = 'COMPANY_PLACEHOLDER' 条件做数据隔离
            2. 查询 knowledge_embeddings 时不要 SELECT embedding 字段（向量数据无法展示）
            3. 要获取文档内容：SELECT title, content FROM knowledge_base_docs WHERE ...
            4. 要搜索关键词：SELECT chunk_text, source_doc_id FROM knowledge_embeddings WHERE chunk_text ILIKE '%关键词%' AND source_type = 'KNOWLEDGE_BASE' AND company = 'COMPANY_PLACEHOLDER'
            5. 要关联文档信息：SELECT e.chunk_text, d.title FROM knowledge_embeddings e JOIN knowledge_base_docs d ON e.source_doc_id::text = d.id WHERE e.chunk_text ILIKE '%关键词%' AND e.company = 'COMPANY_PLACEHOLDER'
            6. 文档内容包含：产品报价、原料采购、原料入库、生产计划、辅料采购等业务数据（以自然语言描述句格式存储）
            """;

    /** 危险 SQL 关键词 */
    private static final Pattern DANGEROUS_SQL = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|EXEC|EXECUTE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 向量语义检索工具：用 embedding 向量做相似度搜索
     * 
     * 当用户问题涉及具体业务数据（如"克重175克的机型"、"最便宜的原料"）时，
     * 向量检索比SQL更精准——它能匹配语义相似度而非字面关键词。
     * 系统会自动把查询文本转为向量，与 knowledge_embeddings.embedding 列做余弦距离比较。
     */
    @Tool("向量语义搜索：根据用户问题的语义相似度检索知识库文档。适合查询具体业务数据，如产品报价、原料采购、生产计划等。传入用户的问题或关键词。")
    public String searchByVector(String query) {
        String company = currentCompany.get();
        log.info("[向量检索] query='{}', company='{}'", query, company);
        try {
            var results = knowledgeBaseService.search(query, 0.25f, 10, company);
            if (results == null || results.isEmpty()) {
                log.warn("[向量检索] 无结果");
                return "向量检索无结果，请尝试用queryDatabase工具做关键词搜索。";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("向量检索到 ").append(results.size()).append(" 条相关结果：\n\n");
            for (int i = 0; i < results.size(); i++) {
                var r = results.get(i);
                sb.append("--- 结果 ").append(i + 1).append(" (相似度:");
                if (r.getScore() != null) {
                    sb.append(String.format("%.4f", r.getScore()));
                } else {
                    sb.append("N/A");
                }
                sb.append(") ---\n");
                if (r.getChunkText() != null && !r.getChunkText().isEmpty()) {
                    sb.append(r.getChunkText());
                } else if (r.getContent() != null) {
                    sb.append(r.getContent());
                }
                if (r.getTitle() != null) {
                    sb.append("\n[来源: ").append(r.getTitle()).append("]");
                }
                sb.append("\n\n");
            }
            log.info("[向量检索] 返回 {} 条结果", results.size());
            return sb.toString();
        } catch (Exception e) {
            log.error("[向量检索] 失败: {}", e.getMessage(), e);
            return "向量检索失败: " + e.getMessage() + "，请尝试用queryDatabase工具做SQL查询。";
        }
    }

    /**
     * Text-to-SQL 工具：大模型自动生成 SQL 并执行查询
     * 
     * 调用方式：大模型会传入完整的 SQL 语句，此方法负责安全校验和执行
     * 注意：company 参数由 SmartChatServiceImpl 通过 ThreadLocal 或上下文传入
     */
    @Tool("执行SQL查询数据库获取知识库文档的元数据和文本内容。传入完整的SELECT语句。只能查询，禁止修改数据。SQL中用 'COMPANY_PLACEHOLDER' 代替公司名，系统会自动替换。")
    public String queryDatabase(String sql) {
        log.info("[Text-to-SQL] 接收到SQL: {}", sql);

        // 安全校验
        String trimmedSql = sql.trim().replaceAll(";\\s*$", "");
        String upperSql = trimmedSql.toUpperCase();
        
        if (!upperSql.startsWith("SELECT")) {
            return "错误：只允许SELECT查询语句";
        }
        
        if (DANGEROUS_SQL.matcher(trimmedSql).find()) {
            return "错误：检测到危险SQL操作，已拒绝执行";
        }

        // ======== 性能拦截规则 ========
        // 禁止 SELECT * 全字段查询
        if (upperSql.matches(".*\\bSELECT\\s+\\*\\b.*")) {
            return "错误：禁止 SELECT *，请明确指定需要查询的字段名";
        }
        // 禁止无 WHERE 条件全表扫描
        if (!upperSql.contains("WHERE")) {
            return "错误：SQL 必须包含 WHERE 过滤条件，禁止全表扫描";
        }
        // 禁止前后全包模糊匹配 %keyword%
        if (upperSql.matches(".*LIKE\\s+'%[^']+%[^']*'.*")) {
            return "错误：禁止前后全模糊匹配 %关键词%，请使用后缀匹配 关键词%";
        }
        // 禁止超过 2 表 JOIN
        if (upperSql.split("(?i)JOIN").length > 2) {
            return "错误：禁止超过 2 张表 JOIN 关联";
        }

        // 防止查询向量字段（embedding 列数据量巨大，会导致输出爆炸）
        if (upperSql.contains("KNOWLEDGE_EMBEDDINGS") && upperSql.matches(".*\\bSELECT\\s+[^,]*\\bEMBEDDING\\b[^,]*.*")) {
            return "错误：禁止查询 embedding 向量字段，请改为查询 chunk_text 等文本字段。";
        }

        // 替换公司占位符
        String company = currentCompany.get();
        if (company == null || company.isEmpty()) {
            company = "";
        }
        trimmedSql = trimmedSql.replace("COMPANY_PLACEHOLDER", "'" + company.replace("'", "''") + "'");

        // 自动注入 LIMIT（如果没有的话，限制为 10）
        if (!upperSql.contains("LIMIT")) {
            trimmedSql = trimmedSql + " LIMIT 10";
        }

        try {
            // L2 缓存：检查 DB 查询结果缓存（按用户隔离）
            String userId = currentUserId.get();
            if (llmCacheService != null && userId != null) {
                List<Map<String, Object>> cached = llmCacheService.getCachedDbResult(userId, trimmedSql);
                if (cached != null) {
                    log.info("[Text-to-SQL] L2缓存命中, userId={}, 返回 {} 行", userId, cached.size());
                    if (cached.isEmpty()) {
                        return "查询结果为空，数据库中没有匹配的数据。";
                    }
                    return formatAsMarkdownTable(cached);
                }
            }

            long sqlStart = System.currentTimeMillis();
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(trimmedSql);
            long sqlElapsed = System.currentTimeMillis() - sqlStart;

            // 慢 SQL 监控（超过 500ms 告警）
            if (sqlElapsed > 500) {
                log.warn("[慢SQL告警] 耗时 {}ms, SQL: {}", sqlElapsed, trimmedSql);
            } else {
                log.info("[Text-to-SQL] SQL执行耗时 {}ms, 返回 {} 行", sqlElapsed, rows.size());
            }

            if (rows.isEmpty()) {
                return "查询结果为空，数据库中没有匹配的数据。";
            }

            // 写入 L2 缓存
            if (llmCacheService != null && userId != null) {
                llmCacheService.putCachedDbResult(userId, trimmedSql, rows);
            }

            // 格式化为 Markdown 表格，方便大模型理解
            return formatAsMarkdownTable(rows);

        } catch (Exception e) {
            log.error("[Text-to-SQL] SQL执行失败: {}", e.getMessage(), e);
            return "SQL执行失败: " + e.getMessage() + "。请检查SQL语法是否正确。";
        }
    }

    /** ThreadLocal 存储当前请求的 company，用于 SQL 注入 */
    private static final ThreadLocal<String> currentCompany = new ThreadLocal<>();

    /**
     * 设置当前请求的公司（由 SmartChatServiceImpl 调用）
     */
    public void setCurrentCompany(String company) {
        currentCompany.set(company);
    }

    /**
     * 设置当前请求的 userId（由 SmartChatServiceImpl 调用，用于缓存隔离）
     */
    public void setCurrentUserId(String userId) {
        currentUserId.set(userId);
    }

    /**
     * 清理 ThreadLocal（请求结束后调用）
     */
    public void clearCurrentCompany() {
        currentCompany.remove();
        currentUserId.remove();
    }

    /**
     * 获取数据库表结构（大模型生成SQL时需要参考）
     */
    @Tool("获取供应链数据库的表结构信息，用于了解有哪些表和字段可以查询")
    public String getDatabaseSchema() {
        return SCHEMA_CONTEXT;
    }

    /**
     * 将查询结果格式化为 Markdown 表格
     */
    private String formatAsMarkdownTable(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "无数据";
        }

        StringBuilder sb = new StringBuilder();
        Set<String> columns = rows.get(0).keySet();

        // 表头
        sb.append("| ");
        sb.append(String.join(" | ", columns));
        sb.append(" |\n");

        // 分隔行
        sb.append("| ");
        sb.append(String.join(" | ", columns.stream().map(c -> "---").toList()));
        sb.append(" |\n");

        // 数据行
        for (Map<String, Object> row : rows) {
            sb.append("| ");
            sb.append(columns.stream()
                    .map(c -> {
                        Object v = row.get(c);
                        return v == null ? "" : v.toString();
                    })
                    .reduce((a, b) -> a + " | " + b)
                    .orElse(""));
            sb.append(" |\n");
        }

        sb.append("\n共 ").append(rows.size()).append(" 行数据。");
        return sb.toString();
    }
}
