package com.imagemanager.org;

import com.imagemanager.dingtalk.DingDepartment;
import com.imagemanager.dingtalk.DingUser;
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
        assertEquals("/宝娜斯集团", paths.get(1L));
        assertEquals("/宝娜斯集团/技术部", paths.get(2L));
        assertEquals("/宝娜斯集团/技术部/前端组", paths.get(3L));
    }
}
