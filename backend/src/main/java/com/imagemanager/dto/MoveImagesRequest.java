package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 移动图片请求
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoveImagesRequest {
    
    /**
     * 图片ID列表
     */
    private List<String> imageIds;
    
    /**
     * 目标相册ID
     */
    private String targetAlbumId;
}
