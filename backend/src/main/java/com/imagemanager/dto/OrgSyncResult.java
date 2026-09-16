package com.imagemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 钉钉组织同步结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgSyncResult {
    private String company;
    private String status;
    private String message;
    private int deptCount;
    private int userCount;
    private Long durationMs;
    private String lastSyncAt;
}
