package com.imagemanager.org;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 部门归属规则：展示/主部门取最具体层级，避免全员挂在集团根节点。
 */
final class OrgAffiliation {

    static final long ROOT_DEPT_ID = 1L;
    /** 钉钉通讯录根部门对外展示名（全称），与 users.company 租户键「宝娜斯集团」区分。 */
    static final String ROOT_LEGAL_NAME = "宝娜斯集团有限公司";
    static final String ROOT_SHORT_NAME = "宝娜斯集团";

    private OrgAffiliation() {
    }

    static int pathDepth(String path) {
        if (path == null || path.isBlank()) {
            return 0;
        }
        int depth = 0;
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                depth++;
            }
        }
        return depth;
    }

    /**
     * 在所属部门中取路径最深的一个作为主部门/展示部门。
     * 深度相同时避开钉钉根部门 1，避免标签停在「宝娜斯集团」。
     */
    static Long pickPrimaryDeptId(Collection<Long> deptIds, Map<Long, String> paths) {
        if (deptIds == null || deptIds.isEmpty()) {
            return null;
        }
        Long best = null;
        int bestDepth = -1;
        for (Long id : deptIds) {
            if (id == null || id < 0) {
                continue;
            }
            String path = paths == null ? null : paths.get(id);
            int depth = pathDepth(path);
            if (best == null
                    || depth > bestDepth
                    || (depth == bestDepth && isRoot(best) && !isRoot(id))) {
                best = id;
                bestDepth = depth;
            }
        }
        return best;
    }

    /**
     * 同一人同时属于上级与下级时，只保留最具体的部门（下级），
     * 这样集团根节点不会堆上全部员工。
     */
    static Set<Long> mostSpecificDeptIds(Collection<Long> deptIds, Map<Long, Set<Long>> descendantsByDept) {
        Set<Long> owned = new HashSet<>();
        if (deptIds != null) {
            for (Long id : deptIds) {
                if (id != null) {
                    owned.add(id);
                }
            }
        }
        Set<Long> visible = new HashSet<>();
        for (Long id : owned) {
            Set<Long> descendants = descendantsByDept == null
                    ? Set.of()
                    : descendantsByDept.getOrDefault(id, Set.of());
            boolean hasDeeper = false;
            for (Long other : owned) {
                if (!id.equals(other) && descendants.contains(other)) {
                    hasDeeper = true;
                    break;
                }
            }
            if (!hasDeeper) {
                visible.add(id);
            }
        }
        return visible;
    }

    static boolean isRoot(Long id) {
        return id != null && id == ROOT_DEPT_ID;
    }

    static String canonicalRootName(String name) {
        if (name == null || name.isBlank()) {
            return ROOT_LEGAL_NAME;
        }
        String trimmed = name.trim();
        if ("根部门".equals(trimmed) || ROOT_SHORT_NAME.equals(trimmed)) {
            return ROOT_LEGAL_NAME;
        }
        return trimmed;
    }

    static String displayName(Long dingDeptId, String name) {
        if (isRoot(dingDeptId)) {
            return canonicalRootName(name);
        }
        if (name == null || name.isBlank()) {
            return "未命名部门";
        }
        return name;
    }

    /**
     * 钉钉根部门 parent_id 常为 0；部分一级部门也会标 0。
     * 根部门（dept_id=1）无父级；其余缺失/0/自指的父级一律挂到 1。
     */
    static Long effectiveParentId(Long dingDeptId, Long parentId) {
        if (isRoot(dingDeptId)) {
            return null;
        }
        if (parentId == null || parentId <= 0 || parentId.equals(dingDeptId)) {
            return ROOT_DEPT_ID;
        }
        return parentId;
    }

    static String canonicalPath(String path) {
        if (path == null || path.isBlank()) {
            return "/" + ROOT_LEGAL_NAME;
        }
        String value = path.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        if ("/".equals(value) || ("/" + ROOT_SHORT_NAME).equals(value) || "/根部门".equals(value)) {
            return "/" + ROOT_LEGAL_NAME;
        }
        String shortPrefix = "/" + ROOT_SHORT_NAME + "/";
        if (value.startsWith(shortPrefix) && !value.startsWith("/" + ROOT_LEGAL_NAME + "/")) {
            return "/" + ROOT_LEGAL_NAME + "/" + value.substring(shortPrefix.length());
        }
        if (value.startsWith("/根部门/")) {
            return "/" + ROOT_LEGAL_NAME + "/" + value.substring("/根部门/".length());
        }
        return value;
    }

    static String leafName(String path) {
        String canonical = canonicalPath(path);
        int slash = canonical.lastIndexOf('/');
        if (slash < 0 || slash == canonical.length() - 1) {
            return ROOT_LEGAL_NAME;
        }
        return canonical.substring(slash + 1);
    }

    static boolean isShortCompanyLabel(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String trimmed = name.trim();
        return ROOT_SHORT_NAME.equals(trimmed) || "根部门".equals(trimmed);
    }
}
