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
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    
    /**
     * 初始化默认用户和通知
     */
    @PostConstruct
    public void initDefaultData() {
        // 检查是否已有用户
        if (userRepository.count() > 0) {
            log.info("用户数据已存在，跳过初始化");
            return;
        }
        
        log.info("初始化默认用户和通知...");
        
        // 创建默认用户
        User defaultUser = User.builder()
                .id("user-1")
                .username("Alex Wang")
                .password("password123")
                .email("alex@example.com")
                .avatarUrl(null)
                .nickname("Alex")
                .bio("摄影爱好者")
                .phone("13800138000")
                .role("user")
                .membership("pro")
                .storageUsed(1024L * 1024 * 1024 * 5)  // 5GB
                .storageLimit(1024L * 1024 * 1024 * 50)  // 50GB
                .createdAt(LocalDateTime.now().minusYears(1))
                .lastLoginAt(LocalDateTime.now())
                .build();
        userRepository.save(defaultUser);
        
        // 创建默认通知
        Notification notif1 = Notification.builder()
                .id("notif-1")
                .title("上传成功")
                .content("5张新图片上传成功")
                .type("upload")
                .read(false)
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .userId("user-1")
                .build();
        notificationRepository.save(notif1);
        
        Notification notif2 = Notification.builder()
                .id("notif-2")
                .title("相册更新")
                .content("相册\"风景\"已更新")
                .type("album")
                .read(false)
                .createdAt(LocalDateTime.now().minusHours(1))
                .userId("user-1")
                .build();
        notificationRepository.save(notif2);
        
        Notification notif3 = Notification.builder()
                .id("notif-3")
                .title("系统通知")
                .content("系统维护通知")
                .type("system")
                .read(true)
                .createdAt(LocalDateTime.now().minusDays(2))
                .userId("user-1")
                .build();
        notificationRepository.save(notif3);
        
        log.info("默认用户和通知初始化完成");
    }
    
    @Override
    public User getCurrentUser() {
        log.info("获取当前用户信息");
        // 返回默认用户
        return userRepository.findById("user-1")
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
    
    @Override
    public List<Notification> getNotifications() {
        log.info("获取通知列表");
        String currentUserId = SessionUtil.getCurrentUserId();
        if (currentUserId == null) {
            currentUserId = "user-1"; // 降级默认
        }
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUserId);
    }
    
    @Override
    public Notification createNotification(CreateNotificationRequest request) {
        // 从session获取当前用户ID
        String currentUserId = SessionUtil.getCurrentUserId();
        if (currentUserId == null) {
            currentUserId = "user-1"; // 降级默认
        }
        log.info("创建通知：userId={}, type={}, title={}", currentUserId, request.getType(), request.getTitle());

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
        log.info("通知创建成功，ID：{}", notification.getId());

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
        log.info("删除通知：{}", notificationId);
        
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("通知不存在"));
        
        notificationRepository.delete(notification);
        log.info("通知删除成功");
    }
    
    @Override
    public void markNotificationRead(String notificationId) {
        log.info("标记通知为已读：{}", notificationId);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("通知不存在"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }
    
    @Override
    public void markAllNotificationsRead() {
        String userId = SessionUtil.getCurrentUserId();
        if (userId == null) userId = "user-1";
        log.info("标记所有通知为已读：{}", userId);
        List<Notification> notifications = notificationRepository.findByUserIdAndReadFalse(userId);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    @Override
    public Integer getUnreadCount() {
        String userId = SessionUtil.getCurrentUserId();
        if (userId == null) userId = "user-1";
        log.info("获取未读通知数量：{}", userId);
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
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }
    
    @Override
    public UserSettings getSettings(String userId) {
        log.info("获取用户设置，用户ID：{}", userId);
        
        UserSettingsEntity entity = userSettingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));
        
        return convertToDto(entity);
    }
    
    @Override
    public UserSettings updateSettings(String userId, UserSettings settings) {
        log.info("更新用户设置，用户ID：{}", userId);
        
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
        
        userSettingsRepository.save(entity);
        
        return convertToDto(entity);
    }
    
    /**
     * 创建默认设置
     */
    private UserSettingsEntity createDefaultSettings(String userId) {
        log.info("创建默认设置，用户ID：{}", userId);
        
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
                .storageUsed(0L)
                .storageLimit(1024L * 1024 * 1024 * 10) // 默认10GB
                .createdAt(LocalDateTime.now())
                .build();
        
        user = userRepository.save(user);
        
        // 创建默认设置
        createDefaultSettings(user.getId());
        
        // 创建用户图片表（动态表方案）
        boolean tableCreated = imageTableService.ensureUserImageTable(user.getId());
        log.info("用户图片表创建: userId={}, result={}", user.getId(), tableCreated);
        
        log.info("用户创建成功，ID：{}", user.getId());
        return user;
    }
    
    @Override
    public User updateUser(String userId, UpdateUserRequest request) {
        log.info("更新用户信息，用户ID：{}", userId);
        
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
        
        return userRepository.save(user);
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
        notificationRepository.deleteAll(notifications);
        
        // 删除用户
        userRepository.delete(user);
        
        log.info("用户删除成功");
    }
    
    @Override
    public void resetPassword(String userId, String newPassword) {
        log.info("重置用户密码，用户ID：{}", userId);
        
        User user = getUserById(userId);
        
        // 加密新密码
        String encodedPassword = passwordEncoder.encode(newPassword);
        
        user.setPassword(encodedPassword);
        userRepository.save(user);
        
        log.info("密码重置成功");
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
