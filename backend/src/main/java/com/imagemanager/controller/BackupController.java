package com.imagemanager.controller;

import com.imagemanager.config.AuthInterceptor;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.service.BackupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 备份控制器
 */
@Slf4j
@RestController
@RequestMapping("/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /**
     * 创建备份
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createBackup(
            @RequestParam(defaultValue = "full") String backupType,
            HttpServletRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        Map<String, Object> backupResult = backupService.createBackup(userInfo.getId(), backupType);
        
        if (backupResult.containsKey("error")) {
            return ResponseEntity.badRequest().body(backupResult);
        }
        
        return ResponseEntity.ok(backupResult);
    }

    /**
     * 下载备份
     */
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadBackup(HttpServletRequest request) {
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            return ResponseEntity.status(401).build();
        }

        return backupService.downloadBackup(userInfo.getId(), null);
    }

    /**
     * 导入数据
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importData(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        try {
            String jsonData = new String(file.getBytes(), StandardCharsets.UTF_8);
            Map<String, Object> importResult = backupService.importUserData(userInfo.getId(), jsonData);
            
            if (importResult.containsKey("error")) {
                return ResponseEntity.badRequest().body(importResult);
            }
            
            return ResponseEntity.ok(importResult);
        } catch (Exception e) {
            log.error("Import data failed", e);
            result.put("error", "导入失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 导出数据
     */
    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportData(HttpServletRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        Map<String, Object> exportResult = backupService.exportUserData(userInfo.getId());
        
        result.put("success", true);
        result.put("data", exportResult);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取备份列表（根路径，同 /list）
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getBackupListRoot(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return getBackupList(page, pageSize, request);
    }

    /**
     * 获取备份列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getBackupList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        
        Map<String, Object> result = new HashMap<>();
        
        LoginResponse.UserInfo userInfo = getUserInfo(request);
        if (userInfo == null) {
            result.put("error", "未登录");
            return ResponseEntity.status(401).body(result);
        }

        Map<String, Object> listResult = backupService.getBackupList(userInfo.getId(), page, pageSize);
        return ResponseEntity.ok(listResult);
    }

    /**
     * 从 request 属性中获取用户信息
     */
    private LoginResponse.UserInfo getUserInfo(HttpServletRequest request) {
        return (LoginResponse.UserInfo) request.getAttribute(AuthInterceptor.USER_INFO_ATTRIBUTE);
    }
}
