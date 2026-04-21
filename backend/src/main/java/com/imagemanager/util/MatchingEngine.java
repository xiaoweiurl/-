package com.imagemanager.util;

import com.imagemanager.dto.MatchingConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 匹配引擎工具类
 * 提供多种匹配模式支持
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
public class MatchingEngine {
    
    // 默认匹配模式
    public static final String MODE_CONTAINS = "contains";
    public static final String MODE_EXACT = "exact";
    public static final String MODE_STARTS_WITH = "startsWith";
    public static final String MODE_ENDS_WITH = "endsWith";
    public static final String MODE_REGEX = "regex";
    public static final String MODE_FUZZY = "fuzzy";
    
    /**
     * 匹配结果
     */
    public static class MatchResult {
        private boolean matched;
        private String matchedKeyword;
        private String targetKeyword; // fuzzy模式下的目标关键词
        
        public MatchResult(boolean matched, String matchedKeyword, String targetKeyword) {
            this.matched = matched;
            this.matchedKeyword = matchedKeyword;
            this.targetKeyword = targetKeyword;
        }
        
        public boolean isMatched() { return matched; }
        public String getMatchedKeyword() { return matchedKeyword; }
        public String getTargetKeyword() { return targetKeyword; }
        
        public static MatchResult matched(String keyword) {
            return new MatchResult(true, keyword, keyword);
        }
        
        public static MatchResult fuzzyMatched(String matchedKeyword, String targetKeyword) {
            return new MatchResult(true, matchedKeyword, targetKeyword);
        }
        
        public static MatchResult notMatched() {
            return new MatchResult(false, null, null);
        }
    }
    
    /**
     * 根据文件名和关键词列表进行匹配
     *
     * @param fileName 文件名
     * @param keywords 关键词列表
     * @param config 匹配配置
     * @return 匹配结果
     */
    public static MatchResult match(String fileName, List<String> keywords, MatchingConfig config) {
        if (fileName == null || fileName.isEmpty() || keywords == null || keywords.isEmpty()) {
            return MatchResult.notMatched();
        }
        
        String mode = config != null && config.getMode() != null ? config.getMode() : MODE_CONTAINS;
        boolean caseSensitive = config != null && config.getCaseSensitive() != null && config.getCaseSensitive();
        
        // 处理模糊匹配的特殊逻辑
        if (MODE_FUZZY.equals(mode)) {
            return fuzzyMatch(fileName, keywords, config.getSynonyms(), caseSensitive);
        }
        
        // 普通匹配模式
        String processedFileName = caseSensitive ? fileName : fileName.toLowerCase();
        
        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) {
                continue;
            }
            
            String processedKeyword = caseSensitive ? keyword : keyword.toLowerCase();
            boolean matched = false;
            
            switch (mode) {
                case MODE_EXACT:
                    matched = processedFileName.equals(processedKeyword);
                    break;
                    
                case MODE_STARTS_WITH:
                    matched = processedFileName.startsWith(processedKeyword);
                    break;
                    
                case MODE_ENDS_WITH:
                    matched = processedFileName.endsWith(processedKeyword);
                    break;
                    
                case MODE_REGEX:
                    try {
                        Pattern pattern = caseSensitive 
                            ? Pattern.compile(keyword) 
                            : Pattern.compile(keyword, Pattern.CASE_INSENSITIVE);
                        matched = pattern.matcher(fileName).find();
                    } catch (Exception e) {
                        log.warn("正则表达式匹配失败: {}, 错误: {}", keyword, e.getMessage());
                        // 正则失败时降级为包含匹配
                        matched = processedFileName.contains(processedKeyword);
                    }
                    break;
                    
                case MODE_CONTAINS:
                default:
                    matched = processedFileName.contains(processedKeyword);
                    break;
            }
            
            if (matched) {
                log.debug("文件 {} 通过 {} 模式匹配到关键词 {}", fileName, mode, keyword);
                return MatchResult.matched(keyword);
            }
        }
        
        return MatchResult.notMatched();
    }
    
    /**
     * 模糊匹配（支持同义词）
     */
    private static MatchResult fuzzyMatch(String fileName, List<String> keywords, 
                                          List<MatchingConfig.SynonymGroup> synonyms, boolean caseSensitive) {
        if (fileName == null || fileName.isEmpty()) {
            return MatchResult.notMatched();
        }
        
        String processedFileName = caseSensitive ? fileName : fileName.toLowerCase();
        
        // 第一步：直接匹配关键词
        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) {
                continue;
            }
            String processedKeyword = caseSensitive ? keyword : keyword.toLowerCase();
            if (processedFileName.contains(processedKeyword)) {
                return MatchResult.matched(keyword);
            }
        }
        
        // 第二步：通过同义词匹配
        if (synonyms != null && !synonyms.isEmpty()) {
            for (MatchingConfig.SynonymGroup group : synonyms) {
                if (group.getKeywords() == null || group.getTargetKeyword() == null) {
                    continue;
                }
                
                for (String synonym : group.getKeywords()) {
                    if (synonym == null || synonym.trim().isEmpty()) {
                        continue;
                    }
                    
                    String processedSynonym = caseSensitive ? synonym : synonym.toLowerCase();
                    if (processedFileName.contains(processedSynonym)) {
                        log.debug("文件 {} 通过同义词 {} 匹配到目标关键词 {}", 
                                 fileName, synonym, group.getTargetKeyword());
                        // 返回原始关键词（相册关键词）而不是同义词
                        for (String keyword : keywords) {
                            if (keyword.equals(group.getTargetKeyword())) {
                                return MatchResult.fuzzyMatched(synonym, keyword);
                            }
                        }
                        return MatchResult.fuzzyMatched(synonym, group.getTargetKeyword());
                    }
                }
            }
        }
        
        // 第三步：中文拼音首字母匹配（如果安装了相关库）
        // TODO: 可以集成 pinyin4j 库支持拼音匹配
        
        // 第四步：编辑距离模糊匹配（简单实现）
        for (String keyword : keywords) {
            if (keyword == null || keyword.trim().isEmpty()) {
                continue;
            }
            // 简单检查：关键词长度大于2且文件名包含关键词的前两个字符
            if (keyword.length() > 2) {
                String prefix = caseSensitive ? keyword.substring(0, 2) : keyword.substring(0, 2).toLowerCase();
                if (processedFileName.contains(prefix)) {
                    log.debug("文件 {} 通过前缀 {} 模糊匹配到关键词 {}", fileName, prefix, keyword);
                    return MatchResult.matched(keyword);
                }
            }
        }
        
        return MatchResult.notMatched();
    }
    
    /**
     * 创建默认配置（包含匹配，不区分大小写）
     */
    public static MatchingConfig createDefaultConfig() {
        MatchingConfig config = new MatchingConfig();
        config.setMode(MODE_CONTAINS);
        config.setCaseSensitive(false);
        return config;
    }
    
    /**
     * 创建包含匹配配置
     */
    public static MatchingConfig createContainsConfig(boolean caseSensitive) {
        MatchingConfig config = new MatchingConfig();
        config.setMode(MODE_CONTAINS);
        config.setCaseSensitive(caseSensitive);
        return config;
    }
    
    /**
     * 创建模糊匹配配置
     */
    public static MatchingConfig createFuzzyConfig(List<MatchingConfig.SynonymGroup> synonyms, boolean caseSensitive) {
        MatchingConfig config = new MatchingConfig();
        config.setMode(MODE_FUZZY);
        config.setCaseSensitive(caseSensitive);
        config.setSynonyms(synonyms);
        return config;
    }
}
