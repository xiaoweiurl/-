package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * AI 识别请求
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AIRecognitionRequest {
    
    /**
     * 图片URL列表
     */
    private List<String> imageUrls;
    
    /**
     * 是否使用关键词匹配
     */
    private Boolean useKeywordMatch;
    
    /**
     * 是否使用视觉识别
     */
    private Boolean useVisionRecognition;
}
