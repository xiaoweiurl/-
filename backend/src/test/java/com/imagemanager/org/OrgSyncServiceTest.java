package com.imagemanager.org;

import com.imagemanager.dingtalk.DingDepartment;
import com.imagemanager.dingtalk.DingUser;
import com.imagemanager.dto.OrgDepartmentNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrgSyncServiceTest {

    @Test
    void mergeUsersDedupesByUseridAndUnionsDeptIds() {
        DingUser a = DingUser.builder().userid("u1").name("张三").title(null)
                .deptIdList(new java.util.ArrayList<>(List.of(1L))).build();
        DingUser b = DingUser.builder().userid("u1").name("张三").title("工程师")
                .deptIdList(new java.util.ArrayList<>(List.of(2L))).email("a@b.com").build();
        DingUser c = DingUser.builder().userid("u2").name("李四").deptIdList(List.of(1L)).build();
        Map<String, DingUser> merged = OrgSyncService.mergeUsers(List.of(a, b, c));
        assertEquals(2, merged.size());
        assertEquals("工程师", merged.get("u1").getTitle());
        assertTrue(merged.get("u1").getDeptIdList().containsAll(List.of(1L, 2L)));
        assertEquals("a@b.com", merged.get("u1").getEmail());
    }

    @Test
    void buildPathsConcatenatesAncestors() {
        List<DingDepartment> depts = List.of(
                DingDepartment.builder().deptId(1L).name("宝娜斯集团").parentId(null).build(),
                DingDepartment.builder().deptId(2L).name("技术部").parentId(1L).build(),
                DingDepartment.builder().deptId(3L).name("前端组").parentId(2L).build());
        Map<Long, String> paths = OrgSyncService.buildPaths(depts);
        assertEquals("/宝娜斯集团有限公司", paths.get(1L));
        assertEquals("/宝娜斯集团有限公司/技术部", paths.get(2L));
        assertEquals("/宝娜斯集团有限公司/技术部/前端组", paths.get(3L));
    }

    @Test
    void buildPathsHangsParentZeroFirstLevelUnderLegalRoot() {
        List<DingDepartment> depts = List.of(
                DingDepartment.builder().deptId(1L).name("宝娜斯集团").parentId(0L).build(),
                DingDepartment.builder().deptId(2L).name("财务人事").parentId(0L).build(),
                DingDepartment.builder().deptId(3L).name("技术研发").parentId(1L).build());
        Map<Long, String> paths = OrgSyncService.buildPaths(depts);
        assertEquals("/宝娜斯集团有限公司", paths.get(1L));
        assertEquals("/宝娜斯集团有限公司/财务人事", paths.get(2L));
        assertEquals("/宝娜斯集团有限公司/技术研发", paths.get(3L));
    }

    @Test
    void departmentTreeHangsPeopleOnMostSpecificDeptNotRoot() {
        List<OrgDirectory.OrgDepartmentView> depts = List.of(
                dept("d1", 1L, null, "宝娜斯集团有限公司", "/宝娜斯集团有限公司"),
                dept("d2", 2L, 1L, "财务人事", "/宝娜斯集团有限公司/财务人事"),
                dept("d3", 3L, 1L, "江苏宝娜斯针织有限公司", "/宝娜斯集团有限公司/江苏宝娜斯针织有限公司"),
                dept("d4", 4L, 3L, "生产部", "/宝娜斯集团有限公司/江苏宝娜斯针织有限公司/生产部"));
        List<OrgDirectory.OrgDeptMemberRow> rows = List.of(
                member("u-finance", "张三", "会计", 1L),
                member("u-finance", "张三", "会计", 2L),
                member("u-factory", "李四", "车间主任", 1L),
                member("u-factory", "李四", "车间主任", 3L),
                member("u-factory", "李四", "车间主任", 4L),
                member("u-hq-only", "王五", "总裁办", 1L));

        List<OrgDepartmentNode> tree = OrgSyncService.buildDepartmentTree(depts, rows);
        assertEquals(1, tree.size());
        OrgDepartmentNode root = tree.get(0);
        assertEquals("宝娜斯集团有限公司", root.getName());
        assertEquals(1, root.getUserCount());
        assertEquals("王五", root.getMembers().get(0).getName());

        OrgDepartmentNode finance = childNamed(root, "财务人事");
        assertEquals(1, finance.getUserCount());
        assertEquals("张三", finance.getMembers().get(0).getName());
        assertEquals("会计", finance.getMembers().get(0).getJobTitle());

        OrgDepartmentNode jiangsu = childNamed(root, "江苏宝娜斯针织有限公司");
        assertEquals(0, jiangsu.getUserCount());
        assertTrue(jiangsu.getMembers().isEmpty());

        OrgDepartmentNode production = childNamed(jiangsu, "生产部");
        assertEquals(1, production.getUserCount());
        assertEquals("李四", production.getMembers().get(0).getName());
    }

    @Test
    void emptyDepartmentStaysEmpty() {
        List<OrgDirectory.OrgDepartmentView> depts = List.of(
                dept("d1", 1L, null, "宝娜斯集团有限公司", "/宝娜斯集团有限公司"),
                dept("d2", 2L, 1L, "空部门", "/宝娜斯集团有限公司/空部门"));
        List<OrgDepartmentNode> tree = OrgSyncService.buildDepartmentTree(depts, List.of());
        OrgDepartmentNode empty = childNamed(tree.get(0), "空部门");
        assertEquals(0, empty.getUserCount());
        assertTrue(empty.getMembers().isEmpty());
    }

    @Test
    void shortRootNameAndParentZeroBecomeLegalCompanyTree() {
        List<OrgDirectory.OrgDepartmentView> depts = List.of(
                dept("d1", 1L, 0L, "宝娜斯集团", "/宝娜斯集团"),
                dept("d2", 2L, 0L, "财务人事", "/财务人事"),
                dept("d3", 3L, 1L, "技术研发", "/宝娜斯集团/技术研发"));
        List<OrgDepartmentNode> tree = OrgSyncService.buildDepartmentTree(depts, List.of(
                member("u1", "赵六", "会计", 2L)));
        assertEquals(1, tree.size());
        OrgDepartmentNode root = tree.get(0);
        assertEquals("宝娜斯集团有限公司", root.getName());
        assertEquals("/宝娜斯集团有限公司", root.getPath());
        assertEquals(0, root.getUserCount());
        OrgDepartmentNode finance = childNamed(root, "财务人事");
        assertEquals("/宝娜斯集团有限公司/财务人事", finance.getPath());
        assertEquals(1, finance.getUserCount());
        assertEquals("赵六", finance.getMembers().get(0).getName());
        assertEquals("技术研发", childNamed(root, "技术研发").getName());
    }

    private static OrgDirectory.OrgDepartmentView dept(String id, Long dingId, Long parent, String name, String path) {
        return OrgDirectory.OrgDepartmentView.builder()
                .id(id)
                .dingDeptId(dingId)
                .parentDingDeptId(parent)
                .name(name)
                .path(path)
                .userCount(0)
                .build();
    }

    private static OrgDirectory.OrgDeptMemberRow member(String dingUserId, String name, String title, Long deptId) {
        return OrgDirectory.OrgDeptMemberRow.builder()
                .dingUserId(dingUserId)
                .name(name)
                .jobTitle(title)
                .dingDeptId(deptId)
                .build();
    }

    private static OrgDepartmentNode childNamed(OrgDepartmentNode parent, String name) {
        return parent.getChildren().stream()
                .filter(n -> name.equals(n.getName()))
                .findFirst()
                .orElseThrow();
    }
}
