package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 批量下载图片响应
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDownloadResponse {
    
    /**
     * 原始URL
     */
    private String originalUrl;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 是否跳过（已存在）
     */
    private boolean skipped;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 保存后的图片ID
     */
    private String imageId;
}
