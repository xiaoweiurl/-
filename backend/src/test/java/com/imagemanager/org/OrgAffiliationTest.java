package com.imagemanager.org;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrgAffiliationTest {

    @Test
    void pathDepthCountsSegments() {
        assertEquals(0, OrgAffiliation.pathDepth(null));
        assertEquals(0, OrgAffiliation.pathDepth("/"));
        assertEquals(1, OrgAffiliation.pathDepth("/宝娜斯集团有限公司"));
        assertEquals(2, OrgAffiliation.pathDepth("/宝娜斯集团有限公司/财务人事"));
        assertEquals(3, OrgAffiliation.pathDepth("/宝娜斯集团有限公司/江苏宝娜斯针织有限公司/生产部"));
    }

    @Test
    void pickPrimaryPrefersDeepestPathNotRoot() {
        Map<Long, String> paths = Map.of(
                1L, "/宝娜斯集团有限公司",
                2L, "/宝娜斯集团有限公司/财务人事",
                3L, "/宝娜斯集团有限公司/江苏宝娜斯针织有限公司/生产部");
        assertEquals(2L, OrgAffiliation.pickPrimaryDeptId(List.of(1L, 2L), paths));
        assertEquals(3L, OrgAffiliation.pickPrimaryDeptId(List.of(1L, 3L), paths));
        assertEquals(1L, OrgAffiliation.pickPrimaryDeptId(List.of(1L), Map.of(1L, "/宝娜斯集团有限公司")));
        assertNull(OrgAffiliation.pickPrimaryDeptId(List.of(), paths));
    }

    @Test
    void pickPrimaryBreaksDepthTieByAvoidingRoot() {
        Map<Long, String> paths = new HashMap<>();
        paths.put(1L, "/集团");
        paths.put(8L, "/财务");
        assertEquals(8L, OrgAffiliation.pickPrimaryDeptId(List.of(1L, 8L), paths));
    }

    @Test
    void mostSpecificDropsAncestorWhenChildAlsoPresent() {
        Map<Long, Set<Long>> descendants = Map.of(
                1L, Set.of(2L, 3L, 4L),
                2L, Set.of(4L),
                3L, Set.of(),
                4L, Set.of());
        Set<Long> visible = OrgAffiliation.mostSpecificDeptIds(List.of(1L, 2L, 4L), descendants);
        assertEquals(Set.of(4L), visible);
        assertFalse(visible.contains(1L));
    }

    @Test
    void mostSpecificKeepsSiblingDepartments() {
        Map<Long, Set<Long>> descendants = Map.of(
                1L, Set.of(2L, 3L),
                2L, Set.of(),
                3L, Set.of());
        Set<Long> visible = OrgAffiliation.mostSpecificDeptIds(List.of(1L, 2L, 3L), descendants);
        assertEquals(Set.of(2L, 3L), visible);
        assertTrue(visible.contains(2L));
        assertTrue(visible.contains(3L));
    }

    @Test
    void canonicalRootNameExpandsShortCompanyLabel() {
        assertEquals("宝娜斯集团有限公司", OrgAffiliation.canonicalRootName(null));
        assertEquals("宝娜斯集团有限公司", OrgAffiliation.canonicalRootName("宝娜斯集团"));
        assertEquals("宝娜斯集团有限公司", OrgAffiliation.canonicalRootName("根部门"));
        assertEquals("宝娜斯集团有限公司", OrgAffiliation.canonicalRootName("宝娜斯集团有限公司"));
    }

    @Test
    void canonicalPathRewritesShortCompanyPrefix() {
        assertEquals("/宝娜斯集团有限公司", OrgAffiliation.canonicalPath("/宝娜斯集团"));
        assertEquals("/宝娜斯集团有限公司/财务人事", OrgAffiliation.canonicalPath("/宝娜斯集团/财务人事"));
        assertEquals("/宝娜斯集团有限公司/江苏宝娜斯针织有限公司/生产部",
                OrgAffiliation.canonicalPath("/宝娜斯集团/江苏宝娜斯针织有限公司/生产部"));
        assertEquals("财务人事", OrgAffiliation.leafName("/宝娜斯集团/财务人事"));
    }

    @Test
    void effectiveParentHangsNonRootZeroParentUnderDingRoot() {
        assertNull(OrgAffiliation.effectiveParentId(1L, 0L));
        assertEquals(1L, OrgAffiliation.effectiveParentId(2L, 0L));
        assertEquals(1L, OrgAffiliation.effectiveParentId(2L, null));
        assertEquals(3L, OrgAffiliation.effectiveParentId(4L, 3L));
    }
}
