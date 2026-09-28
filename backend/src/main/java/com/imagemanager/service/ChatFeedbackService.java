package com.imagemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.eval.RagEvalCase;
import com.imagemanager.util.KeywordExtractor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 对话反馈。答错的问题按公司记入待补充，供管理员补文档或导出成评测题。
 */
@Service
public class ChatFeedbackService {

    static final String COMPANY_EQ =
            "COALESCE(NULLIF(company, ''), '') = COALESCE(NULLIF(?, ''), '')";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChatFeedbackService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> record(String userId, String company, String conversationId,
                                       String question, String answer, String sourcesJson,
                                       String comment, String verdict) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        if (!"useful".equals(verdict) && !"wrong".equals(verdict)) {
            throw new IllegalArgumentException("反馈只能是有用或答错");
        }
        String status = "wrong".equals(verdict) ? "pending" : "recorded";
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update(
                "INSERT INTO knowledge_supplement " +
                        "(id, user_id, company, conversation_id, question, answer, sources_json, comment, verdict, status, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                id,
                clip(userId, 100),
                clip(company, 50),
                clip(conversationId, 64),
                clip(question, 4000),
                clip(answer, 8000),
                blankToNull(sourcesJson),
                blankToNull(clip(comment, 1000)),
                verdict,
                status
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("status", status);
        result.put("verdict", verdict);
        return result;
    }

    public List<Map<String, Object>> listPending(String company) {
        return jdbcTemplate.query(
                "SELECT id, user_id, question, answer, sources_json, comment, verdict, created_at " +
                        "FROM knowledge_supplement WHERE status = 'pending' AND verdict = 'wrong' AND " + COMPANY_EQ + " " +
                        "ORDER BY created_at DESC LIMIT 100",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("userId", rs.getString("user_id"));
                    row.put("question", rs.getString("question"));
                    row.put("answer", rs.getString("answer"));
                    row.put("sourcesJson", rs.getString("sources_json"));
                    row.put("comment", rs.getString("comment"));
                    row.put("verdict", rs.getString("verdict"));
                    if (rs.getTimestamp("created_at") != null) {
                        row.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                    }
                    return row;
                },
                company
        );
    }

    public String exportWrongAsJsonl(String company) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT question, sources_json, comment, company FROM knowledge_supplement " +
                        "WHERE verdict = 'wrong' AND " + COMPANY_EQ + " " +
                        "ORDER BY created_at DESC LIMIT 500",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("question", rs.getString("question"));
                    row.put("sourcesJson", rs.getString("sources_json"));
                    row.put("comment", rs.getString("comment"));
                    row.put("company", rs.getString("company"));
                    return row;
                },
                company
        );
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> row : rows) {
            try {
                out.append(objectMapper.writeValueAsString(toEvalCase(row))).append('\n');
            } catch (Exception ex) {
                throw new IllegalStateException("导出评测题失败", ex);
            }
        }
        return out.toString();
    }

    /**
     * 答错记录转成评测 jsonl 的一行。不把错误答案写进 expectedFacts。
     */
    public RagEvalCase toEvalCase(Map<String, Object> row) {
        String question = row.get("question") == null ? "" : row.get("question").toString();
        RagEvalCase evalCase = new RagEvalCase();
        evalCase.question = question;
        evalCase.expectedFacts = new ArrayList<>();
        evalCase.keywords = KeywordExtractor.extractKeywords(question);
        evalCase.expectedSourceIds = sourceIds(row.get("sourcesJson"));
        evalCase.huohao = KeywordExtractor.extractProductCode(question);
        evalCase.shouldRefuse = false;
        evalCase.company = row.get("company") == null ? null : row.get("company").toString();
        evalCase.comment = row.get("comment") == null ? null : row.get("comment").toString();
        evalCase.example = false;
        return evalCase;
    }

    private List<String> sourceIds(Object sourcesJson) {
        List<String> ids = new ArrayList<>();
        if (sourcesJson == null || sourcesJson.toString().isBlank()) {
            return ids;
        }
        try {
            JsonNode root = objectMapper.readTree(sourcesJson.toString());
            if (!root.isArray()) {
                return ids;
            }
            for (JsonNode node : root) {
                String recordId = text(node, "recordId");
                if (!recordId.isEmpty()) {
                    ids.add(recordId);
                    continue;
                }
                String chunkId = text(node, "chunkId");
                if (!chunkId.isEmpty()) {
                    ids.add(chunkId);
                }
            }
        } catch (Exception ignored) {
            return ids;
        }
        return ids;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
