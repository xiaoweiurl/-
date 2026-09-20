package com.imagemanager.service.impl;

import com.imagemanager.dto.CreateNotificationRequest;
import com.imagemanager.dto.CreateUserRequest;
import com.imagemanager.dto.UpdateUserRequest;
import com.imagemanager.dto.UserSettings;
import com.imagemanager.entity.Notification;
import com.imagemanager.entity.User;
import com.imagemanager.entity.UserSettingsEntity;
import com.imagemanager.repository.AlbumRepository;
import com.imagemanager.repository.ImageRepository;
import com.imagemanager.repository.NotificationRepository;
import com.imagemanager.repository.UserRepository;
import com.imagemanager.repository.UserSettingsRepository;
import com.imagemanager.service.ImageTableService;
import com.imagemanager.service.UserService;
import com.imagemanager.util.SessionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import java.util.Objects;

/**
 * 用户服务实现类
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private NotificationRepository notificationRepository;
    
    @Autowired
    private ImageRepository imageRepository;
    
    @Autowired
    private AlbumRepository albumRepository;
    
    @Autowired
    private UserSettingsRepository userSettingsRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private ImageTableService imageTableService;
    
    
    @Override
    public User getCurrentUser() {
        String currentUserId = SessionUtil.requireCurrentUserId();
        return userRepository.findById(Objects.requireNonNull(currentUserId))
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
    
    @Override
    public List<Notification> getNotifications() {
        String currentUserId = SessionUtil.requireCurrentUserId();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUserId);
    }
    
    @Override
    public Notification createNotification(CreateNotificationRequest request) {
        // 从session获取当前用户ID
        String currentUserId = SessionUtil.requireCurrentUserId();

        Notification notification = Notification.builder()
                .id(UUID.randomUUID().toString())
                .type(request.getType() != null ? request.getType() : "system")
                .title(request.getTitle())
                .content(request.getContent())
                .resourceId(request.getResourceId())
                .read(false)
                .createdAt(LocalDateTime.now())
                .userId(currentUserId)
                .build();
        
        notification = notificationRepository.save(notification);

        return notification;
    }

    @Override
    public void notify(String type, String title, String content, String targetId) {
        try {
            CreateNotificationRequest request = new CreateNotificationRequest();
            request.setType(type);
            request.setTitle(title);
            request.setContent(content);
            request.setTargetId(targetId);
            createNotification(request);
        } catch (Exception e) {
            // 通知创建失败不能影响主业务
            log.warn("通知创建失败（不影响主业务）：type={}, title={}, error={}", type, title, e.getMessage());
        }
    }

    @Override
    public void deleteNotification(String notificationId) {
        
        Notification notification = notificationRepository.findById(Objects.requireNonNull(notificationId))
                .orElseThrow(() -> new RuntimeException("通知不存在"));
        
        notificationRepository.delete(Objects.requireNonNull(notification));
    }
    
    @Override
    public void markNotificationRead(String notificationId) {
        Notification notification = notificationRepository.findById(Objects.requireNonNull(notificationId))
                .orElseThrow(() -> new RuntimeException("通知不存在"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }
    
    @Override
    public void markAllNotificationsRead() {
        String userId = SessionUtil.requireCurrentUserId();
        List<Notification> notifications = notificationRepository.findByUserIdAndReadFalse(userId);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    @Override
    public Integer getUnreadCount() {
        String userId = SessionUtil.requireCurrentUserId();
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }
    
    @Override
    public Integer getImageCount() {
        return (int) imageRepository.count();
    }
    
    @Override
    public Integer getAlbumCount() {
        return (int) albumRepository.count();
    }
    
    @Override
    public Integer getFavoriteCount() {
        return imageRepository.countByFavoriteTrue();
    }
    
    @Override
    public List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        userRepository.findAll().forEach(users::add);
        return users;
    }
    
    @Override
    public User getUserById(String userId) {
        return userRepository.findById(Objects.requireNonNull(userId))
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
    
    @Override
    public UserSettings getSettings(String userId) {
        
        UserSettingsEntity entity = userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));
        
        return convertToDto(entity);
    }
    
    @Override
    public UserSettings updateSettings(String userId, UserSettings settings) {
        
        UserSettingsEntity entity = userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));
        
        // 更新设置
        if (settings.getTheme() != null) {
            entity.setTheme(settings.getTheme());
        }
        if (settings.getLanguage() != null) {
            entity.setLanguage(settings.getLanguage());
        }
        if (settings.getPageSize() != null) {
            entity.setPageSize(settings.getPageSize());
        }
        if (settings.getDefaultSort() != null) {
            entity.setDefaultSort(settings.getDefaultSort());
        }
        if (settings.getAiRecognitionEnabled() != null) {
            entity.setAiRecognitionEnabled(settings.getAiRecognitionEnabled());
        }
        if (settings.getEmailNotifications() != null) {
            entity.setEmailNotifications(settings.getEmailNotifications());
        }
        if (settings.getSystemNotifications() != null) {
            entity.setSystemNotifications(settings.getSystemNotifications());
        }
        if (settings.getUploadNotifications() != null) {
            entity.setUploadNotifications(settings.getUploadNotifications());
        }
        if (settings.getAutoPlayVideos() != null) {
            entity.setAutoPlayVideos(settings.getAutoPlayVideos());
        }
        if (settings.getHighQualityPreviews() != null) {
            entity.setHighQualityPreviews(settings.getHighQualityPreviews());
        }
        if (settings.getCompactMode() != null) {
            entity.setCompactMode(settings.getCompactMode());
        }
        if (settings.getShowFileInfo() != null) {
            entity.setShowFileInfo(settings.getShowFileInfo());
        }
        if (settings.getDefaultView() != null) {
            entity.setDefaultView(settings.getDefaultView());
        }
        
        userSettingsRepository.save(Objects.requireNonNull(entity));
        
        return convertToDto(entity);
    }
    
    /**
     * 创建默认设置
     */
    private UserSettingsEntity createDefaultSettings(String userId) {
        
        UserSettingsEntity entity = UserSettingsEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .theme("system")
                .language("zh-CN")
                .pageSize(40)
                .defaultSort("createdAt")
                .aiRecognitionEnabled(true)
                .emailNotifications(true)
                .systemNotifications(true)
                .uploadNotifications(true)
                .autoPlayVideos(true)
                .highQualityPreviews(true)
                .compactMode(false)
                .showFileInfo(true)
                .defaultView("grid")
                .build();
        
        return userSettingsRepository.save(entity);
    }
    
    /**
     * 转换为DTO
     */
    private UserSettings convertToDto(UserSettingsEntity entity) {
        return UserSettings.builder()
                .theme(entity.getTheme())
                .language(entity.getLanguage())
                .pageSize(entity.getPageSize())
                .defaultSort(entity.getDefaultSort())
                .aiRecognitionEnabled(entity.getAiRecognitionEnabled())
                .emailNotifications(entity.getEmailNotifications())
                .systemNotifications(entity.getSystemNotifications())
                .uploadNotifications(entity.getUploadNotifications())
                .autoPlayVideos(entity.getAutoPlayVideos())
                .highQualityPreviews(entity.getHighQualityPreviews())
                .compactMode(entity.getCompactMode())
                .showFileInfo(entity.getShowFileInfo())
                .defaultView(entity.getDefaultView())
                .build();
    }
    
    @Override
    public User createUser(CreateUserRequest request) {
        log.info("创建新用户：{}", request.getUsername());
        
        // 检查用户名是否已存在
        if (existsByUsername(request.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        if (existsByEmail(request.getEmail())) {
            throw new RuntimeException("邮箱已被注册");
        }
        
        // 加密密码
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        
        // 创建用户
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(request.getUsername())
                .password(encodedPassword)
                .email(request.getEmail())
                .nickname(request.getNickname() != null ? request.getNickname() : request.getUsername())
                .phone(request.getPhone())
                .role(request.getRole() != null ? request.getRole() : "user")
                .membership(request.getMembership() != null ? request.getMembership() : "free")
                .company(request.getCompany())
                .storageUsed(0L)
                .storageLimit(1024L * 1024 * 1024 * 10) // 默认10GB
                .createdAt(LocalDateTime.now())
                .build();
        
        user = userRepository.save(user);
        
        // 创建默认设置
        createDefaultSettings(user.getId());
        
        // 创建用户图片表（动态表方案）
        imageTableService.ensureUserImageTable(user.getUsername());
        
        log.info("用户创建成功，ID：{}", user.getId());
        return user;
    }
    
    @Override
    public User updateUser(String userId, UpdateUserRequest request) {
        
        User user = getUserById(userId);
        
        // 更新字段
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (existsByEmail(request.getEmail())) {
                throw new RuntimeException("邮箱已被其他用户使用");
            }
            user.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getMembership() != null) {
            user.setMembership(request.getMembership());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        
        return userRepository.save(Objects.requireNonNull(user));
    }
    
    @Override
    public void deleteUser(String userId) {
        log.info("删除用户，用户ID：{}", userId);
        
        // 检查用户是否存在
        User user = getUserById(userId);
        
        // 删除用户设置
        userSettingsRepository.findByUserId(userId).ifPresent(userSettingsRepository::delete);
        
        // 删除用户通知
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        notificationRepository.deleteAll(Objects.requireNonNull(notifications));
        
        // 删除用户
        userRepository.delete(Objects.requireNonNull(user));
        
        log.info("用户删除成功");
    }
    
    @Override
    public void resetPassword(String userId, String newPassword) {
        log.info("重置用户密码，用户ID：{}", userId);
        
        User user = getUserById(userId);
        
        // 加密新密码
        String encodedPassword = passwordEncoder.encode(newPassword);

        user.setPassword(encodedPassword);
        // 管理员重置密码后，强制用户首次登录改密
        user.setMustChangePassword(true);
        userRepository.save(user);

        log.info("密码重置成功，已标记强制改密");
    }
    
    @Override
    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }
    
    @Override
    public boolean existsByEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
