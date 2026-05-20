package com.imagemanager.dto;

import lombok.Data;

/**
 * 存储配额DTO
 */
@Data
public class StorageQuotaDTO {
    private String id;
    private String userId;
    private String username;
    private Long maxStorageBytes;
    private Long usedStorageBytes;
    private Long remainingBytes;
    private Double usagePercentage;
    private String maxStorageFormatted;
    private String usedStorageFormatted;
    private String remainingFormatted;
    private Integer imageCount;
    private Integer albumCount;
    private Integer documentCount;
}
