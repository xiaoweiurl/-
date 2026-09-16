package com.imagemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 部门树下的通讯录成员（叶子行）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgDepartmentMember {
    private String dingtalkUserid;
    private String name;
    private String jobTitle;
    private boolean alreadyRegistered;
}
