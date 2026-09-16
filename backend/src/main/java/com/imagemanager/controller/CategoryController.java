package com.imagemanager.controller;

import com.imagemanager.dto.*;
import com.imagemanager.entity.Album;
import com.imagemanager.entity.Image;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.service.AlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分类管理控制器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/categories")
@Tag(name = "分类管理", description = "图片分类、相册相关接口")
public class CategoryController {
    
    @Autowired
    private AlbumService albumService;
    
    @Autowired
    private ImageRepository imageRepository;
    
    /**
     * 获取所有分类
     */
    @GetMapping
    @Operation(summary = "获取所有分类", description = "获取所有图片分类（相册）列表")
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
        log.info("获取所有分类");
        
        List<Album> albums = albumService.getAllAlbums();
        List<CategoryResponse> categories = albums.stream()
                .map(album -> convertToCategoryResponse(album))
                .collect(Collectors.toList());
        
        return ApiResponse.success(categories);
    }
    
    /**
     * 获取分类详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取分类详情", description = "根据ID获取分类详细信息")
    public ApiResponse<CategoryResponse> getCategoryById(
            @Parameter(description = "分类ID") @PathVariable String id) {
        log.info("获取分类详情：{}", id);
        
        Album album = albumService.getAlbumById(id);
        return ApiResponse.success(convertToCategoryResponse(album));
    }
    
    /**
     * 获取分类下的图片
     */
    @GetMapping("/{id}/images")
    @Operation(summary = "获取分类图片", description = "获取指定分类下的所有图片")
    public ApiResponse<List<Image>> getCategoryImages(
            @Parameter(description = "分类ID") @PathVariable String id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "40") Integer pageSize) {
        log.info("获取分类图片：{}", id);
        
        List<Image> images = imageRepository.findByAlbumIdAndDeletedFalse(id, 
                org.springframework.data.domain.PageRequest.of(page - 1, pageSize))
                .getContent();
        
        return ApiResponse.success(images);
    }
    
    /**
     * 获取所有标签
     */
    @GetMapping("/tags")
    @Operation(summary = "获取所有标签", description = "获取所有图片AI标签及使用次数")
    public ApiResponse<List<TagResponse>> getAllTags() {
        log.info("获取所有标签");
        return ApiResponse.success(new ArrayList<>());
    }
    
    /**
     * 转换 Album 为 CategoryResponse
     */
    private CategoryResponse convertToCategoryResponse(Album album) {
        int imageCount = imageRepository.countByAlbumId(album.getId());
        
        return CategoryResponse.builder()
                .id(album.getId())
                .name(album.getName())
                .description(album.getDescription())
                .imageCount(imageCount)
                .coverUrl(album.getCoverUrl())
                .sortOrder(album.getSortOrder())
                .build();
    }
}
