package com.imagemanager.dingtalk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 钉钉部门（topapi/v2/department）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DingDepartment {
    private Long deptId;
    private Long parentId;
    private String name;
    private Integer order;
}
