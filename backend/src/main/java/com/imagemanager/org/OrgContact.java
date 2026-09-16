package com.imagemanager.org;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本地缓存的钉钉通讯录联系人。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgContact {
    private String id;
    private String dingUserId;
    private String dingUnionId;
    private String name;
    private String jobTitle;
    private String email;
    private String mobile;
    private String avatarUrl;
    private Long primaryDingDeptId;
    private String deptName;
    private String deptPath;
    private String localUserId;
    private boolean active;
}
