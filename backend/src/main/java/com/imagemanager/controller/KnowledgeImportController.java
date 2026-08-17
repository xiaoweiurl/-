package com.imagemanager.controller;

import com.imagemanager.service.KnowledgeImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识批量导入接口（200G 级数据，流式处理直写 Milvus）
 *
 * 支持两种导入方式：
 * 1. POST /api/knowledge/import/path  服务器本地路径（zip 或文件夹）
 * 2. POST /api/knowledge/import/upload  上传 zip 文件
 *
 * 进度查询与取消：
 * - GET  /api/knowledge/import/progress/{taskId}
 * - POST /api/knowledge/import/cancel/{taskId}
 * - GET  /api/knowledge/import/tasks
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge/import")
@RequiredArgsConstructor
public class KnowledgeImportController {

    private final KnowledgeImportService importService;

    /**
     * 按服务器路径导入（zip 压缩包或文件夹）
     */
    @PostMapping("/path")
    public ResponseEntity<Map<String, Object>> importByPath(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "path 参数必填"));
        }
        try {
            return ResponseEntity.ok(importService.submitPath(path.trim()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 上传 zip 文件导入
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> importByUpload(
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
        }
        try {
            return ResponseEntity.ok(importService.submitUpload(file));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("上传导入失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "导入失败: " + e.getMessage()));
        }
    }

    /**
     * 查询导入进度
     */
    @GetMapping("/progress/{taskId}")
    public ResponseEntity<Map<String, Object>> progress(@PathVariable long taskId) {
        try {
            return ResponseEntity.ok(importService.getProgress(taskId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 取消导入任务
     */
    @PostMapping("/cancel/{taskId}")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable long taskId) {
        try {
            return ResponseEntity.ok(importService.cancel(taskId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 导入任务列表
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<Map<String, Object>>> tasks(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(importService.listTasks(limit));
    }
}
