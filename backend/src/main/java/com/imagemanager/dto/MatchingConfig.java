package com.imagemanager.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 相册匹配规则配置
 * 用于定义图片自动分类的匹配模式
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchingConfig {
    
    /**
     * 匹配模式
     * - contains: 包含匹配（默认）
     * - exact: 精确匹配
     * - startsWith: 开头匹配
     * - endsWith: 结尾匹配
     * - regex: 正则表达式匹配
     * - fuzzy: 模糊/同义词匹配
     */
    private String mode = "contains";
    
    /**
     * 是否区分大小写
     * 默认 false（不区分大小写）
     */
    private Boolean caseSensitive = false;
    
    /**
     * 同义词配置（用于 fuzzy 模式）
     * 配置哪些关键词视为等价
     */
    private List<SynonymGroup> synonyms;
    
    /**
     * 同义词组
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SynonymGroup {
        /**
         * 同义词列表
         */
        private List<String> keywords;
        
        /**
         * 对应的相册关键词（用于匹配的目标词）
         */
        private String targetKeyword;
    }
}
