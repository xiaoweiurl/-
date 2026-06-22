package com.imagemanager.service.impl;

import com.imagemanager.service.PositionKnowledgeCardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PositionKnowledgeCardServiceImpl implements PositionKnowledgeCardService {

    private static final Logger log = LoggerFactory.getLogger(PositionKnowledgeCardServiceImpl.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public List<Map<String, Object>> listCards(String userId, String company, String keyword, String department, String team, String status, int page, int size) {
        StringBuilder sql = new StringBuilder("SELECT * FROM position_knowledge_cards WHERE 1=1");
        List<Object> params = new ArrayList<>();

        // 数据隔离：按公司过滤
        if (company != null && !company.isEmpty()) {
            sql.append(" AND (company = ? OR company IS NULL)");
            params.add(company);
        }

        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (position_name ILIKE ? OR position_holder ILIKE ? OR department ILIKE ?)");
            String kw = "%" + keyword + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        if (department != null && !department.isEmpty()) {
            sql.append(" AND department = ?");
            params.add(department);
        }
        if (team != null && !team.isEmpty()) {
            sql.append(" AND team = ?");
            params.add(team);
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }

        sql.append(" ORDER BY updated_at DESC");
        int offset = (page - 1) * size;
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @Override
    public int countCards(String userId, String company, String keyword, String department, String team, String status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM position_knowledge_cards WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (company != null && !company.isEmpty()) {
            sql.append(" AND (company = ? OR company IS NULL)");
            params.add(company);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (position_name ILIKE ? OR position_holder ILIKE ? OR department ILIKE ?)");
            String kw = "%" + keyword + "%";
            params.add(kw);
            params.add(kw);
            params.add(kw);
        }
        if (department != null && !department.isEmpty()) {
            sql.append(" AND department = ?");
            params.add(department);
        }
        if (team != null && !team.isEmpty()) {
            sql.append(" AND team = ?");
            params.add(team);
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND status = ?");
            params.add(status);
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    @Override
    public Map<String, Object> createCard(String userId, String company, Map<String, Object> body) {
        // 生成卡片编号
        String cardNo = generateCardNo();

        String sql = "INSERT INTO position_knowledge_cards " +
                "(card_no, submit_date, department, position_name, position_holder, report_to, " +
                "team, position_nature, core_responsibilities, auxiliary_responsibilities, " +
                "key_deliverables, hard_skills, soft_skills, " +
                "upstream_inputs, downstream_outputs, " +
                "completed_work, ongoing_work, bottlenecks, support_needed, " +
                "improvement_direction, process_optimization, tool_resource_needs, " +
                "additional_notes, status, company, user_id) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        jdbcTemplate.update(sql,
                cardNo,
                getString(body, "submitDate"),
                getString(body, "department"),
                getString(body, "positionName"),
                getString(body, "positionHolder"),
                getString(body, "reportTo"),
                getString(body, "team"),
                getString(body, "positionNature"),
                getString(body, "coreResponsibilities"),
                getString(body, "auxiliaryResponsibilities"),
                getString(body, "keyDeliverables"),
                getString(body, "hardSkills"),
                getString(body, "softSkills"),
                getString(body, "upstreamInputs"),
                getString(body, "downstreamOutputs"),
                getString(body, "completedWork"),
                getString(body, "ongoingWork"),
                getString(body, "bottlenecks"),
                getString(body, "supportNeeded"),
                getString(body, "improvementDirection"),
                getString(body, "processOptimization"),
                getString(body, "toolResourceNeeds"),
                getString(body, "additionalNotes"),
                getStringDefault(body, "status", "draft"),
                company,
                userId
        );

        log.info("创建岗位知识卡片: cardNo={}, positionName={}, userId={}, company={}", cardNo, getString(body, "positionName"), userId, company);

        // 返回创建的卡片
        return getCardByCardNo(cardNo);
    }

    @Override
    public Map<String, Object> updateCard(String id, String userId, String company, Map<String, Object> body) {
        // 先检查权限
        Map<String, Object> existing = getCard(id, userId, company);
        if (existing == null) {
            throw new RuntimeException("卡片不存在或无权限");
        }

        StringBuilder sql = new StringBuilder("UPDATE position_knowledge_cards SET ");
        List<Object> params = new ArrayList<>();
        List<String> sets = new ArrayList<>();

        // 动态构建UPDATE语句
        addIfPresent(sets, params, "submit_date", body.get("submitDate"));
        addIfPresent(sets, params, "department", body.get("department"));
        addIfPresent(sets, params, "position_name", body.get("positionName"));
        addIfPresent(sets, params, "position_holder", body.get("positionHolder"));
        addIfPresent(sets, params, "report_to", body.get("reportTo"));
        addIfPresent(sets, params, "team", body.get("team"));
        addIfPresent(sets, params, "position_nature", body.get("positionNature"));
        addIfPresent(sets, params, "core_responsibilities", body.get("coreResponsibilities"));
        addIfPresent(sets, params, "auxiliary_responsibilities", body.get("auxiliaryResponsibilities"));
        addIfPresent(sets, params, "key_deliverables", body.get("keyDeliverables"));
        addIfPresent(sets, params, "hard_skills", body.get("hardSkills"));
        addIfPresent(sets, params, "soft_skills", body.get("softSkills"));
        addIfPresent(sets, params, "upstream_inputs", body.get("upstreamInputs"));
        addIfPresent(sets, params, "downstream_outputs", body.get("downstreamOutputs"));
        addIfPresent(sets, params, "completed_work", body.get("completedWork"));
        addIfPresent(sets, params, "ongoing_work", body.get("ongoingWork"));
        addIfPresent(sets, params, "bottlenecks", body.get("bottlenecks"));
        addIfPresent(sets, params, "support_needed", body.get("supportNeeded"));
        addIfPresent(sets, params, "improvement_direction", body.get("improvementDirection"));
        addIfPresent(sets, params, "process_optimization", body.get("processOptimization"));
        addIfPresent(sets, params, "tool_resource_needs", body.get("toolResourceNeeds"));
        addIfPresent(sets, params, "additional_notes", body.get("additionalNotes"));
        addIfPresent(sets, params, "status", body.get("status"));

        if (sets.isEmpty()) {
            return existing;
        }

        sets.add("updated_at = NOW()");
        sql.append(String.join(", ", sets));
        sql.append(" WHERE id = ?::uuid");
        params.add(id);

        // 权限过滤
        if (company != null && !company.isEmpty()) {
            sql.append(" AND (company = ? OR company IS NULL)");
            params.add(company);
        }

        jdbcTemplate.update(sql.toString(), params.toArray());
        log.info("更新岗位知识卡片: id={}, userId={}", id, userId);

        return getCard(id, userId, company);
    }

    @Override
    public void deleteCard(String id, String userId, String company) {
        String sql = "DELETE FROM position_knowledge_cards WHERE id = ?::uuid";
        List<Object> params = new ArrayList<>();
        params.add(id);

        if (company != null && !company.isEmpty()) {
            sql += " AND (company = ? OR company IS NULL)";
            params.add(company);
        }

        int rows = jdbcTemplate.update(sql, params.toArray());
        log.info("删除岗位知识卡片: id={}, rows={}", id, rows);
    }

    @Override
    public Map<String, Object> getCard(String id, String userId, String company) {
        String sql = "SELECT * FROM position_knowledge_cards WHERE id = ?::uuid";
        List<Object> params = new ArrayList<>();
        params.add(id);

        if (company != null && !company.isEmpty()) {
            sql += " AND (company = ? OR company IS NULL)";
            params.add(company);
        }

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, params.toArray());
        return results.isEmpty() ? null : results.get(0);
    }

    private Map<String, Object> getCardByCardNo(String cardNo) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT * FROM position_knowledge_cards WHERE card_no = ?", cardNo);
        return results.isEmpty() ? null : results.get(0);
    }

    private String generateCardNo() {
        // KC-yyyyMMdd-4位随机数
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String rand = String.format("%04d", new Random().nextInt(10000));
        return "KC-" + date + "-" + rand;
    }

    private String getString(Map<String, Object> body, String key) {
        Object val = body.get(key);
        return val != null ? val.toString() : null;
    }

    private String getStringDefault(Map<String, Object> body, String key, String defaultValue) {
        Object val = body.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private void addIfPresent(List<String> sets, List<Object> params, String column, Object value) {
        if (value != null) {
            sets.add(column + " = ?");
            params.add(value.toString());
        }
    }
}
