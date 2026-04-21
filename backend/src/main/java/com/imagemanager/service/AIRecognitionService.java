package com.imagemanager.service;

import com.imagemanager.entity.Album;
import java.util.List;

/**
 * AI识别服务接口
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
public interface AIRecognitionService {
    
    /**
     * 分析图片内容并返回分类和标签
     * 
     * @param imageUrl 图片URL
     * @param fileName 文件名
     * @param albums 可用相册列表（用于分类匹配）
     * @return 包含分类和标签的结果
     */
    AIRecognitionResult analyzeImage(String imageUrl, String fileName, List<Album> albums);
    
    /**
     * 使用AI分析图片内容（基于Base64编码的图片）
     * 
     * @param imageBase64 Base64编码的图片
     * @param fileName 文件名
     * @param albums 可用相册列表
     * @return 包含分类和标签的结果
     */
    AIRecognitionResult analyzeImageWithAI(String imageBase64, String fileName, List<Album> albums);
    
    /**
     * AI识别结果
     */
    class AIRecognitionResult {
        private String albumId;
        private String albumName;
        private List<String> tags;
        private Double confidence;
        private String method; // llm, filename, fallback
        private String description; // AI生成的图片描述

        /**
         * 建议创建的新相册名称（当没有匹配到现有相册时）
         * 如果不为null，表示需要创建新相册
         */
        private String suggestedAlbumName;

        public AIRecognitionResult() {}

        public AIRecognitionResult(String albumId, String albumName, List<String> tags, Double confidence, String method) {
            this.albumId = albumId;
            this.albumName = albumName;
            this.tags = tags;
            this.confidence = confidence;
            this.method = method;
        }

        public String getAlbumId() { return albumId; }
        public void setAlbumId(String albumId) { this.albumId = albumId; }
        public String getAlbumName() { return albumName; }
        public void setAlbumName(String albumName) { this.albumName = albumName; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getSuggestedAlbumName() { return suggestedAlbumName; }
        public void setSuggestedAlbumName(String suggestedAlbumName) { this.suggestedAlbumName = suggestedAlbumName; }

        /**
         * 是否需要创建新相册
         */
        public boolean shouldCreateNewAlbum() {
            return albumId == null && suggestedAlbumName != null && !suggestedAlbumName.isEmpty();
        }
    }
}
