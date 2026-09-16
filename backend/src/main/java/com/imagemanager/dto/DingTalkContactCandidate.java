package com.imagemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 注册时钉钉同名候选人（409 返回）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DingTalkContactCandidate {
    private String dingtalkUserid;
    private String name;
    private String jobTitle;
    private String deptName;
    private String deptPath;
    private boolean alreadyRegistered;
}
