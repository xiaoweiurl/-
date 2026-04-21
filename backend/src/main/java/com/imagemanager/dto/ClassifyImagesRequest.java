package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 分类图片请求
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClassifyImagesRequest {
    
    /**
     * 图片ID列表
     */
    private List<String> imageIds;
    
    /**
     * 目标分类
     */
    private String targetCategory;
    
    /**
     * 是否使用AI识别
     */
    private Boolean useAI;
}
