package com.imagemanager.controller;

import com.imagemanager.config.StorageConfig;
import com.imagemanager.dto.ApiResponse;
import com.imagemanager.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档管理控制器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/documents")
@Tag(name = "文档管理", description = "文档上传、查询、删除等操作")
public class DocumentController {
    
    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private StorageConfig storageConfig;
    
    /**
     * 上传文档
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文档", description = "上传单个文档文件，支持最大5GB")
    public ApiResponse<Map<String, Object>> uploadDocument(
            @Parameter(description = "文档文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件名") @RequestParam(required = false) String fileName,
            @Parameter(description = "文档分类") @RequestParam(required = false) String category) {
        log.info("上传文档：{}, category: {}", file.getOriginalFilename(), category);
        
        String name = fileName != null && !fileName.isEmpty() ? fileName : file.getOriginalFilename();
        Map<String, Object> result = documentService.uploadDocument(file, name, category);
        
        return ApiResponse.success("文档上传成功", result);
    }
    
    /**
     * 批量上传文档
     */
    @PostMapping("/upload/batch")
    @Operation(summary = "批量上传文档", description = "批量上传多个文档文件")
    public ApiResponse<List<Map<String, Object>>> batchUploadDocuments(
            @Parameter(description = "文档文件列表") @RequestParam("files") List<MultipartFile> files) {
        log.info("批量上传文档，数量：{}", files.size());
        List<Map<String, Object>> results = documentService.batchUploadDocuments(files);
        return ApiResponse.success("批量上传成功，共上传 " + results.size() + " 个文档", results);
    }
    
    /**
     * 删除文档
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档", description = "根据ID删除文档")
    public ApiResponse<Void> deleteDocument(
            @Parameter(description = "文档ID") @PathVariable String id) {
        log.info("删除文档：{}", id);
        documentService.deleteDocument(id);
        return ApiResponse.success("文档已移入回收站", null);
    }
    
    /**
     * 批量删除文档
     */
    @DeleteMapping("/batch")
    @Operation(summary = "批量删除文档", description = "将多个文档移入回收站")
    public ApiResponse<Map<String, Object>> batchDeleteDocuments(
            @Parameter(description = "文档ID列表") @RequestBody List<String> ids) {
        log.info("批量删除文档，数量：{}", ids.size());
        Map<String, Object> result = documentService.batchDeleteDocuments(ids);
        return ApiResponse.success("批量删除完成", result);
    }
    
    /**
     * 恢复文档
     */
    @PostMapping("/{id}/restore")
    @Operation(summary = "恢复文档", description = "从回收站恢复文档")
    public ApiResponse<Void> restoreDocument(
            @Parameter(description = "文档ID") @PathVariable String id) {
        log.info("恢复文档：{}", id);
        documentService.restoreDocument(id);
        return ApiResponse.success("文档已恢复", null);
    }
    
    /**
     * 批量恢复文档
     */
    @PostMapping("/batch/restore")
    @Operation(summary = "批量恢复文档", description = "从回收站批量恢复文档")
    public ApiResponse<Map<String, Object>> batchRestoreDocuments(
            @Parameter(description = "文档ID列表") @RequestBody List<String> ids) {
        log.info("批量恢复文档，数量：{}", ids.size());
        Map<String, Object> result = documentService.batchRestoreDocuments(ids);
        return ApiResponse.success("批量恢复完成", result);
    }
    
    /**
     * 永久删除文档
     */
    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "永久删除文档", description = "直接从数据库和存储中永久删除文档，无法恢复")
    public ApiResponse<Void> permanentDeleteDocument(
            @Parameter(description = "文档ID") @PathVariable String id) {
        log.info("永久删除文档：{}", id);
        documentService.permanentDeleteDocument(id);
        return ApiResponse.success("文档已永久删除", null);
    }
    
    /**
     * 获取文档信息
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取文档信息", description = "根据ID获取文档详细信息")
    public ApiResponse<Map<String, Object>> getDocumentById(
            @Parameter(description = "文档ID") @PathVariable String id) {
        log.info("获取文档信息：{}", id);
        Map<String, Object> document = documentService.getDocumentById(id);
        return ApiResponse.success(document);
    }
    
    /**
     * 下载文档（生成临时访问URL）
     */
    @GetMapping("/{id}/download")
    @Operation(summary = "下载文档", description = "获取文档的下载链接")
    public ApiResponse<Map<String, Object>> getDownloadUrl(
            @Parameter(description = "文档ID") @PathVariable String id) {
        log.info("获取文档下载链接：{}", id);
        String downloadUrl = documentService.getDownloadUrl(id);
        Map<String, Object> result = new HashMap<>();
        result.put("url", downloadUrl);
        return ApiResponse.success(result);
    }
    
    /**
     * 获取文档文件（文件代理）
     */
    @GetMapping("/{id}/file")
    @Operation(summary = "获取文档文件", description = "获取文档文件内容，用于预览和下载")
    public ResponseEntity<Resource> getDocumentFile(
            @Parameter(description = "文档ID") @PathVariable String id) {
        log.info("获取文档文件：{}", id);
        
        try {
            Map<String, Object> doc = documentService.getDocumentById(id);
            String filePath = (String) doc.get("filePath");
            
            if (filePath == null || filePath.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            // 构建完整文件路径
            Path fullPath = Paths.get(storageConfig.getLocalPath(), "documents", filePath);
            File file = fullPath.toFile();
            
            if (!file.exists()) {
                log.error("文档文件不存在：{}", fullPath);
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            String contentType = (String) doc.get("contentType");
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "inline; filename=\"" + doc.get("originalName") + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("获取文档文件失败：{}", id, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 获取文档列表
     */
    @GetMapping
    @Operation(summary = "获取文档列表", description = "分页获取文档列表，支持按分类筛选")
    public ApiResponse<Map<String, Object>> getDocuments(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int pageSize,
            @Parameter(description = "文档分类") @RequestParam(required = false) String category) {
        log.info("获取文档列表，page={}, pageSize={}, category={}", page, pageSize, category);
        Map<String, Object> result = documentService.getDocumentsByCategory(page, pageSize, category);
        return ApiResponse.success(result);
    }
    
    /**
     * 获取文档统计信息
     */
    @GetMapping("/stats")
    @Operation(summary = "获取文档统计", description = "获取各分类的文档数量统计")
    public ApiResponse<Map<String, Integer>> getDocumentStats() {
        log.info("获取文档统计信息");
        Map<String, Integer> stats = documentService.getDocumentStats();
        return ApiResponse.success(stats);
    }
}
