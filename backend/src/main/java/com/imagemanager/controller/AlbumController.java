package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.BatchUpdateMatchingRequest;
import com.imagemanager.entity.Album;
import com.imagemanager.service.AlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 相册管理控制器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/albums")
@Tag(name = "相册管理", description = "相册的创建、更新、删除等操作")
public class AlbumController {
    
    @Autowired
    private AlbumService albumService;
    
    /**
     * 获取所有相册
     */
    @GetMapping
    @Operation(summary = "获取所有相册", description = "获取用户的全部相册列表")
    public ApiResponse<List<Album>> getAllAlbums() {
        log.info("获取所有相册");
        List<Album> albums = albumService.getAllAlbums();
        return ApiResponse.success(albums);
    }
    
    /**
     * 获取相册详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取相册详情", description = "根据ID获取相册详细信息")
    public ApiResponse<Album> getAlbumById(
            @Parameter(description = "相册ID") @PathVariable String id) {
        log.info("获取相册详情：{}", id);
        Album album = albumService.getAlbumById(id);
        return ApiResponse.success(album);
    }
    
    /**
     * 创建相册
     */
    @PostMapping
    @Operation(summary = "创建相册", description = "创建新的相册，支持配置化匹配规则")
    public ApiResponse<Album> createAlbum(@RequestBody Album album) {
        log.info("创建相册：{}, 关键词：{}, 匹配配置：{}", 
                 album.getName(), album.getKeywords(), album.getMatchingConfig());
        Album created = albumService.createAlbum(
            album.getName(), 
            album.getDescription(),
            album.getKeywords(),
            album.getMatchingConfig()
        );
        return ApiResponse.success("创建成功", created);
    }
    
    /**
     * 更新相册
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新相册", description = "更新相册名称、描述和匹配配置")
    public ApiResponse<Album> updateAlbum(
            @Parameter(description = "相册ID") @PathVariable String id,
            @RequestBody Album album) {
        log.info("更新相册：{}", id);
        Album updated = albumService.updateAlbum(id, album.getName(), album.getDescription(), album.getMatchingConfig());
        return ApiResponse.success("更新成功", updated);
    }
    
    /**
     * 删除相册
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除相册", description = "删除指定相册")
    public ApiResponse<Void> deleteAlbum(
            @Parameter(description = "相册ID") @PathVariable String id) {
        log.info("删除相册：{}", id);
        albumService.deleteAlbum(id);
        return ApiResponse.success("删除成功", null);
    }
    
    /**
     * 批量删除相册
     */
    @PostMapping("/batch-delete")
    @Operation(summary = "批量删除相册", description = "批量删除多个相册，返回删除结果")
    public ApiResponse<Map<String, Object>> batchDeleteAlbums(@RequestBody BatchDeleteRequest request) {
        log.info("批量删除相册，数量：{}", request.getIds().size());
        Map<String, Object> result = albumService.batchDeleteAlbums(request.getIds());
        return ApiResponse.success("批量删除完成", result);
    }
    
    /**
     * 批量更新所有相册的匹配模式
     */
    @PutMapping("/matching-mode")
    @Operation(summary = "批量更新匹配模式", description = "将所有相册的匹配模式设置为指定值")
    public ApiResponse<Integer> batchUpdateMatchingMode(@RequestBody BatchUpdateMatchingRequest request) {
        log.info("批量更新相册匹配模式为：{}", request.getMode());
        int updatedCount = albumService.batchUpdateMatchingMode(request.getMode());
        return ApiResponse.success("更新成功，共更新 " + updatedCount + " 个相册", updatedCount);
    }
    
    /**
     * 重置所有相册的匹配配置为默认（包含匹配）
     */
    @PutMapping("/matching-mode/reset")
    @Operation(summary = "重置匹配配置", description = "将所有相册的匹配配置重置为默认的包含匹配模式")
    public ApiResponse<Integer> resetAllMatchingConfig() {
        log.info("重置所有相册的匹配配置");
        int updatedCount = albumService.resetAllMatchingConfig();
        return ApiResponse.success("重置成功，共重置 " + updatedCount + " 个相册", updatedCount);
    }
    
    /**
     * 创建层级相册
     */
    @PostMapping("/tree")
    @Operation(summary = "创建层级相册", description = "创建带父级的相册，如：松野湃/速干T恤")
    public ApiResponse<Album> createAlbumWithParent(@RequestBody CreateAlbumRequest request) {
        log.info("创建层级相册：{}，父级：{}", request.getName(), request.getParentId());
        Album created = albumService.createAlbumWithParent(
            request.getName(),
            request.getParentId(),
            request.getDescription(),
            request.getKeywords()
        );
        return ApiResponse.success("创建成功", created);
    }
    
    /**
     * 根据路径获取或创建相册
     */
    @PostMapping("/by-path")
    @Operation(summary = "根据路径获取或创建相册", description = "根据路径自动创建层级相册，如：松野湃/速干T恤")
    public ApiResponse<Album> getOrCreateByPath(@RequestBody GetOrCreateByPathRequest request) {
        log.info("根据路径获取或创建相册：{}", request.getPath());
        Album album = albumService.getOrCreateAlbumByPath(request.getPath());
        return ApiResponse.success(album);
    }
    
    /**
     * 获取相册树
     */
    @GetMapping("/tree")
    @Operation(summary = "获取相册树", description = "获取用户的层级相册结构")
    public ApiResponse<List<Album>> getAlbumTree(
            @Parameter(description = "用户ID") @RequestParam(required = false, defaultValue = "user-1") String userId) {
        log.info("获取相册树：{}", userId);
        List<Album> albums = albumService.getAlbumTree(userId);
        return ApiResponse.success(albums);
    }
    
    /**
     * 获取子相册
     */
    @GetMapping("/{id}/children")
    @Operation(summary = "获取子相册", description = "获取指定相册下的子相册")
    public ApiResponse<List<Album>> getChildAlbums(
            @Parameter(description = "父相册ID") @PathVariable String id) {
        log.info("获取子相册：{}", id);
        List<Album> children = albumService.getChildAlbums(id);
        return ApiResponse.success(children);
    }
}

// 请求类
class CreateAlbumRequest {
    private String name;
    private String parentId;
    private String description;
    private List<String> keywords;
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
}

class GetOrCreateByPathRequest {
    private String path;
    
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
}

class BatchDeleteRequest {
    private List<String> ids;
    
    public List<String> getIds() { return ids; }
    public void setIds(List<String> ids) { this.ids = ids; }
}
