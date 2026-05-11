package com.imagemanager.controller;

import com.imagemanager.dto.ApiResponse;
import com.imagemanager.dto.BatchDownloadRequest;
import com.imagemanager.dto.BatchDownloadResponse;
import com.imagemanager.entity.BatchDownloadTask;
import com.imagemanager.service.BatchDownloadTaskService;
import com.imagemanager.service.ImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/api/batch-download")
@Tag(name = "异步批量下载", description = "异步批量下载图片相关接口")
public class AsyncBatchDownloadController {

    @Autowired
    private ImageService imageService;

    @Autowired
    private BatchDownloadTaskService taskService;

    /**
     * 从请求中获取当前用户ID
     */
    private String getCurrentUserId(HttpServletRequest request) {
        // 优先从 X-Session-Id header 获取会话（前端传递方式）
        String sessionId = request.getHeader("X-Session-Id");
        
        // 如果 header 没有，再从 Cookie 获取
        if (sessionId == null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("session_id".equals(cookie.getName())) {
                    sessionId = cookie.getValue();
                    break;
                }
            }
        }
        
        // 临时返回固定值，后续需要根据session获取真实用户ID
        // TODO: 实现根据session获取用户ID的逻辑
        return sessionId != null ? sessionId : "user-1";
    }

    /**
     * 提交异步批量下载任务
     */
    @PostMapping("/tasks")
    @Operation(summary = "提交异步批量下载任务", description = "提交任务后立即返回任务ID，后台异步处理")
    public ApiResponse<Map<String, Object>> submitBatchDownloadTask(
            HttpServletRequest httpRequest,
            @RequestBody BatchDownloadRequest request) {
        try {
            // 使用 System.out.println 直接输出，不会被日志覆盖
            System.out.println("========== 异步批量下载任务开始 ==========");
            System.out.println("X-Session-Id: " + httpRequest.getHeader("X-Session-Id"));
            System.out.println("Content-Type: " + httpRequest.getContentType());
            
            String userId = getCurrentUserId(httpRequest);
            System.out.println("用户ID: " + userId);
            System.out.println("图片数量: " + (request.getImages() != null ? request.getImages().size() : 0));
            if (request.getImages() != null && !request.getImages().isEmpty()) {
                System.out.println("第一张图片: " + request.getImages().get(0));
            }
            
            // 创建任务
            System.out.println("开始创建任务...");
            String taskId = taskService.createTask(
                    userId,
                    request.getParentAlbumName(),
                    request.getImages() != null ? request.getImages().size() : 0
            );
            System.out.println("任务ID: " + taskId);
            System.out.println("任务创建成功！");

            // 异步处理
            final String finalUserId = userId;
            System.out.println("启动异步线程...");
            CompletableFuture.runAsync(() -> {
                try {
                    System.out.println(">>> 异步线程开始执行，taskId: " + taskId);
                    taskService.updateTaskProcessing(taskId);

                    // 调用原有的批量下载逻辑
                    System.out.println(">>> 开始调用 batchDownloadImagesSync...");
                    List<BatchDownloadResponse> results = imageService.batchDownloadImagesSync(request);
                    System.out.println(">>> 批量下载完成，返回结果数量: " + results.size());

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
                    System.out.println(">>> 异步任务完成：taskId=" + taskId + ", success=" + successCount + ", fail=" + failCount + ", skip=" + skipCount);
                    System.out.println("========== 异步任务执行完成 ==========");

                } catch (Exception e) {
                    System.out.println(">>> 异步任务执行出错：taskId=" + taskId + ", 错误: " + e.getMessage());
                    e.printStackTrace();
                    taskService.updateTaskFailed(taskId, e.getMessage());
                }
            });

            // 立即返回任务ID
            System.out.println("立即返回任务ID: " + taskId);
            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("totalCount", request.getImages() != null ? request.getImages().size() : 0);
            result.put("message", "任务已提交，正在后台处理");
            System.out.println("========== 异步批量下载任务结束 ==========");

            return ApiResponse.success(result);

        } catch (Exception e) {
            System.out.println("!!! 提交异步任务失败，错误: " + e.getMessage());
            e.printStackTrace();
            System.out.println("========== 异常结束 ==========");
            return ApiResponse.error("提交任务失败：" + e.getMessage());
        }
    }

    /**
     * 获取任务进度
     */
    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "获取任务进度", description = "轮询获取批量下载任务进度")
    public ApiResponse<Map<String, Object>> getTaskProgress(
            HttpServletRequest httpRequest,
            @PathVariable String taskId) {
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

            return ApiResponse.success(progress);

        } catch (Exception e) {
            log.error("获取任务进度失败", e);
            return ApiResponse.error("获取任务进度失败：" + e.getMessage());
        }
    }
}
