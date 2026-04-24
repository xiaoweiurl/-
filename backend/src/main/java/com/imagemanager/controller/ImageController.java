package com.imagemanager.controller;

import com.imagemanager.dto.*;
import com.imagemanager.entity.Image;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 图片管理控制器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/images")
@Tag(name = "图片管理", description = "图片上传、查询、删除、收藏等操作")
public class ImageController {
    
    @Autowired
    private ImageService imageService;
    
    @Autowired
    private ImageRepository imageRepository;
    
    /**
     * 查询图片列表
     */
    @GetMapping
    @Operation(summary = "查询图片列表", description = "支持搜索、筛选、排序、分页")
    public ApiResponse<PageResponse<Image>> queryImages(ImageQueryRequest request) {
        log.info("查询图片列表：{}", request);
        PageResponse<Image> result = imageService.queryImages(request);
        return ApiResponse.success(result);
    }
    
    /**
     * 获取图片详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取图片详情", description = "根据ID获取图片详细信息")
    public ApiResponse<Image> getImageById(
            @Parameter(description = "图片ID") @PathVariable String id) {
        Image image = imageService.getImageById(id);
        // 记录浏览次数
        imageService.recordView(id);
        return ApiResponse.success(image);
    }
    
    /**
     * 上传图片
     */
    @PostMapping("/upload")
    @Operation(summary = "上传图片", description = "上传单张图片")
    public ApiResponse<Image> uploadImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "标题") @RequestParam(required = false) String title,
            @Parameter(description = "相册ID") @RequestParam(required = false) String albumId,
            @Parameter(description = "标签") @RequestParam(required = false) List<String> tags) {
        log.info("上传图片：{}", file.getOriginalFilename());
        Image image = imageService.uploadImage(file, title, albumId, tags);
        return ApiResponse.success("上传成功", image);
    }
    
    /**
     * 批量上传图片
     */
    @PostMapping("/upload/batch")
    @Operation(summary = "批量上传图片", description = "批量上传多张图片，自动使用AI识别分类")
    public ApiResponse<List<Image>> batchUploadImages(
            @Parameter(description = "图片文件列表") @RequestParam("files") List<MultipartFile> files) {
        log.info("批量上传图片，数量：{}", files.size());
        List<Image> images = imageService.batchUploadImages(files);
        return ApiResponse.success("批量上传成功，共上传 " + images.size() + " 张图片", images);
    }
    
    /**
     * 更新图片信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新图片信息", description = "更新图片标题、相册、标签等")
    public ApiResponse<Image> updateImage(
            @Parameter(description = "图片ID") @PathVariable String id,
            @RequestBody Image image) {
        log.info("更新图片信息：{}", id);
        Image updated = imageService.updateImage(id, image.getTitle(), 
                image.getAlbumId(), image.getTags(), image.getDescription());
        return ApiResponse.success("更新成功", updated);
    }
    
    /**
     * 删除图片（移至回收站）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除图片", description = "将图片移至回收站")
    public ApiResponse<Void> deleteImage(
            @Parameter(description = "图片ID") @PathVariable String id) {
        log.info("删除图片：{}", id);
        imageService.deleteImage(id);
        return ApiResponse.success("删除成功", null);
    }
    
    /**
     * 永久删除图片
     */
    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "永久删除图片", description = "永久删除图片，无法恢复")
    public ApiResponse<Integer> permanentDelete(
            @Parameter(description = "图片ID") @PathVariable String id) {
        log.info("永久删除图片：{}", id);
        int deletedCount = imageService.permanentDelete(id);
        return ApiResponse.success("永久删除成功，已删除 " + deletedCount + " 张图片（主图+详情图）", deletedCount);
    }
    
    /**
     * 恢复图片
     */
    @PostMapping("/{id}/restore")
    @Operation(summary = "恢复图片", description = "从回收站恢复图片")
    public ApiResponse<Integer> restoreImage(
            @Parameter(description = "图片ID") @PathVariable String id) {
        log.info("恢复图片：{}", id);
        int restoredCount = imageService.restoreImage(id);
        return ApiResponse.success("恢复成功，已恢复 " + restoredCount + " 张图片（主图+详情图）", restoredCount);
    }
    
    /**
     * 切换收藏状态
     */
    @PostMapping("/{id}/favorite")
    @Operation(summary = "切换收藏状态", description = "收藏或取消收藏图片")
    public ApiResponse<Image> toggleFavorite(
            @Parameter(description = "图片ID") @PathVariable String id) {
        log.info("切换收藏状态：{}", id);
        Image image = imageService.toggleFavorite(id);
        return ApiResponse.success(image.getFavorite() ? "已收藏" : "已取消收藏", image);
    }

    
    /**
     * 批量操作
     */
    @PostMapping("/batch")
    @Operation(summary = "批量操作", description = "批量移动、收藏、删除等")
    public ApiResponse<Void> batchOperation(@RequestBody BatchOperationRequest request) {
        log.info("批量操作：{}", request.getOperation());
        
        switch (request.getOperation()) {
            case "delete":
                imageService.batchDelete(request.getImageIds());
                break;
            case "favorite":
                imageService.batchFavorite(request.getImageIds());
                break;
            case "move":
                imageService.moveToAlbum(request.getImageIds(), request.getTargetAlbumId());
                break;
            default:
                return ApiResponse.error("不支持的操作类型");
        }
        
        return ApiResponse.success("操作成功", null);
    }
    
    /**
     * 批量移动图片
     */
    @PostMapping("/move")
    @Operation(summary = "批量移动图片", description = "将图片移动到指定相册")
    public ApiResponse<Void> moveImages(@RequestBody MoveImagesRequest request) {
        log.info("批量移动图片到相册：{}", request.getTargetAlbumId());
        imageService.moveToAlbum(request.getImageIds(), request.getTargetAlbumId());
        return ApiResponse.success("移动成功", null);
    }
    
    /**
     * 批量删除图片
     */
    @PostMapping("/delete")
    @Operation(summary = "批量删除图片", description = "批量删除图片（移至回收站）")
    public ApiResponse<Integer> deleteImages(@RequestBody DeleteImagesRequest request) {
        log.info("批量删除图片，数量：{}", request.getImageIds().size());
        
        int totalDeleted = 0;
        if (Boolean.TRUE.equals(request.getPermanent())) {
            for (String id : request.getImageIds()) {
                totalDeleted += imageService.permanentDelete(id);
            }
            return ApiResponse.success("删除成功，已删除 " + totalDeleted + " 张图片（主图+详情图）", totalDeleted);
        } else {
            imageService.batchDelete(request.getImageIds());
            return ApiResponse.success("删除成功（移至回收站）", 0);
        }
    }
    
    /**
     * 获取收藏图片
     */
    @GetMapping("/favorites")
    @Operation(summary = "获取收藏图片", description = "获取所有收藏的图片")
    public ApiResponse<PageResponse<Image>> getFavorites(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize) {
        log.info("获取收藏图片列表");
        PageResponse<Image> result = imageService.getFavorites(page, pageSize);
        return ApiResponse.success(result);
    }
    
    /**
     * 获取最近上传的图片
     */
    @GetMapping("/recent")
    @Operation(summary = "获取最近上传图片", description = "获取最近7天内上传的图片")
    public ApiResponse<PageResponse<Image>> getRecent(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize) {
        log.info("获取最近上传图片列表");
        PageResponse<Image> result = imageService.getRecent(page, pageSize);
        return ApiResponse.success(result);
    }
    
    /**
     * 获取回收站图片
     */
    @GetMapping("/trash")
    @Operation(summary = "获取回收站图片", description = "获取所有已删除的图片")
    public ApiResponse<PageResponse<Image>> getTrash(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") Integer pageSize) {
        log.info("获取回收站图片列表");
        PageResponse<Image> result = imageService.getTrash(page, pageSize);
        return ApiResponse.success(result);
    }
    
    /**
     * 获取回收站主图数量
     */
    @GetMapping("/trash/count")
    @Operation(summary = "获取回收站主图数量", description = "获取回收站中主图的数量")
    public ApiResponse<Long> getTrashCount() {
        log.info("获取回收站主图数量");
        long count = imageService.getTrashCount();
        return ApiResponse.success(count);
    }
    
    /**
     * 清空回收站
     */
    @DeleteMapping("/trash")
    @Operation(summary = "清空回收站", description = "永久删除回收站中的所有图片")
    public ApiResponse<Integer> clearTrash() {
        log.info("清空回收站");
        int deletedCount = imageService.clearTrash();
        return ApiResponse.success("回收站已清空，已删除 " + deletedCount + " 张图片（主图+详情图）", deletedCount);
    }
    
    /**
     * 批量恢复回收站图片
     */
    @PostMapping("/trash/restore")
    @Operation(summary = "批量恢复回收站图片", description = "从回收站批量恢复指定的图片")
    public ApiResponse<Integer> restoreFromTrash(@RequestBody RestoreImagesRequest request) {
        log.info("恢复回收站图片，数量：{}", request.getImageIds().size());
        
        if (request.getImageIds() == null || request.getImageIds().isEmpty()) {
            return ApiResponse.error("请选择要恢复的图片");
        }
        
        int restoredCount = imageService.batchRestore(request.getImageIds());
        return ApiResponse.success("恢复成功，已恢复 " + restoredCount + " 张图片（主图+详情图）", restoredCount);
    }
    
    /**
     * 获取所有标签
     */
    @GetMapping("/tags")
    @Operation(summary = "获取所有标签", description = "获取所有图片标签及使用次数")
    public ApiResponse<List<TagResponse>> getAllTags() {
        log.info("获取所有标签");
        
        List<String> tags = imageRepository.findAllTags();
        List<TagResponse> tagResponses = tags.stream()
                .map(tag -> {
                    int count = (int) imageRepository.findByTag(tag, 
                            PageRequest.of(0, 1)).getTotalElements();
                    return TagResponse.builder()
                            .name(tag)
                            .count(count)
                            .build();
                })
                .toList();
        
        return ApiResponse.success(tagResponses);
    }
    
    /**
     * 按标签筛选图片
     */
    @GetMapping("/filter")
    @Operation(summary = "筛选图片", description = "按标签、日期等条件筛选图片")
    public ApiResponse<PageResponse<Image>> filterImages(
            @Parameter(description = "标签") @RequestParam(required = false) String tag,
            @Parameter(description = "相册ID") @RequestParam(required = false) String albumId,
            @Parameter(description = "收藏") @RequestParam(required = false) Boolean favorites,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "排序字段") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "排序方向") @RequestParam(defaultValue = "desc") String sortOrder,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "40") Integer pageSize) {
        log.info("筛选图片：tag={}, albumId={}, favorites={}", tag, albumId, favorites);
        
        Sort sort = Sort.by("desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, sort);
        
        Page<Image> imagePage;
        
        if (tag != null && !tag.isEmpty()) {
            imagePage = imageRepository.findByTag(tag, pageRequest);
        } else if (Boolean.TRUE.equals(favorites)) {
            imagePage = imageRepository.findByFavoriteTrueAndDeletedFalse(pageRequest);
        } else if (keyword != null && !keyword.isEmpty()) {
            imagePage = imageRepository.searchByKeyword(keyword, pageRequest);
        } else if (albumId != null && !albumId.isEmpty()) {
            // 支持逗号分隔的多个相册ID
            if (albumId.contains(",")) {
                String[] albumIds = albumId.split(",");
                imagePage = imageRepository.findByAlbumIdInAndDeletedFalse(java.util.Arrays.asList(albumIds), pageRequest);
            } else {
                imagePage = imageRepository.findByAlbumIdAndDeletedFalse(albumId, pageRequest);
            }
        } else {
            imagePage = imageRepository.findByDeletedFalse(pageRequest);
        }
        
        PageResponse<Image> response = new PageResponse<>();
        response.setList(imagePage.getContent());
        response.setTotal(imagePage.getTotalElements());
        response.setPage(page);
        response.setPageSize(pageSize);
        
        return ApiResponse.success(response);
    }
    
    /**
     * 分类图片
     */
    @PostMapping("/classify")
    @Operation(summary = "分类图片", description = "批量对图片进行分类")
    public ApiResponse<Void> classifyImages(@RequestBody ClassifyImagesRequest request) {
        log.info("分类图片到：{}", request.getTargetCategory());
        
        if (request.getImageIds() != null && !request.getImageIds().isEmpty()) {
            imageService.moveToAlbum(request.getImageIds(), request.getTargetCategory());
        }
        
        return ApiResponse.success("分类成功", null);
    }
    
    /**
     * 获取图片统计
     */
    @GetMapping("/stats")
    @Operation(summary = "获取图片统计", description = "获取图片数量统计信息")
    public ApiResponse<Map<String, Object>> getImageStats() {
        log.info("获取图片统计");
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", imageRepository.count());
        stats.put("favorites", imageRepository.countByFavoriteTrue());
        
        return ApiResponse.success(stats);
    }
    
    /**
     * 批量下载网络图片
     */
    @PostMapping("/batch-download")
    @Operation(summary = "批量下载网络图片", description = "根据Excel数据批量下载网络图片并保存到系统")
    public ApiResponse<List<BatchDownloadResponse>> batchDownloadImages(
            @Valid @RequestBody BatchDownloadRequest request) {
        log.info("批量下载网络图片，数量：{}", request.getImages().size());

        if (request.getImages() == null || request.getImages().isEmpty()) {
            return ApiResponse.error("请提供要下载的图片列表");
        }

        List<BatchDownloadResponse> results = imageService.batchDownloadImages(request);

        long successCount = results.stream().filter(r -> Boolean.TRUE.equals(r.getSuccess())).count();
        long skipCount = results.stream().filter(r -> Boolean.TRUE.equals(r.getSkipped())).count();
        long failCount = results.stream().filter(r -> !Boolean.TRUE.equals(r.getSuccess()) && !Boolean.TRUE.equals(r.getSkipped())).count();

        String message = String.format("导入完成：成功 %d 张，跳过 %d 张，失败 %d 张", successCount, skipCount, failCount);
        if (skipCount > 0) {
            log.info("批量导入完成：成功 {} 张，跳过 {} 张（已存在），失败 {} 张", successCount, skipCount, failCount);
        }

        return ApiResponse.success(message, results);
    }
    
    /**
     * 批量导出相册图片
     * 按相册分组，每个相册一个文件夹，文件夹内按商品ID再分小文件夹
     * 主图命名 main.{ext}，详情图命名 detail_1.{ext}、detail_2.{ext} 等
     */
    @GetMapping("/export/{albumId}")
    @Operation(summary = "批量导出相册图片", description = "导出指定相册的所有图片为ZIP文件，按商品分组")
    public org.springframework.http.ResponseEntity<byte[]> exportAlbumImages(
            @Parameter(description = "相册ID") @PathVariable String albumId) {
        log.info("批量导出相册图片：{}", albumId);
        
        try {
            byte[] zipData = imageService.exportAlbumImages(albumId);
            
            // 获取相册名称作为文件名
            String fileName = "album_" + albumId + "_export.zip";
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", new String(fileName.getBytes("UTF-8"), "ISO-8859-1"));
            headers.setContentLength(zipData.length);
            
            return new org.springframework.http.ResponseEntity<>(zipData, headers, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            log.error("导出失败", e);
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * 批量导出多个相册图片
     * 按相册分组，每个相册一个文件夹
     */
    @PostMapping("/export/batch")
    @Operation(summary = "批量导出多个相册", description = "导出多个相册的图片为ZIP文件")
    public org.springframework.http.ResponseEntity<byte[]> exportMultipleAlbums(
            @RequestBody java.util.List<String> albumIds) {
        log.info("批量导出多个相册，数量：{}", albumIds.size());
        
        try {
            byte[] zipData = imageService.exportMultipleAlbums(albumIds);
            
            String fileName = "albums_export.zip";
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", new String(fileName.getBytes("UTF-8"), "ISO-8859-1"));
            headers.setContentLength(zipData.length);
            
            return new org.springframework.http.ResponseEntity<>(zipData, headers, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            log.error("批量导出失败", e);
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
