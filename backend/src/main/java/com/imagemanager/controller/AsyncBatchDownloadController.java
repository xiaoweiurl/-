package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.BatchDownloadRequest;
import com.imagemanager.dto.BatchDownloadResponse;
import com.imagemanager.entity.BatchDownloadTask;
import com.imagemanager.service.BatchDownloadTaskService;
import com.imagemanager.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 异步批量下载控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/images")
@Tag(name = "异步批量下载", description = "异步批量下载图片相关接口")
public class AsyncBatchDownloadController {

    @Autowired
    private ImageService imageService;

    @Autowired
    private BatchDownloadTaskService taskService;

    /**
     * 提交异步批量下载任务
     */
    @PostMapping("/batch-download-async")
    @Operation(summary = "提交异步批量下载任务", description = "提交任务后立即返回任务ID，后台异步处理")
    public ApiResponse<Map<String, Object>> submitBatchDownloadTask(
            @RequestBody BatchDownloadRequest request) {
        try {
            log.info("提交异步批量下载任务，图片数量：{}", request.getImages() != null ? request.getImages().size() : 0);

            // 创建任务
            String taskId = taskService.createTask(
                    "user-1",
                    request.getParentAlbumName(),
                    request.getImages() != null ? request.getImages().size() : 0
            );

            // 异步处理
            CompletableFuture.runAsync(() -> {
                try {
                    taskService.updateTaskProcessing(taskId);

                    // 调用原有的批量下载逻辑
                    List<BatchDownloadResponse> results = imageService.batchDownloadImagesSync(request);

                    // 统计结果
                    int successCount = 0, failCount = 0, skipCount = 0;
                    for (BatchDownloadResponse r : results) {
                        if (r.isSkipped()) {
                            skipCount++;
                        } else if (r.isSuccess()) {
                            successCount++;
                        } else {
                            failCount++;
                        }
                        // 更新进度
                        taskService.updateProgress(taskId, successCount + failCount + skipCount, successCount, failCount, skipCount);
                    }

                    // 标记完成
                    taskService.updateTaskCompleted(taskId, successCount, failCount, skipCount);
                    log.info("异步任务完成：taskId={}, success={}, fail={}, skip={}", taskId, successCount, failCount, skipCount);

                } catch (Exception e) {
                    log.error("异步任务失败：taskId={}", taskId, e);
                    taskService.updateTaskFailed(taskId, e.getMessage());
                }
            });

            // 立即返回任务ID
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("totalCount", request.getImages() != null ? request.getImages().size() : 0);
            result.put("message", "任务已提交，正在后台处理");

            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("提交异步任务失败", e);
            return ApiResponse.error("提交任务失败：" + e.getMessage());
        }
    }

    /**
     * 获取任务进度
     */
    @GetMapping("/batch-download-progress/{taskId}")
    @Operation(summary = "获取任务进度", description = "轮询获取批量下载任务进度")
    public ApiResponse<Map<String, Object>> getTaskProgress(@PathVariable String taskId) {
        try {
            BatchDownloadTask task = taskService.getTask(taskId);

            if (task == null) {
                return ApiResponse.error("任务不存在");
            }

            Map<String, Object> progress = new HashMap<>();
            progress.put("taskId", task.getTaskId());
            progress.put("status", task.getStatus());
            progress.put("totalCount", task.getTotalCount());
            progress.put("processedCount", task.getProcessedCount());
            progress.put("successCount", task.getSuccessCount());
            progress.put("failCount", task.getFailCount());
            progress.put("skipCount", task.getSkipCount());
            progress.put("progressPercent", taskService.getProgressPercent(taskId));
            progress.put("errorMessage", task.getErrorMessage());
            progress.put("createdAt", task.getCreatedAt());
            progress.put("completedAt", task.getCompletedAt());

            return ApiResponse.success(progress);

        } catch (Exception e) {
            log.error("获取任务进度失败：taskId={}", taskId, e);
            return ApiResponse.error("获取进度失败：" + e.getMessage());
        }
    }
}
