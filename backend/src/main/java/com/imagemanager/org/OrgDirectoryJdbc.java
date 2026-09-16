package com.imagemanager.org;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class OrgDirectoryJdbc implements OrgDirectory {

    private final JdbcTemplate jdbcTemplate;

    public OrgDirectoryJdbc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int countActiveContacts(String company) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM org_users WHERE company = ? AND active = TRUE",
                Integer.class, company);
        return count == null ? 0 : count;
    }

    @Override
    public List<OrgContact> findActiveByName(String company, String name) {
        String normalized = OrgNameMatcher.normalize(name);
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<OrgContact> rows = jdbcTemplate.query(
                CONTACT_SELECT
                        + " WHERE u.company = ? AND u.active = TRUE"
                        + " AND replace(replace(trim(u.name), ' ', ''), chr(12288), '') ILIKE ?",
                CONTACT_MAPPER, company, normalized);
        List<OrgContact> exact = new ArrayList<>();
        for (OrgContact row : rows) {
            if (OrgNameMatcher.namesEqual(name, row.getName())) {
                exact.add(row);
            }
        }
        return exact;
    }

    @Override
    public Optional<OrgContact> findByDingUserId(String company, String dingUserId) {
        if (dingUserId == null || dingUserId.isBlank()) {
            return Optional.empty();
        }
        List<OrgContact> rows = jdbcTemplate.query(
                CONTACT_SELECT + " WHERE u.company = ? AND u.ding_userid = ?",
                CONTACT_MAPPER, company, dingUserId.trim());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public Optional<String> findLocalDeptId(String company, Long dingDeptId) {
        if (dingDeptId == null) {
            return Optional.empty();
        }
        List<String> ids = jdbcTemplate.query(
                "SELECT id FROM org_departments WHERE company = ? AND ding_dept_id = ? AND active = TRUE",
                (rs, i) -> rs.getString(1), company, dingDeptId);
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    @Override
    public List<OrgDepartmentView> listActiveDepartments(String company) {
        return jdbcTemplate.query(
                "SELECT d.id, d.ding_dept_id, d.parent_ding_dept_id, d.name, d.path, "
                        + " (SELECT COUNT(*) FROM org_users u WHERE u.company = d.company AND u.active = TRUE "
                        + "  AND u.primary_ding_dept_id = d.ding_dept_id) AS user_count "
                        + " FROM org_departments d WHERE d.company = ? AND d.active = TRUE "
                        + " ORDER BY d.order_num, d.name",
                (rs, i) -> OrgDepartmentView.builder()
                        .id(rs.getString("id"))
                        .dingDeptId(longOrNull(rs, "ding_dept_id"))
                        .parentDingDeptId(longOrNull(rs, "parent_ding_dept_id"))
                        .name(rs.getString("name"))
                        .path(rs.getString("path"))
                        .userCount(rs.getInt("user_count"))
                        .build(),
                company);
    }

    @Override
    public List<OrgContact> searchContacts(String company, String keyword, int limit) {
        int size = Math.min(Math.max(limit, 1), 100);
        if (keyword == null || keyword.isBlank()) {
            return jdbcTemplate.query(
                    CONTACT_SELECT + " WHERE u.company = ? AND u.active = TRUE ORDER BY u.name LIMIT ?",
                    CONTACT_MAPPER, company, size);
        }
        String like = "%" + keyword.trim() + "%";
        return jdbcTemplate.query(
                CONTACT_SELECT + " WHERE u.company = ? AND u.active = TRUE "
                        + " AND (u.name ILIKE ? OR u.job_title ILIKE ? OR d.name ILIKE ?) "
                        + " ORDER BY u.name LIMIT ?",
                CONTACT_MAPPER, company, like, like, like, size);
    }

    @Override
    public void bindLocalUser(String orgUserId, String localUserId) {
        jdbcTemplate.update(
                "UPDATE org_users SET local_user_id = ?, updated_at = NOW() WHERE id = ?",
                localUserId, orgUserId);
    }

    private static final String CONTACT_SELECT =
            "SELECT u.id AS id, u.ding_userid, u.ding_unionid, u.name, u.job_title, u.email, u.mobile, "
                    + " u.avatar_url, u.primary_ding_dept_id, u.local_user_id, u.active, "
                    + " d.name AS dept_name, d.path AS dept_path "
                    + " FROM org_users u "
                    + " LEFT JOIN org_departments d ON d.company = u.company "
                    + "  AND d.ding_dept_id = u.primary_ding_dept_id";

    private static final RowMapper<OrgContact> CONTACT_MAPPER = (rs, i) -> OrgContact.builder()
            .id(rs.getString("id"))
            .dingUserId(rs.getString("ding_userid"))
            .dingUnionId(rs.getString("ding_unionid"))
            .name(rs.getString("name"))
            .jobTitle(rs.getString("job_title"))
            .email(rs.getString("email"))
            .mobile(rs.getString("mobile"))
            .avatarUrl(rs.getString("avatar_url"))
            .primaryDingDeptId(longOrNull(rs, "primary_ding_dept_id"))
            .deptName(rs.getString("dept_name"))
            .deptPath(rs.getString("dept_path"))
            .localUserId(rs.getString("local_user_id"))
            .active(rs.getBoolean("active"))
            .build();

    private static Long longOrNull(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
