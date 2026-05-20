package com.imagemanager.scheduler;

import com.imagemanager.entity.Image;
import com.imagemanager.entity.ShareLink;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.repository.ShareLinkRepository;
import com.imagemanager.repository.SystemSettingRepository;
import com.imagemanager.service.AuditService;
import com.imagemanager.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemScheduler {

    private final ImageRepository imageRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final StorageService storageService;
    private final AuditService auditService;

    /**
     * 清理回收站过期图片
     * 每天凌晨 2 点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanTrash() {
        log.info("Starting trash cleanup task...");
        
        int retentionDays = systemSettingRepository.getIntValue(
                SystemSetting.SettingKey.TRASH_RETENTION_DAYS, 30);
        
        LocalDateTime deadline = LocalDateTime.now().minusDays(retentionDays);
        
        // 查找过期的已删除图片
        List<Image> expiredImages = imageRepository.findByDeletedTrueAndDeletedAtBefore(deadline);
        
        int count = 0;
        for (Image image : expiredImages) {
            try {
                // 永久删除图片
                imageRepository.delete(image);
                
                // 更新存储使用量
                if (image.getSize() != null && image.getUserId() != null) {
                    storageService.subtractUsedStorage(image.getUserId(), image.getSize());
                }
                
                count++;
            } catch (Exception e) {
                log.error("Failed to delete expired image: {}", image.getId(), e);
            }
        }
        
        log.info("Trash cleanup completed. Deleted {} expired images.", count);
    }

    /**
     * 清理过期分享链接
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void cleanExpiredShares() {
        log.info("Starting expired shares cleanup task...");
        
        List<ShareLink> expiredLinks = shareLinkRepository.findExpiredLinks(LocalDateTime.now());
        
        int count = 0;
        for (ShareLink link : expiredLinks) {
            try {
                link.setDeleted(true);
                shareLinkRepository.save(link);
                count++;
            } catch (Exception e) {
                log.error("Failed to mark expired share as deleted: {}", link.getId(), e);
            }
        }
        
        if (count > 0) {
            log.info("Expired shares cleanup completed. Marked {} shares as deleted.", count);
        }
    }

    /**
     * 重新计算存储使用量
     * 每周日凌晨 3 点执行
     */
    @Scheduled(cron = "0 0 3 ? * SUN")
    public void recalculateAllStorage() {
        log.info("Starting storage recalculation task...");
        
        // 获取所有有图片的用户
        List<String> userIds = imageRepository.findDistinctUserIds();
        
        for (String userId : userIds) {
            try {
                storageService.recalculateUsedStorage(userId);
            } catch (Exception e) {
                log.error("Failed to recalculate storage for user: {}", userId, e);
            }
        }
        
        log.info("Storage recalculation completed for {} users.", userIds.size());
    }
}
