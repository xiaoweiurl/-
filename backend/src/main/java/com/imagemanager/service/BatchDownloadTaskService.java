package com.imagemanager.service;

import com.imagemanager.entity.BatchDownloadTask;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批量下载任务存储服务
 */
@Service
public class BatchDownloadTaskService {

    // 使用 ConcurrentHashMap 存储任务，支持并发访问
    private final Map<String, BatchDownloadTask> taskStore = new ConcurrentHashMap<>();

    /**
     * 创建新任务
     */
    public String createTask(String userId, String parentAlbumName, int totalCount) {
        String taskId = "task-" + UUID.randomUUID().toString().substring(0, 8);

        BatchDownloadTask task = BatchDownloadTask.builder()
                .taskId(taskId)
                .userId(userId)
                .parentAlbumName(parentAlbumName)
                .status("pending")
                .totalCount(totalCount)
                .processedCount(0)
                .successCount(0)
                .failCount(0)
                .skipCount(0)
                .errorMessage(null)
                .createdAt(LocalDateTime.now())
                .completedAt(null)
                .build();

        taskStore.put(taskId, task);
        return taskId;
    }

    /**
     * 获取任务状态
     */
    public BatchDownloadTask getTask(String taskId) {
        return taskStore.get(taskId);
    }

    /**
     * 更新任务为处理中
     */
    public void updateTaskProcessing(String taskId) {
        BatchDownloadTask task = taskStore.get(taskId);
        if (task != null) {
            task.setStatus("processing");
            taskStore.put(taskId, task);
        }
    }

    /**
     * 更新任务进度
     */
    public void updateProgress(String taskId, int processedCount, int successCount, int failCount, int skipCount) {
        BatchDownloadTask task = taskStore.get(taskId);
        if (task != null) {
            task.setProcessedCount(processedCount);
            task.setSuccessCount(successCount);
            task.setFailCount(failCount);
            task.setSkipCount(skipCount);
            taskStore.put(taskId, task);
        }
    }

    /**
     * 更新任务为完成
     */
    public void updateTaskCompleted(String taskId, int successCount, int failCount, int skipCount) {
        BatchDownloadTask task = taskStore.get(taskId);
        if (task != null) {
            task.setStatus("completed");
            task.setProcessedCount(task.getTotalCount());
            task.setSuccessCount(successCount);
            task.setFailCount(failCount);
            task.setSkipCount(skipCount);
            task.setCompletedAt(LocalDateTime.now());
            taskStore.put(taskId, task);
        }
    }

    /**
     * 更新任务为失败
     */
    public void updateTaskFailed(String taskId, String errorMessage) {
        BatchDownloadTask task = taskStore.get(taskId);
        if (task != null) {
            task.setStatus("failed");
            task.setErrorMessage(errorMessage);
            task.setCompletedAt(LocalDateTime.now());
            taskStore.put(taskId, task);
        }
    }

    /**
     * 获取进度百分比
     */
    public int getProgressPercent(String taskId) {
        BatchDownloadTask task = taskStore.get(taskId);
        if (task == null || task.getTotalCount() == 0) {
            return 0;
        }
        return (int) ((task.getProcessedCount() * 100.0) / task.getTotalCount());
    }
}
