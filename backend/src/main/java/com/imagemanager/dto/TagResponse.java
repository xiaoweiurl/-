package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 标签响应
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagResponse {
    
    /**
     * 标签名称
     */
    private String name;
    
    /**
     * 使用次数
     */
    private Integer count;
}
