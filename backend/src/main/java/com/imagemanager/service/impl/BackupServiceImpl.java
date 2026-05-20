package com.imagemanager.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.entity.*;
import com.imagemanager.repository.*;
import com.imagemanager.service.AuditService;
import com.imagemanager.service.BackupService;
import com.imagemanager.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 备份服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupServiceImpl implements BackupService {

    private final AlbumRepository albumRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final StorageService storageService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Map<String, Object> createBackup(String userId, String backupType) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 获取用户数据
            Map<String, Object> backupData = exportUserData(userId);
            
            // 生成备份文件名
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "backup_" + backupType + "_" + timestamp + ".json";
            
            // 转换为JSON
            String jsonData = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(backupData);
            
            // 记录审计日志
            auditService.log(AuditLog.ActionType.BACKUP_DATA, "backup", null, 
                    filename, "{\"type\":\"" + backupType + "\"}", userId);
            
            result.put("success", true);
            result.put("filename", filename);
            result.put("data", backupData);
            result.put("size", jsonData.getBytes(StandardCharsets.UTF_8).length);
            result.put("createdAt", LocalDateTime.now().toString());
            
            log.info("Backup created for user {}: {}", userId, filename);
            return result;
        } catch (Exception e) {
            log.error("Failed to create backup for user {}", userId, e);
            result.put("error", e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> getBackupList(String userId, int page, int pageSize) {
        // 由于备份文件是即时生成的，这里返回空列表
        // 实际项目中可以将备份保存到对象存储
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", Collections.emptyList());
        result.put("total", 0);
        return result;
    }

    @Override
    public ResponseEntity<byte[]> downloadBackup(String userId, String backupId) {
        try {
            Map<String, Object> backupData = exportUserData(userId);
            String jsonData = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(backupData);
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "backup_" + timestamp + ".json";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", filename);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(jsonData.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Failed to download backup for user {}", userId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Override
    @Transactional
    public Map<String, Object> restoreFromBackup(String userId, String backupId) {
        Map<String, Object> result = new HashMap<>();
        result.put("error", "备份恢复功能需要上传备份文件");
        return result;
    }

    @Override
    public void deleteBackup(String userId, String backupId) {
        // 即时备份不需要删除
        log.info("Delete backup requested for user {}: {}", userId, backupId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(String userId) {
        Map<String, Object> exportData = new HashMap<>();
        
        try {
            // 用户信息
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("username", user.getUsername());
                userInfo.put("email", user.getEmail());
                userInfo.put("role", user.getRole());
                userInfo.put("createdAt", user.getCreatedAt());
                exportData.put("user", userInfo);
            }
            
            // 相册数据
            List<Album> albums = albumRepository.findByUserId(userId);
            exportData.put("albums", albums);
            
            // 图片数据
            List<Image> images = imageRepository.findByUserIdAndDeletedFalse(userId);
            exportData.put("images", images);
            
            // 存储配额
            Map<String, Object> quota = new HashMap<>();
            quota.put("quota", storageService.getUserQuota(userId));
            exportData.put("storage", quota);
            
            // 导出信息
            Map<String, Object> exportInfo = new HashMap<>();
            exportInfo.put("exportTime", LocalDateTime.now().toString());
            exportInfo.put("version", "1.0");
            exportInfo.put("albumCount", albums.size());
            exportInfo.put("imageCount", images.size());
            exportData.put("exportInfo", exportInfo);
            
            return exportData;
        } catch (Exception e) {
            log.error("Failed to export user data for user {}", userId, e);
            throw new RuntimeException("导出数据失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public Map<String, Object> importUserData(String userId, String jsonData) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> importData = objectMapper.readValue(jsonData, Map.class);
            
            int albumsImported = 0;
            int imagesImported = 0;
            
            // 导入相册
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> albumsData = (List<Map<String, Object>>) importData.get("albums");
            if (albumsData != null) {
                for (Map<String, Object> albumData : albumsData) {
                    String albumId = (String) albumData.get("id");
                    if (albumId != null && !albumRepository.existsById(albumId)) {
                        Album album = new Album();
                        album.setId(albumId);
                        album.setName((String) albumData.get("name"));
                        album.setDescription((String) albumData.get("description"));
                        album.setCoverUrl((String) albumData.get("coverUrl"));
                        album.setParentId((String) albumData.get("parentId"));
                        album.setUserId(userId);
                        albumRepository.save(album);
                        albumsImported++;
                    }
                }
            }
            
            // 记录审计日志
            auditService.log(AuditLog.ActionType.RESTORE_DATA, "import", null,
                    "data_import", 
                    "{\"albumsImported\":" + albumsImported + ",\"imagesImported\":" + imagesImported + "}",
                    userId);
            
            result.put("success", true);
            result.put("albumsImported", albumsImported);
            result.put("imagesImported", imagesImported);
            result.put("message", "数据导入成功");
            
            log.info("Data imported for user {}: {} albums, {} images", 
                    userId, albumsImported, imagesImported);
            return result;
        } catch (Exception e) {
            log.error("Failed to import user data for user {}", userId, e);
            result.put("error", "导入数据失败: " + e.getMessage());
            return result;
        }
    }
}
