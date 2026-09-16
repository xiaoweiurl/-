package com.imagemanager.org;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dingtalk.DingDepartment;
import com.imagemanager.dingtalk.DingTalkClient;
import com.imagemanager.dingtalk.DingTalkException;
import com.imagemanager.dingtalk.DingUser;
import com.imagemanager.dto.OrgDepartmentNode;
import com.imagemanager.dto.OrgSyncResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 钉钉组织同步：部门树 + 通讯录写入本地表。不创建本地登录账号（账号由姓名注册产生）。
 */
@Slf4j
@Service
public class OrgSyncService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DingTalkClient dingTalkClient;
    private final DingTalkProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;
    private final OrgDirectory orgDirectory;
    private final ReentrantLock syncLock = new ReentrantLock();

    public OrgSyncService(DingTalkClient dingTalkClient,
                          DingTalkProperties properties,
                          JdbcTemplate jdbcTemplate,
                          PlatformTransactionManager transactionManager,
                          OrgDirectory orgDirectory) {
        this.dingTalkClient = dingTalkClient;
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.orgDirectory = orgDirectory;
    }

    public boolean isConfigured() {
        return dingTalkClient.isConfigured();
    }

    public OrgSyncResult status(String company) {
        String c = resolveCompany(company);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT last_sync_at, last_status, last_message, dept_count, user_count, duration_ms "
                        + "FROM org_sync_state WHERE company = ?", c);
        OrgSyncResult.OrgSyncResultBuilder builder = OrgSyncResult.builder()
                .company(c)
                .status("never")
                .message(dingTalkClient.isConfigured() ? "尚未同步" : DingTalkException.notConfigured().getMessage())
                .deptCount(0)
                .userCount(0);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            builder.status(str(row.get("last_status")))
                    .message(str(row.get("last_message")))
                    .deptCount(intVal(row.get("dept_count")))
                    .userCount(intVal(row.get("user_count")))
                    .durationMs(longVal(row.get("duration_ms")))
                    .lastSyncAt(formatTs(row.get("last_sync_at")));
        }
        return builder.build();
    }

    public List<OrgDepartmentNode> departmentTree(String company) {
        List<OrgDirectory.OrgDepartmentView> flat = orgDirectory.listActiveDepartments(resolveCompany(company));
        Map<Long, OrgDepartmentNode> nodes = new LinkedHashMap<>();
        for (OrgDirectory.OrgDepartmentView view : flat) {
            nodes.put(view.getDingDeptId(), OrgDepartmentNode.builder()
                    .id(view.getId())
                    .dingDeptId(view.getDingDeptId())
                    .parentDingDeptId(view.getParentDingDeptId())
                    .name(view.getName())
                    .path(view.getPath())
                    .userCount(view.getUserCount())
                    .children(new ArrayList<>())
                    .build());
        }
        List<OrgDepartmentNode> roots = new ArrayList<>();
        for (OrgDepartmentNode node : nodes.values()) {
            OrgDepartmentNode parent = node.getParentDingDeptId() == null
                    ? null : nodes.get(node.getParentDingDeptId());
            if (parent == null || parent.getDingDeptId().equals(node.getDingDeptId())) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    public OrgSyncResult sync() {
        if (!syncLock.tryLock()) {
            throw new DingTalkException("已有钉钉组织同步任务在执行，请稍后再试");
        }
        long started = System.currentTimeMillis();
        String company = resolveCompany(null);
        try {
            if (!dingTalkClient.isConfigured()) {
                throw DingTalkException.notConfigured();
            }
            List<DingDepartment> departments = dingTalkClient.fetchAllDepartments();
            List<Long> deptIds = departments.stream()
                    .map(DingDepartment::getDeptId)
                    .filter(Objects::nonNull)
                    .toList();
            List<DingUser> rawUsers = dingTalkClient.fetchUsersInDepartments(deptIds);
            Map<String, DingUser> uniqueUsers = mergeUsers(rawUsers);

            OrgSyncResult persisted = txTemplate.execute(status -> persist(company, departments, uniqueUsers));
            if (persisted == null) {
                throw new DingTalkException("同步事务未提交");
            }
            persisted.setDurationMs(System.currentTimeMillis() - started);
            writeState(company, persisted);
            log.info("钉钉组织同步完成: company={}, depts={}, users={}, {}ms",
                    company, persisted.getDeptCount(), persisted.getUserCount(), persisted.getDurationMs());
            return persisted;
        } catch (RuntimeException e) {
            OrgSyncResult failed = OrgSyncResult.builder()
                    .company(company)
                    .status("failed")
                    .message(e.getMessage())
                    .durationMs(System.currentTimeMillis() - started)
                    .build();
            try {
                writeState(company, failed);
            } catch (Exception ex) {
                log.warn("写入钉钉同步失败状态出错: {}", ex.getMessage());
            }
            throw e;
        } finally {
            syncLock.unlock();
        }
    }

    static Map<String, DingUser> mergeUsers(List<DingUser> rawUsers) {
        Map<String, DingUser> unique = new LinkedHashMap<>();
        if (rawUsers == null) {
            return unique;
        }
        for (DingUser user : rawUsers) {
            if (user == null || user.getUserid() == null || user.getUserid().isBlank()) {
                continue;
            }
            DingUser existing = unique.get(user.getUserid());
            if (existing == null) {
                unique.put(user.getUserid(), user);
                continue;
            }
            Set<Long> deptIds = new HashSet<>();
            if (existing.getDeptIdList() != null) {
                deptIds.addAll(existing.getDeptIdList());
            }
            if (user.getDeptIdList() != null) {
                deptIds.addAll(user.getDeptIdList());
            }
            existing.setDeptIdList(new ArrayList<>(deptIds));
            if (isBlank(existing.getTitle()) && !isBlank(user.getTitle())) {
                existing.setTitle(user.getTitle());
            }
            if (isBlank(existing.getEmail()) && !isBlank(user.getEmail())) {
                existing.setEmail(user.getEmail());
            }
            if (isBlank(existing.getMobile()) && !isBlank(user.getMobile())) {
                existing.setMobile(user.getMobile());
            }
        }
        return unique;
    }

    private OrgSyncResult persist(String company, List<DingDepartment> departments, Map<String, DingUser> users) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp ts = Timestamp.valueOf(now);
        Map<Long, String> paths = buildPaths(departments);

        Set<Long> seenDeptIds = new HashSet<>();
        for (DingDepartment dept : departments) {
            if (dept.getDeptId() == null || dept.getDeptId() < 0) {
                continue;
            }
            seenDeptIds.add(dept.getDeptId());
            jdbcTemplate.update(
                    "INSERT INTO org_departments (id, ding_dept_id, parent_ding_dept_id, name, path, company, order_num, active, synced_at, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, NOW(), NOW()) "
                            + "ON CONFLICT (company, ding_dept_id) DO UPDATE SET "
                            + " parent_ding_dept_id = EXCLUDED.parent_ding_dept_id, "
                            + " name = EXCLUDED.name, path = EXCLUDED.path, order_num = EXCLUDED.order_num, "
                            + " active = TRUE, synced_at = EXCLUDED.synced_at, updated_at = NOW()",
                    UUID.randomUUID().toString(), dept.getDeptId(), dept.getParentId(),
                    nvl(dept.getName(), "未命名部门"),
                    paths.getOrDefault(dept.getDeptId(), "/" + nvl(dept.getName(), "未命名部门")),
                    company, dept.getOrder() == null ? 0 : dept.getOrder(), ts);
        }
        if (!seenDeptIds.isEmpty()) {
            String in = seenDeptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            jdbcTemplate.update(
                    "UPDATE org_departments SET active = FALSE, updated_at = NOW() "
                            + "WHERE company = ? AND ding_dept_id NOT IN (" + in + ")",
                    company);
        }

        Map<String, String> existingIds = new HashMap<>();
        jdbcTemplate.query(
                "SELECT ding_userid, id FROM org_users WHERE company = ?",
                rs -> {
                    existingIds.put(rs.getString(1), rs.getString(2));
                }, company);

        Set<String> seenUserIds = new HashSet<>();
        for (DingUser user : users.values()) {
            seenUserIds.add(user.getUserid());
            Long primaryDept = primaryDeptId(user);
            String orgUserId = existingIds.get(user.getUserid());
            if (orgUserId == null) {
                orgUserId = UUID.randomUUID().toString();
                jdbcTemplate.update(
                        "INSERT INTO org_users (id, ding_userid, ding_unionid, name, job_title, mobile, email, avatar_url, company, "
                                + " primary_ding_dept_id, active, synced_at, created_at, updated_at) "
                                +                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, NOW(), NOW())",
                        orgUserId, user.getUserid(), user.getUnionid(), nvl(user.getName(), user.getUserid()),
                        user.getTitle(), user.getMobile(), user.getEmail(), user.getAvatar(),
                        company, primaryDept, ts);
            } else {
                jdbcTemplate.update(
                        "UPDATE org_users SET ding_unionid = ?, name = ?, job_title = ?, mobile = ?, email = ?, avatar_url = ?, "
                                + " primary_ding_dept_id = ?, active = TRUE, synced_at = ?, updated_at = NOW() "
                                + " WHERE company = ? AND ding_userid = ?",
                        user.getUnionid(), nvl(user.getName(), user.getUserid()), user.getTitle(),
                        user.getMobile(), user.getEmail(), user.getAvatar(), primaryDept, ts,
                        company, user.getUserid());
            }
            jdbcTemplate.update("DELETE FROM org_user_departments WHERE org_user_id = ?", orgUserId);
            List<Long> deptIds = user.getDeptIdList() == null ? List.of() : user.getDeptIdList();
            for (Long deptId : deptIds) {
                if (deptId == null || deptId < 0) {
                    continue;
                }
                jdbcTemplate.update(
                        "INSERT INTO org_user_departments (org_user_id, ding_dept_id) VALUES (?, ?) "
                                + "ON CONFLICT DO NOTHING",
                        orgUserId, deptId);
            }
        }
        if (!seenUserIds.isEmpty()) {
            List<String> ids = new ArrayList<>(seenUserIds);
            String placeholders = ids.stream().map(s -> "?").collect(Collectors.joining(","));
            List<Object> params = new ArrayList<>();
            params.add(company);
            params.addAll(ids);
            jdbcTemplate.update(
                    "UPDATE org_users SET active = FALSE, updated_at = NOW() "
                            + "WHERE company = ? AND ding_userid NOT IN (" + placeholders + ")",
                    params.toArray());
        } else {
            jdbcTemplate.update(
                    "UPDATE org_users SET active = FALSE, updated_at = NOW() WHERE company = ?",
                    company);
        }

        return OrgSyncResult.builder()
                .company(company)
                .status("success")
                .message("同步完成")
                .deptCount(seenDeptIds.size())
                .userCount(seenUserIds.size())
                .lastSyncAt(now.format(ISO))
                .build();
    }

    static Map<Long, String> buildPaths(List<DingDepartment> departments) {
        Map<Long, DingDepartment> byId = new HashMap<>();
        for (DingDepartment dept : departments) {
            if (dept.getDeptId() != null) {
                byId.put(dept.getDeptId(), dept);
            }
        }
        Map<Long, String> paths = new HashMap<>();
        for (DingDepartment dept : departments) {
            if (dept.getDeptId() != null) {
                paths.put(dept.getDeptId(), pathOf(dept.getDeptId(), byId, new HashSet<>()));
            }
        }
        return paths;
    }

    private static String pathOf(Long deptId, Map<Long, DingDepartment> byId, Set<Long> stack) {
        DingDepartment dept = byId.get(deptId);
        if (dept == null) {
            return "";
        }
        if (!stack.add(deptId)) {
            return "/" + nvl(dept.getName(), "未命名部门");
        }
        if (dept.getParentId() == null || dept.getParentId().equals(deptId) || !byId.containsKey(dept.getParentId())) {
            return "/" + nvl(dept.getName(), "未命名部门");
        }
        return pathOf(dept.getParentId(), byId, stack) + "/" + nvl(dept.getName(), "未命名部门");
    }

    private static Long primaryDeptId(DingUser user) {
        if (user.getDeptIdList() == null || user.getDeptIdList().isEmpty()) {
            return 1L;
        }
        return user.getDeptIdList().get(0);
    }

    private void writeState(String company, OrgSyncResult result) {
        txTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                "INSERT INTO org_sync_state (company, last_sync_at, last_status, last_message, dept_count, user_count, duration_ms) "
                        + "VALUES (?, NOW(), ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (company) DO UPDATE SET last_sync_at = NOW(), last_status = EXCLUDED.last_status, "
                        + " last_message = EXCLUDED.last_message, dept_count = EXCLUDED.dept_count, "
                        + " user_count = EXCLUDED.user_count, duration_ms = EXCLUDED.duration_ms",
                company, result.getStatus(), result.getMessage(),
                result.getDeptCount(), result.getUserCount(), result.getDurationMs()));
    }

    private String resolveCompany(String company) {
        if (company != null && !company.isBlank()) {
            return company.trim();
        }
        String configured = properties.getCompany();
        return configured == null || configured.isBlank() ? "宝娜斯集团" : configured.trim();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String nvl(String s, String fallback) {
        return isBlank(s) ? fallback : s;
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static int intVal(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static Long longVal(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return null;
    }

    private static String formatTs(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime().format(ISO);
        }
        return String.valueOf(v);
    }
}
