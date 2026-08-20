package com.imagemanager.controller;

import com.imagemanager.service.KnowledgeImportService;
import com.imagemanager.util.SessionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识批量导入 Controller（200G 级，流式直写 Milvus）
 *
 * 响应协议统一为 { success: true/false, ...data }：
 * - 提交接口: { success, taskId, status, message }
 * - 进度接口: { success, progress: {...} }
 * - 取消接口: { success, message }
 * - 任务列表: { success, tasks: [...] }
 * 与前端 knowledge/page.tsx 的 data.success / data.progress / data.taskId 判断完全对齐
 */
@RestController
@RequestMapping("/knowledge/import")
public class KnowledgeImportController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeImportController.class);

    private final KnowledgeImportService importService;

    public KnowledgeImportController(KnowledgeImportService importService) {
        this.importService = importService;
    }

    /** 按服务器路径导入（zip 或文件夹） */
    @PostMapping("/path")
    public Map<String, Object> submitPath(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (path == null || path.isBlank()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("error", "path 不能为空");
            return resp;
        }
        try {
            String userId = SessionUtil.getCurrentUser();
            String company = SessionUtil.getCurrentCompany();
            Map<String, Object> result = importService.submitPath(path.trim(), userId, company);
            Map<String, Object> resp = new HashMap<>(result);
            resp.put("success", true);
            return resp;
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("路径导入提交被拒绝: {}", e.getMessage());
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return resp;
        } catch (Exception e) {
            log.error("路径导入提交失败", e);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("error", "导入失败: " + e.getMessage());
            return resp;
        }
    }

    /** 上传 zip 导入 */
    @PostMapping("/upload")
    public Map<String, Object> submitUpload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("error", "文件为空");
            return resp;
        }
        try {
            String userId = SessionUtil.getCurrentUser();
            String company = SessionUtil.getCurrentCompany();
            Map<String, Object> result = importService.submitUpload(file, userId, company);
            Map<String, Object> resp = new HashMap<>(result);
            resp.put("success", true);
            return resp;
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("上传导入提交被拒绝: {}", e.getMessage());
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return resp;
        } catch (Exception e) {
            log.error("上传导入提交失败", e);
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", false);
            resp.put("error", "导入失败: " + e.getMessage());
            return resp;
        }
    }

    /** 查询导入进度（内存优先，重启后回源 PG） */
    @GetMapping("/progress/{taskId}")
    public Map<String, Object> getProgress(@PathVariable long taskId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Map<String, Object> progress = importService.getProgress(taskId);
            resp.put("success", true);
            resp.put("progress", progress);
            return resp;
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return resp;
        }
    }

    /** 取消导入任务 */
    @PostMapping("/cancel/{taskId}")
    public Map<String, Object> cancel(@PathVariable long taskId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            boolean cancelled = importService.cancel(taskId);
            resp.put("success", cancelled);
            resp.put("message", cancelled ? "任务已取消" : "任务已结束，无法取消");
            if (!cancelled) {
                resp.put("error", "任务已结束，无法取消");
            }
            return resp;
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return resp;
        }
    }

    /** 任务历史列表 */
    @GetMapping("/tasks")
    public Map<String, Object> tasks(@RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> resp = new HashMap<>();
        try {
            List<Map<String, Object>> tasks = importService.listTasks(limit);
            resp.put("success", true);
            resp.put("tasks", tasks);
            return resp;
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
            return resp;
        }
    }
}
