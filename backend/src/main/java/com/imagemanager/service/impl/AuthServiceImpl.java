package com.imagemanager.service.impl;

import com.imagemanager.dto.LoginRequest;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.dto.RegisterRequest;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import java.util.Objects;

/**
 * 认证服务实现类 — Redis Session 存储
 *
 * Session 存储结构：
 *   session:{sessionId}  → Hash (userInfo JSON, createTime, expiresAt, rememberMe)
 *   user:session:{userId} → String (当前有效的 sessionId，SSO 单点登录)
 *
 * @author Image Manager Team
 * @version 3.0.0
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private com.imagemanager.cache.LlmCacheService llmCacheService;

    // ============ Redis Key 前缀 ============
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String USER_SESSION_KEY_PREFIX = "user:session:";

    // ============ Session 过期时间 ============
    private static final long SESSION_TIMEOUT_HOURS = 24;
    private static final long SESSION_TIMEOUT_REMEMBER_HOURS = 7 * 24; // 7天

    // Session 续期阈值（剩余时间少于此值时自动续期）
    private static final long SESSION_RENEWAL_THRESHOLD_HOURS = 2;

    // 用户设置存储（生产环境应存储在数据库）
    private final Map<String, UserSettings> userSettingsMap = new HashMap<>();

    // ============ 种子账号配置（安全：仅 local 环境播种，密码必须来自环境变量） ============
    @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:local}")
    private String activeProfile;

    @org.springframework.beans.factory.annotation.Value("${app.seed.superadmin-password:}")
    private String seedSuperadminPassword;

    @org.springframework.beans.factory.annotation.Value("${app.seed.admin-password:}")
    private String seedAdminPassword;

    @org.springframework.beans.factory.annotation.Value("${app.seed.user-password:}")
    private String seedUserPassword;

    /**
     * 初始化默认用户（种子账号）
     * 安全约束：
     * 1. 仅 local 环境播种，生产环境跳过（账号由管理员手工创建）
     * 2. 密码一律从环境变量读取，未配置则跳过对应账号，代码无任何默认密码
     * 3. 日志不打印明文密码
     * 4. 种子账号 mustChangePassword=true，强制首次登录改密
     */
    @PostConstruct
    public void initDefaultData() {
        if (!"local".equalsIgnoreCase(activeProfile) && !"dev".equalsIgnoreCase(activeProfile)) {
            log.info("当前环境[{}]非本地环境，跳过种子账号初始化", activeProfile);
            return;
        }

        // 三级权限：预置超级管理员（独立判断，兼容已有环境）
        seedUserIfAbsent("superadmin", "superadmin-1", "超级管理员", "superadmin@example.com",
                "superadmin", "premium", seedSuperadminPassword);

        if (userRepository.count() == 0) {
            seedUserIfAbsent("admin", "admin-1", "Administrator", "admin@example.com",
                    "admin", "premium", seedAdminPassword);
            boolean userSeeded = seedUserIfAbsent("user", "user-1", "普通用户", "user@example.com",
                    "user", "pro", seedUserPassword);

            if (userSeeded) {
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
            }
        }
    }

    /**
     * 按用户名播种账号（密码来自环境变量，未配置则跳过；日志不打印密码）
     */
    private boolean seedUserIfAbsent(String username, String id, String nickname, String email,
                                     String role, String membership, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            return false;
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            log.warn("种子账号[{}]未配置环境变量密码（seed.{}-password / SEED_*），跳过创建", username, username);
            return false;
        }
        User u = User.builder()
                .id(id)
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .email(email)
                .avatarUrl(null)
                .nickname(nickname)
                .bio("种子账号")
                .phone(null)
                .role(role)
                .membership(membership)
                .storageUsed(0L)
                .storageLimit(1024L * 1024 * 1024 * 100)
                .createdAt(LocalDateTime.now())
                .lastLoginAt(null)
                .mustChangePassword(true)
                .build();
        userRepository.save(u);
        log.info("创建种子账号: {}（角色 {}，首次登录需改密）", username, role);
        return true;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        log.info("用户登录：{}", request.getUsername());

        // 速率限制检查
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

        if (storedPassword != null && storedPassword.startsWith("$2")) {
            passwordValid = passwordEncoder.matches(request.getPassword(), storedPassword);
        } else {
            passwordValid = storedPassword != null && storedPassword.equals(request.getPassword());
            if (passwordValid) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
                userRepository.save(user);
            }
        }

        if (!passwordValid) {
            log.warn("密码错误：{}", request.getUsername());
            throw new RuntimeException("用户名或密码错误");
        }

        // ============ SSO: 检查用户是否已有活跃会话 ============
        String userId = user.getId();
        String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
        String oldSessionId = redisTemplate.opsForValue().get(userSessionKey);
        boolean forceLogin = request.getForceLogin() != null && request.getForceLogin();

        if (oldSessionId != null && !forceLogin) {
            // 验证旧 session 是否真的还活着
            String sessionKey = SESSION_KEY_PREFIX + oldSessionId;
            Map<Object, Object> oldSession = redisTemplate.opsForHash().entries(sessionKey);
            
            if (!oldSession.isEmpty()) {
                // 用户已有活跃会话，返回提示让前端确认
                log.info("SSO: 用户 {} 已有活跃会话(oldSessionId={}***), 等待确认是否踢掉",
                        request.getUsername(), oldSessionId.substring(0, Math.min(8, oldSessionId.length())));
                return LoginResponse.builder()
                        .alreadyLoggedIn(true)
                        .message("该账户已在其他地方登录，确认登录将使之前的登录失效")
                        .build();
            } else {
                // 旧 session 已过期，清理映射
                redisTemplate.delete(userSessionKey);
            }
        }

        if (oldSessionId != null && forceLogin) {
            // 强制登录，踢掉旧会话
            redisTemplate.delete(SESSION_KEY_PREFIX + oldSessionId);
            log.info("SSO: 踢掉用户 {} 的旧会话 {}", request.getUsername(), oldSessionId.substring(0, Math.min(8, oldSessionId.length())));
        }

        // ============ 创建新 session ============
        String sessionId = generateSecureSessionId();
        String effectiveCompany = user.getCompany();
        if (effectiveCompany == null || effectiveCompany.trim().isEmpty()) {
            effectiveCompany = request.getCompany();
        }
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(userId)
                .username(user.getUsername())
                .email(user.getEmail())
                .avatar(user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty() ? user.getAvatarUrl() : null)
                .role(user.getRole())
                .membership(user.getMembership())
                .company(effectiveCompany)
                .mustChangePassword(Boolean.TRUE.equals(user.getMustChangePassword()))
                .build();

        boolean rememberMe = request.getRememberMe() != null && request.getRememberMe();
        long timeoutHours = rememberMe ? SESSION_TIMEOUT_REMEMBER_HOURS : SESSION_TIMEOUT_HOURS;
        long expiresAt = System.currentTimeMillis() + timeoutHours * 60 * 60 * 1000;

        // 存入 Redis
        saveSessionToRedis(sessionId, userInfo, rememberMe, expiresAt, timeoutHours);

        // SSO: 记录用户当前 sessionId
        redisTemplate.opsForValue().set(userSessionKey, Objects.requireNonNull(sessionId), timeoutHours, TimeUnit.HOURS);

        // 验证存储是否成功
        String verifySessionId = redisTemplate.opsForValue().get(userSessionKey);

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("用户登录成功：{}, SSO会话已建立", request.getUsername());

        return LoginResponse.builder()
                .sessionId(sessionId)
                .user(userInfo)
                .expiresIn(timeoutHours * 60 * 60 * 1000)
                .build();
    }

    @Override
    public LoginResponse register(RegisterRequest request) {
        log.info("用户注册：username={}, company={}", request.getUsername(), request.getCompany());

        if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().length() < 6) {
            throw new RuntimeException("密码长度不能少于6位");
        }
        if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (request.getCompany() == null || request.getCompany().trim().isEmpty()) {
            throw new RuntimeException("请选择所属公司");
        }
        if (!"宝娜斯集团".equals(request.getCompany())) {
            throw new RuntimeException("公司只能选择宝娜斯集团");
        }

        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("邮箱已被注册");
        }

        String userId = UUID.randomUUID().toString();
        User newUser = User.builder()
                .id(userId)
                .username(request.getUsername().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail().trim())
                .nickname(request.getUsername().trim())
                .role("user")
                .company(request.getCompany())
                .membership("free")
                .storageUsed(0L)
                .storageLimit(1024L * 1024 * 1024 * 10L)
                .createdAt(LocalDateTime.now())
                .build();

        userRepository.save(newUser);
        log.info("用户注册成功：{}, 公司：{}", request.getUsername(), request.getCompany());

        // 自动登录
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(request.getUsername());
        loginRequest.setPassword(request.getPassword());
        loginRequest.setRememberMe(true);
        return login(loginRequest);
    }

    @Override
    public void logout(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }

        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);

        if (!sessionData.isEmpty()) {
            String userId = (String) sessionData.get("userId");
            String username = (String) sessionData.get("username");

            // 删除 session
            redisTemplate.delete(sessionKey);

            // SSO: 清除用户当前 session 映射
            if (userId != null) {
                String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
                String currentSessionId = redisTemplate.opsForValue().get(userSessionKey);
                if (sessionId.equals(currentSessionId)) {
                    redisTemplate.delete(userSessionKey);
                }
            }


            // 清理用户 LLM 缓存
            if (userId != null) {
                try {
                    llmCacheService.clearUserCache(userId);
                } catch (Exception e) {
                    log.warn("清理用户LLM缓存失败: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public LoginResponse.UserInfo validateSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;

        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);

        if (sessionData.isEmpty()) {
            return null;
        }

        // 检查是否过期
        long expiresAt = Long.parseLong((String) sessionData.getOrDefault("expiresAt", "0"));
        if (System.currentTimeMillis() > expiresAt) {
            // 已过期，清理
            String userId = (String) sessionData.get("userId");
            redisTemplate.delete(sessionKey);
            if (userId != null) {
                String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
                String currentSessionId = redisTemplate.opsForValue().get(userSessionKey);
                if (sessionId.equals(currentSessionId)) {
                    redisTemplate.delete(userSessionKey);
                }
            }
            return null;
        }

        // SSO 校验：确保此 session 仍然是该用户的当前 session
        String userId = (String) sessionData.get("userId");
        if (userId != null) {
            String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
            String currentSessionId = redisTemplate.opsForValue().get(userSessionKey);
            if (!sessionId.equals(currentSessionId)) {
                // 此 session 已被新登录踢掉，清理
                redisTemplate.delete(sessionKey);
                return null;
            }
        }

        // 构造 UserInfo
        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id((String) sessionData.get("userId"))
                .username((String) sessionData.get("username"))
                .email((String) sessionData.get("email"))
                .avatar((String) sessionData.get("avatar"))
                .role((String) sessionData.get("role"))
                .membership((String) sessionData.get("membership"))
                .company((String) sessionData.get("company"))
                .mustChangePassword(Boolean.parseBoolean((String) sessionData.getOrDefault("mustChangePassword", "false")))
                .build();

        // 续期逻辑：剩余时间不足 2 小时则自动续期
        long remainingMs = expiresAt - System.currentTimeMillis();
        long renewalThresholdMs = SESSION_RENEWAL_THRESHOLD_HOURS * 60 * 60 * 1000;
        if (remainingMs < renewalThresholdMs && remainingMs > 0) {
            boolean rememberMe = Boolean.parseBoolean((String) sessionData.getOrDefault("rememberMe", "false"));
            long timeoutHours = rememberMe ? SESSION_TIMEOUT_REMEMBER_HOURS : SESSION_TIMEOUT_HOURS;
            long newExpiresAt = System.currentTimeMillis() + timeoutHours * 60 * 60 * 1000;

            // 更新 expiresAt
            redisTemplate.opsForHash().put(sessionKey, "expiresAt", Objects.requireNonNull(String.valueOf(newExpiresAt)));

            // 重设 TTL
            redisTemplate.expire(sessionKey, timeoutHours, TimeUnit.HOURS);

            // 同时更新 SSO mapping 的 TTL
            if (userId != null) {
                String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
                redisTemplate.expire(userSessionKey, timeoutHours, TimeUnit.HOURS);
            }

        }

        // 更新最后访问时间
        redisTemplate.opsForHash().put(sessionKey, "lastAccessAt", Objects.requireNonNull(String.valueOf(System.currentTimeMillis())));

        return userInfo;
    }

    @Override
    public void updateProfile(String userId, UpdateProfileRequest request) {

        User user = userRepository.findById(Objects.requireNonNull(userId))
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (request.getUsername() != null) user.setUsername(request.getUsername());
        if (request.getNickname() != null) user.setNickname(request.getNickname());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        if (request.getAvatar() != null) user.setAvatarUrl(request.getAvatar());
        if (request.getBio() != null) user.setBio(request.getBio());
        if (request.getPhone() != null) user.setPhone(request.getPhone());

        userRepository.save(Objects.requireNonNull(user));

        // 同步更新 Redis 中所有该用户的 session 信息
        syncUserInfoToRedis(userId, user);
    }

    @Override
    public void changePassword(String userId, String currentPassword, String newPassword) {

        if (!RateLimiter.allow(userId, RateLimiter.LimitType.PASSWORD_CHANGE)) {
            long resetTime = RateLimiter.getResetTime(userId, RateLimiter.LimitType.PASSWORD_CHANGE);
            throw new RateLimitException("密码修改尝试次数过多，请在 " + resetTime + " 秒后重试");
        }

        User user = userRepository.findById(Objects.requireNonNull(userId))
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            log.warn("修改密码失败，当前密码错误：{}", userId);
            throw new RuntimeException("当前密码错误");
        }

        PasswordValidator.Strength strength = PasswordValidator.checkStrength(newPassword);
        if (strength.getLevel() < PasswordValidator.Strength.FAIR.getLevel()) {
            String desc = PasswordValidator.getStrengthDescription(newPassword);
            throw new WeakPasswordException(desc);
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new RuntimeException("新密码不能与当前密码相同");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        // 改密成功后清除强制改密标志
        user.setMustChangePassword(false);
        userRepository.save(user);

        // 修改密码后踢掉所有 session，强制重新登录
        forceLogoutUser(userId);

    }

    @Override
    public UserSettings getUserSettings(String userId) {
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
        userSettingsMap.put(userId, settings);
    }

    @Override
    public void deleteAllUserSessions(String userId) {
        forceLogoutUser(userId);
    }

    @Override
    @Transactional
    public boolean bindCompany(String userId, String company) {
        String currentCompany = null;
        try {
            currentCompany = jdbcTemplate.queryForObject(
                "SELECT company FROM users WHERE id = ?::uuid", String.class, userId);
        } catch (@SuppressWarnings("unused") Exception e) {
            try {
                currentCompany = jdbcTemplate.queryForObject(
                    "SELECT company FROM users WHERE id = ?", String.class, userId);
            } catch (Exception ex) {
                log.error("查询用户公司失败: userId={}", userId, ex);
                return false;
            }
        }

        if (currentCompany != null && !currentCompany.trim().isEmpty()) {
            log.warn("用户已绑定公司，不可更改: userId={}, currentCompany={}", userId, currentCompany);
            return false;
        }

        int updated;
        try {
            updated = jdbcTemplate.update(
                "UPDATE users SET company = ? WHERE id = ?::uuid AND (company IS NULL OR company = '')",
                company, userId);
        } catch (@SuppressWarnings("unused") Exception e) {
            try {
                updated = jdbcTemplate.update(
                    "UPDATE users SET company = ? WHERE id = ? AND (company IS NULL OR company = '')",
                    company, userId);
            } catch (Exception ex) {
                log.error("绑定公司失败: userId={}", userId, ex);
                return false;
            }
        }

        if (updated > 0) {
            log.info("公司绑定成功: userId={}, company={}", userId, company);
            return true;
        } else {
            log.warn("公司绑定失败（可能已被其他请求绑定）: userId={}", userId);
            return false;
        }
    }

    // ============ 私有方法 ============

    /**
     * 生成安全的 Session ID
     */
    private String generateSecureSessionId() {
        try {
            String raw = UUID.randomUUID().toString() + System.currentTimeMillis()
                    + Double.toString(Math.random());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 算法不可用", e);
            return UUID.randomUUID().toString();
        }
    }

    /**
     * 将 session 数据存入 Redis Hash
     */
    private void saveSessionToRedis(String sessionId, LoginResponse.UserInfo userInfo,
                                     boolean rememberMe, long expiresAt, long timeoutHours) {
        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        Map<String, String> sessionData = new HashMap<>();
        sessionData.put("userId", userInfo.getId());
        sessionData.put("username", userInfo.getUsername());
        sessionData.put("email", userInfo.getEmail() != null ? userInfo.getEmail() : "");
        sessionData.put("avatar", userInfo.getAvatar() != null ? userInfo.getAvatar() : "");
        sessionData.put("role", userInfo.getRole());
        sessionData.put("membership", userInfo.getMembership() != null ? userInfo.getMembership() : "");
        sessionData.put("company", userInfo.getCompany() != null ? userInfo.getCompany() : "");
        sessionData.put("mustChangePassword", String.valueOf(Boolean.TRUE.equals(userInfo.getMustChangePassword())));
        sessionData.put("rememberMe", String.valueOf(rememberMe));
        sessionData.put("createTime", String.valueOf(System.currentTimeMillis()));
        sessionData.put("lastAccessAt", String.valueOf(System.currentTimeMillis()));
        sessionData.put("expiresAt", String.valueOf(expiresAt));

        redisTemplate.opsForHash().putAll(sessionKey, sessionData);
        // 设置 TTL（Redis 自动过期清理）
        redisTemplate.expire(sessionKey, timeoutHours, TimeUnit.HOURS);
    }

    /**
     * 用户信息变更后，同步更新 Redis 中的 session
     */
    private void syncUserInfoToRedis(String userId, User user) {
        String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
        String sessionId = redisTemplate.opsForValue().get(userSessionKey);
        if (sessionId == null) return;

        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        Map<Object, Object> sessionData = redisTemplate.opsForHash().entries(sessionKey);
        if (sessionData.isEmpty()) return;

        // 更新变更的字段
        redisTemplate.opsForHash().put(sessionKey, "username", Objects.requireNonNull(user.getUsername() != null ? user.getUsername() : ""));
        redisTemplate.opsForHash().put(sessionKey, "email", Objects.requireNonNull(user.getEmail() != null ? user.getEmail() : ""));
        redisTemplate.opsForHash().put(sessionKey, "avatar", Objects.requireNonNull(user.getAvatarUrl() != null ? user.getAvatarUrl() : ""));

    }

    /**
     * 强制用户下线（删除所有 session）
     */
    private void forceLogoutUser(String userId) {
        String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
        String sessionId = redisTemplate.opsForValue().get(userSessionKey);

        if (sessionId != null) {
            redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        }
        redisTemplate.delete(userSessionKey);

    }
}
