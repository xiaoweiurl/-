package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 批量操作请求参数
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationRequest {
    
    /**
     * 图片ID列表
     */
    private List<String> imageIds;
    
    /**
     * 目标相册ID（移动操作时使用）
     */
    private String targetAlbumId;
    
    /**
     * 操作类型（move, favorite, delete, restore）
     */
    private String operation;
}
