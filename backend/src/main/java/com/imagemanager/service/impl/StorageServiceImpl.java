package com.imagemanager.service.impl;

import com.imagemanager.dto.StorageQuotaDTO;
import com.imagemanager.entity.StorageQuota;
import com.imagemanager.entity.User;
import com.imagemanager.repository.*;
import com.imagemanager.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final StorageQuotaRepository storageQuotaRepository;
    private final UserRepository userRepository;
    private final ImageRepository imageRepository;
    private final AlbumRepository albumRepository;
    private final SystemSettingRepository systemSettingRepository;

    @Override
    public StorageQuotaDTO getUserQuota(String userId) {
        StorageQuota quota = storageQuotaRepository.findByUserId(userId)
                .orElseGet(() -> {
                    initializeUserQuota(userId);
                    return storageQuotaRepository.findByUserId(userId).orElse(null);
                });

        if (quota == null) {
            return null;
        }

        return toDTO(quota);
    }

    @Override
    @Transactional
    public void updateQuota(String userId, Long maxStorageBytes) {
        StorageQuota quota = storageQuotaRepository.findByUserId(userId)
                .orElseGet(() -> {
                    StorageQuota q = new StorageQuota();
                    q.setUserId(userId);
                    return q;
                });

        quota.setMaxStorageBytes(maxStorageBytes);
        storageQuotaRepository.save(quota);
        log.info("Updated storage quota for user {}: {} bytes", userId, maxStorageBytes);
    }

    @Override
    public boolean hasEnoughSpace(String userId, Long bytes) {
        StorageQuota quota = storageQuotaRepository.findByUserId(userId).orElse(null);
        if (quota == null) {
            // 默认配额
            long defaultQuota = systemSettingRepository.getLongValue(
                    SystemSetting.SettingKey.DEFAULT_USER_STORAGE_QUOTA, 10737418240L);
            return bytes <= defaultQuota;
        }
        return quota.hasEnoughSpace(bytes);
    }

    @Override
    @Transactional
    public void addUsedStorage(String userId, Long bytes) {
        storageQuotaRepository.addUsedStorage(userId, bytes);
        log.debug("Added {} bytes to user {} storage", bytes, userId);
    }

    @Override
    @Transactional
    public void subtractUsedStorage(String userId, Long bytes) {
        storageQuotaRepository.subtractUsedStorage(userId, bytes);
        log.debug("Subtracted {} bytes from user {} storage", bytes, userId);
    }

    @Override
    @Transactional
    public void recalculateUsedStorage(String userId) {
        // 计算用户所有未删除图片的总大小
        Long totalSize = imageRepository.sumSizeByUserIdAndDeletedFalse(userId);
        if (totalSize == null) totalSize = 0L;

        StorageQuota quota = storageQuotaRepository.findByUserId(userId)
                .orElseGet(() -> {
                    StorageQuota q = new StorageQuota();
                    q.setUserId(userId);
                    return q;
                });

        quota.setUsedStorageBytes(totalSize);
        storageQuotaRepository.save(quota);
        log.info("Recalculated storage for user {}: {} bytes", userId, totalSize);
    }

    @Override
    public Map<String, Object> getSystemStorageStats() {
        Map<String, Object> stats = new HashMap<>();
        
        Long totalUsed = storageQuotaRepository.getTotalUsedStorage();
        Long totalMax = storageQuotaRepository.getTotalMaxStorage();
        
        stats.put("totalUsedStorage", totalUsed != null ? totalUsed : 0);
        stats.put("totalMaxStorage", totalMax != null ? totalMax : 0);
        stats.put("totalUsedFormatted", StorageQuota.formatSize(totalUsed));
        stats.put("totalMaxFormatted", StorageQuota.formatSize(totalMax));
        
        Long totalImages = imageRepository.countByDeletedFalse();
        Long totalAlbums = albumRepository.count();
        Long totalUsers = userRepository.count();
        
        stats.put("totalImages", totalImages);
        stats.put("totalAlbums", totalAlbums);
        stats.put("totalUsers", totalUsers);
        
        return stats;
    }

    @Override
    public Page<StorageQuotaDTO> getAllUserQuotas(int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        return storageQuotaRepository.findAll(pageable)
                .map(this::toDTO);
    }

    @Override
    @Transactional
    public void initializeUserQuota(String userId) {
        if (storageQuotaRepository.findByUserId(userId).isPresent()) {
            return;
        }

        long defaultQuota = systemSettingRepository.getLongValue(
                SystemSetting.SettingKey.DEFAULT_USER_STORAGE_QUOTA, 10737418240L);

        StorageQuota quota = new StorageQuota();
        quota.setUserId(userId);
        quota.setMaxStorageBytes(defaultQuota);
        quota.setUsedStorageBytes(0L);
        storageQuotaRepository.save(quota);
        log.info("Initialized storage quota for user {}: {} bytes", userId, defaultQuota);
    }

    private StorageQuotaDTO toDTO(StorageQuota quota) {
        StorageQuotaDTO dto = new StorageQuotaDTO();
        dto.setId(quota.getId());
        dto.setUserId(quota.getUserId());
        dto.setMaxStorageBytes(quota.getMaxStorageBytes());
        dto.setUsedStorageBytes(quota.getUsedStorageBytes());
        dto.setRemainingBytes(quota.getRemainingBytes());
        dto.setUsagePercentage(quota.getUsagePercentage());
        dto.setMaxStorageFormatted(StorageQuota.formatSize(quota.getMaxStorageBytes()));
        dto.setUsedStorageFormatted(StorageQuota.formatSize(quota.getUsedStorageBytes()));
        dto.setRemainingFormatted(StorageQuota.formatSize(quota.getRemainingBytes()));

        // 获取用户名
        userRepository.findById(quota.getUserId())
                .ifPresent(user -> dto.setUsername(user.getUsername()));

        // 获取图片和相册数量
        dto.setImageCount(imageRepository.countByUserIdAndDeletedFalse(quota.getUserId()).intValue());
        dto.setAlbumCount(albumRepository.countByUserId(quota.getUserId()).intValue());

        return dto;
    }
}
