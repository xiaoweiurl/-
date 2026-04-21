package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * AI 识别响应
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIRecognitionResponse {
    
    /**
     * 图片ID
     */
    private String imageId;
    
    /**
     * 识别的分类
     */
    private String category;
    
    /**
     * 置信度（0-1）
     */
    private Double confidence;
    
    /**
     * 识别的标签
     */
    private List<String> tags;
    
    /**
     * 识别方法（keyword, vision）
     */
    private String method;
    
    /**
     * 错误信息
     */
    private String error;
}
