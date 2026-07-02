package com.imagemanager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 向量语义搜索结果 DTO（知识库共用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemorySearchResult {
    private UUID id;
    private UUID domainId;
    private String domainName;
    private UUID cardId;
    private String title;
    private String content;
    private BigDecimal score;
    private String confidence;
    private String source;
    private UUID sourceDocId;
}
