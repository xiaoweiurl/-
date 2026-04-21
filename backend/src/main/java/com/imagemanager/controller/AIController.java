package com.imagemanager.controller;

import com.imagemanager.dto.*;
import com.imagemanager.entity.Album;
import com.imagemanager.entity.Image;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.service.AIRecognitionService;
import com.imagemanager.service.AlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 识别控制器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@Tag(name = "AI识别", description = "图片智能识别、分类等AI功能")
public class AIController {
    
    @Autowired
    private AIRecognitionService aiRecognitionService;
    
    @Autowired
    private AlbumService albumService;
    
    @Autowired
    private ImageRepository imageRepository;
    
    /**
     * 识别图片
     */
    @PostMapping("/recognize")
    @Operation(summary = "识别图片", description = "使用AI识别图片内容，返回分类和标签")
    public ApiResponse<List<AIRecognitionResponse>> recognizeImages(
            @RequestBody AIRecognitionRequest request) {
        log.info("AI识别图片，数量：{}", request.getImageUrls() != null ? request.getImageUrls().size() : 0);
        
        List<AIRecognitionResponse> results = new ArrayList<>();
        List<Album> albums = albumService.getAllAlbums();
        
        for (String imageUrl : request.getImageUrls()) {
            try {
                AIRecognitionService.AIRecognitionResult result;
                
                // 根据请求选择识别方式
                if (Boolean.TRUE.equals(request.getUseVisionRecognition())) {
                    // 使用视觉识别
                    result = aiRecognitionService.analyzeImageWithAI(null, extractFileName(imageUrl), albums);
                } else {
                    // 使用关键词匹配
                    result = aiRecognitionService.analyzeImage(imageUrl, extractFileName(imageUrl), albums);
                }
                
                AIRecognitionResponse response = AIRecognitionResponse.builder()
                        .category(result.getAlbumName())
                        .confidence(result.getConfidence())
                        .tags(result.getTags())
                        .method(result.getMethod())
                        .build();
                
                results.add(response);
            } catch (Exception e) {
                log.error("识别图片失败：{}", imageUrl, e);
                results.add(AIRecognitionResponse.builder()
                        .error(e.getMessage())
                        .build());
            }
        }
        
        return ApiResponse.success(results);
    }
    
    /**
     * 识别单张图片
     */
    @PostMapping("/recognize/{imageId}")
    @Operation(summary = "识别单张图片", description = "根据图片ID识别图片内容")
    public ApiResponse<AIRecognitionResponse> recognizeImage(
            @PathVariable String imageId) {
        log.info("AI识别单张图片：{}", imageId);
        
        Image image = imageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("图片不存在"));
        
        List<Album> albums = albumService.getAllAlbums();
        AIRecognitionService.AIRecognitionResult result = aiRecognitionService.analyzeImage(
                image.getUrl(), image.getOriginalName(), albums);
        
        // 更新图片分类
        if (result.getAlbumId() != null) {
            image.setAlbumId(result.getAlbumId());
            image.setTags(result.getTags());
            imageRepository.save(image);
        }
        
        AIRecognitionResponse response = AIRecognitionResponse.builder()
                .imageId(imageId)
                .category(result.getAlbumName())
                .confidence(result.getConfidence())
                .tags(result.getTags())
                .method(result.getMethod())
                .build();
        
        return ApiResponse.success(response);
    }
    
    /**
     * 从URL提取文件名
     */
    private String extractFileName(String url) {
        if (url == null) return "unknown";
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            return url.substring(lastSlash + 1);
        }
        return url;
    }
}
