package com.imagemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 组织部门树节点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgDepartmentNode {
    private String id;
    private Long dingDeptId;
    private Long parentDingDeptId;
    private String name;
    private String path;
    private Integer userCount;
    @Builder.Default
    private List<OrgDepartmentMember> members = new ArrayList<>();
    @Builder.Default
    private List<OrgDepartmentNode> children = new ArrayList<>();
}
