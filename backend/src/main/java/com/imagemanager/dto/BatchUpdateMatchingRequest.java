package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 批量更新匹配模式请求
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUpdateMatchingRequest {
    
    /**
     * 匹配模式
     * - contains: 包含匹配（默认）
     * - exact: 精确匹配
     * - startsWith: 开头匹配
     * - endsWith: 结尾匹配
     * - regex: 正则表达式匹配
     * - fuzzy: 模糊匹配
     */
    private String mode;
}
