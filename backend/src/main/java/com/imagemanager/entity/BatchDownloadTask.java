package com.imagemanager.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 批量下载任务状态实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchDownloadTask {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * Excel文件名
     */
    private String parentAlbumName;

    /**
     * 任务状态：pending, processing, completed, failed
     */
    private String status;

    /**
     * 总图片数
     */
    private int totalCount;

    /**
     * 已处理图片数
     */
    private int processedCount;

    /**
     * 成功数
     */
    private int successCount;

    /**
     * 失败数
     */
    private int failCount;

    /**
     * 跳过数
     */
    private int skipCount;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;
}
