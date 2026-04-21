package com.imagemanager.service.impl;

import com.imagemanager.dto.LoginRequest;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.dto.UpdateProfileRequest;
import com.imagemanager.dto.UserSettings;
import com.imagemanager.entity.User;
import com.imagemanager.repository.UserRepository;
import com.imagemanager.service.AuthService;
import com.imagemanager.util.PasswordValidator;
import com.imagemanager.util.RateLimiter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 认证服务实现类
 * 
 * @author Image Manager Team
 * @version 2.0.0
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    // 内存存储会话（生产环境应使用 Redis）
    // key: sessionId, value: SessionInfo
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    
    // 用户会话统计（用于限制每个用户的会话数量）
    private final Map<String, AtomicInteger> userSessionCount = new ConcurrentHashMap<>();
    
    // 每个用户最大会话数
    private static final int MAX_SESSIONS_PER_USER = 5;
    
    // Session 有效期（毫秒）
    private static final long SESSION_TIMEOUT = 24 * 60 * 60 * 1000; // 24小时
    private static final long SESSION_TIMEOUT_REMEMBER = 7 * 24 * 60 * 60 * 1000; // 7天
    
    // Session 续期阈值（剩余时间少于此值时自动续期）
    private static final long SESSION_RENEWAL_THRESHOLD = 2 * 60 * 60 * 1000; // 2小时
    
    // 用户设置存储（生产环境应存储在数据库）
    private final Map<String, UserSettings> userSettingsMap = new HashMap<>();
    
    /**
     * Session 信息
     */
    private static class SessionInfo {
        LoginResponse.UserInfo userInfo;
        long createTime;
        long lastAccessTime;
        long expiresAt;
        boolean rememberMe;
        
        SessionInfo(LoginResponse.UserInfo userInfo, boolean rememberMe) {
            this.userInfo = userInfo;
            this.createTime = System.currentTimeMillis();
            this.lastAccessTime = this.createTime;
            this.rememberMe = rememberMe;
            this.expiresAt = this.createTime + (rememberMe ? SESSION_TIMEOUT_REMEMBER : SESSION_TIMEOUT);
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
        
        void updateAccess() {
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        boolean needsRenewal() {
            return !isExpired() && (expiresAt - System.currentTimeMillis()) < SESSION_RENEWAL_THRESHOLD;
        }
        
        void renew() {
            long newExpiry = System.currentTimeMillis() + (rememberMe ? SESSION_TIMEOUT_REMEMBER : SESSION_TIMEOUT);
            this.expiresAt = newExpiry;
        }
    }
    
    /**
     * 初始化默认用户
     */
    @PostConstruct
    public void initDefaultData() {
        if (userRepository.count() > 0) {
            log.info("用户数据已存在，跳过初始化");
            return;
        }
        
        log.info("初始化默认用户...");
        
        // 创建管理员用户
        User adminUser = User.builder()
                .id("admin-1")
                .username("admin")
                .password(passwordEncoder.encode("Admin@123"))  // BCrypt 加密
                .email("admin@example.com")
                .avatarUrl(null)
                .nickname("Administrator")
                .bio("系统管理员")
                .phone("13900139000")
                .role("ADMIN")
                .membership("premium")
                .storageUsed(0L)
                .storageLimit(1024L * 1024 * 1024 * 100)  // 100GB
                .createdAt(LocalDateTime.now())
                .lastLoginAt(null)
                .build();
        userRepository.save(adminUser);
        log.info("创建管理员用户: admin / Admin@123");
        
        // 创建普通用户
        User defaultUser = User.builder()
                .id("user-1")
                .username("user")
                .password(passwordEncoder.encode("User@123"))  // BCrypt 加密
                .email("user@example.com")
                .avatarUrl(null)
                .nickname("普通用户")
                .bio("普通用户账号")
                .phone("13800138000")
                .role("user")
                .membership("pro")
                .storageUsed(1024L * 1024 * 1024 * 5)  // 5GB
                .storageLimit(1024L * 1024 * 1024 * 50)  // 50GB
                .createdAt(LocalDateTime.now())
                .lastLoginAt(null)
                .build();
        userRepository.save(defaultUser);
        log.info("创建普通用户: user / User@123");
        
        // 初始化用户设置
        UserSettings settings = UserSettings.builder()
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
        userSettingsMap.put("user-1", settings);
        
        log.info("默认用户初始化完成");
    }
    
    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("用户登录：{}", request.getUsername());
        
        // 速率限制检查（仅当用户名存在时才检查）
        if (request.getUsername() != null && !request.getUsername().isEmpty()) {
            String clientId = request.getUsername().toLowerCase();
            if (!RateLimiter.allow(clientId, RateLimiter.LimitType.LOGIN)) {
                long resetTime = RateLimiter.getResetTime(clientId, RateLimiter.LimitType.LOGIN);
                throw new RateLimitException("登录尝试次数过多，请在 " + resetTime + " 秒后重试");
            }
        }
        
        // 查找用户
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户名或密码错误"));
        
        // 验证密码
        boolean passwordValid = false;
        String storedPassword = user.getPassword();
        
        // 检测密码格式
        if (storedPassword != null && storedPassword.startsWith("$2")) {
            // BCrypt 格式
            passwordValid = passwordEncoder.matches(request.getPassword(), storedPassword);
        } else {
            // 明文格式（兼容旧数据）
            passwordValid = storedPassword != null && storedPassword.equals(request.getPassword());
            if (passwordValid) {
                // 自动升级为 BCrypt
                user.setPassword(passwordEncoder.encode(request.getPassword()));
                userRepository.save(user);
                log.info("密码已自动升级为 BCrypt 格式：{}", request.getUsername());
            }
        }
        
        if (!passwordValid) {
            log.warn("密码错误：{}", request.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 检查该用户的会话数量
        AtomicInteger sessionCount = userSessionCount.computeIfAbsent(
                user.getId(), k -> new AtomicInteger(0));
        
        // 如果超过最大会话数，删除最早的会话
        if (sessionCount.get() >= MAX_SESSIONS_PER_USER) {
            removeOldestSession(user.getId());
        }
        
        // 创建会话
        String sessionId = generateSecureSessionId();
        // 标准化角色：统一存储为大写 "ADMIN"，支持数据库中的各种大小写形式
        String normalizedRole = "ADMIN".equalsIgnoreCase(user.getRole()) ? "ADMIN" : user.getRole();
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty() ? user.getAvatarUrl() : null)
                .role(normalizedRole)
                .membership(user.getMembership())
                .build();
        
        boolean rememberMe = request.getRememberMe() != null && request.getRememberMe();
        SessionInfo sessionInfo = new SessionInfo(userInfo, rememberMe);
        sessions.put(sessionId, sessionInfo);
        
        // 增加用户会话计数
        sessionCount.incrementAndGet();
        
        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        log.info("用户登录成功：{}, 会话数: {}", request.getUsername(), sessionCount.get());
        
        return LoginResponse.builder()
                .sessionId(sessionId)
                .user(userInfo)
                .expiresIn(rememberMe ? SESSION_TIMEOUT_REMEMBER : SESSION_TIMEOUT)
                .build();
    }
    
    /**
     * 生成安全的 Session ID
     */
    private String generateSecureSessionId() {
        // 使用 UUID + 时间戳 + 随机数的组合，并进行 SHA-256 哈希
        try {
            String raw = UUID.randomUUID().toString() + System.currentTimeMillis() 
                    + Double.toString(Math.random());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            
            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 总是存在，如果失败则使用纯 UUID
            log.error("SHA-256 算法不可用", e);
            return UUID.randomUUID().toString();
        }
    }
    
    /**
     * 删除最早的会话
     */
    private void removeOldestSession(String userId) {
        SessionInfo oldest = null;
        String oldestKey = null;
        
        for (Map.Entry<String, SessionInfo> entry : sessions.entrySet()) {
            if (entry.getValue().userInfo.getId().equals(userId)) {
                if (oldest == null || entry.getValue().createTime < oldest.createTime) {
                    oldest = entry.getValue();
                    oldestKey = entry.getKey();
                }
            }
        }
        
        if (oldestKey != null) {
            sessions.remove(oldestKey);
            log.info("删除最早会话: userId={}, sessionCount={}", userId, 
                    userSessionCount.get(userId).decrementAndGet());
        }
    }
    
    @Override
    public void logout(String sessionId) {
        SessionInfo sessionInfo = sessions.remove(sessionId);
        if (sessionInfo != null) {
            AtomicInteger count = userSessionCount.get(sessionInfo.userInfo.getId());
            if (count != null) {
                count.decrementAndGet();
            }
            log.info("用户登出：{}", sessionInfo.userInfo.getUsername());
        }
    }
    
    @Override
    public LoginResponse.UserInfo validateSession(String sessionId) {
        SessionInfo sessionInfo = sessions.get(sessionId);
        
        if (sessionInfo == null) {
            return null;
        }
        
        // 检查是否过期
        if (sessionInfo.isExpired()) {
            sessions.remove(sessionId);
            AtomicInteger count = userSessionCount.get(sessionInfo.userInfo.getId());
            if (count != null) {
                count.decrementAndGet();
            }
            log.info("会话已过期：{}", sessionId);
            return null;
        }
        
        // 检查是否需要续期
        if (sessionInfo.needsRenewal()) {
            sessionInfo.renew();
            log.debug("会话自动续期：{}", sessionId);
        }
        
        // 更新最后访问时间
        sessionInfo.updateAccess();
        
        return sessionInfo.userInfo;
    }
    
    @Override
    public void updateProfile(String userId, UpdateProfileRequest request) {
        log.info("更新用户资料：{}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        if (request.getUsername() != null) {
            user.setUsername(request.getUsername());
        }
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        if (request.getEmail() != null) {
            user.setEmail(request.getEmail());
        }
        if (request.getAvatar() != null) {
            user.setAvatarUrl(request.getAvatar());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        
        userRepository.save(user);
    }
    
    @Override
    public void changePassword(String userId, String currentPassword, String newPassword) {
        log.info("修改密码：{}", userId);
        
        // 速率限制检查
        if (!RateLimiter.allow(userId, RateLimiter.LimitType.PASSWORD_CHANGE)) {
            long resetTime = RateLimiter.getResetTime(userId, RateLimiter.LimitType.PASSWORD_CHANGE);
            throw new RateLimitException("密码修改尝试次数过多，请在 " + resetTime + " 秒后重试");
        }
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        // 验证当前密码（使用 BCrypt）
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("修改密码失败，当前密码错误：{}", userId);
            throw new RuntimeException("当前密码错误");
        }
        
        // 验证新密码强度
        PasswordValidator.Strength strength = PasswordValidator.checkStrength(newPassword);
        if (strength.getLevel() < PasswordValidator.Strength.FAIR.getLevel()) {
            String desc = PasswordValidator.getStrengthDescription(newPassword);
            throw new WeakPasswordException(desc);
        }
        
        // 不能与当前密码相同
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("新密码不能与当前密码相同");
        }
        
        // 加密并保存新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("密码修改成功：{}", userId);
    }
    
    @Override
    public UserSettings getUserSettings(String userId) {
        log.info("获取用户设置：{}", userId);
        return userSettingsMap.getOrDefault(userId, UserSettings.builder()
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
                .build());
    }
    
    @Override
    public void updateUserSettings(String userId, UserSettings settings) {
        log.info("更新用户设置：{}", userId);
        userSettingsMap.put(userId, settings);
    }
}
