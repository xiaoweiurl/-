package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 分类响应
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {
    
    /**
     * 分类ID
     */
    private String id;
    
    /**
     * 分类名称
     */
    private String name;
    
    /**
     * 分类描述
     */
    private String description;
    
    /**
     * 图片数量
     */
    private Integer imageCount;
    
    /**
     * 封面图URL
     */
    private String coverUrl;
    
    /**
     * 排序
     */
    private Integer sortOrder;
}
