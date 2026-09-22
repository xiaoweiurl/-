package com.imagemanager.org;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dto.DingTalkContactCandidate;
import com.imagemanager.dto.RegisterRequest;
import com.imagemanager.entity.User;
import com.imagemanager.exception.RegisterMatchException;
import com.imagemanager.repository.UserRepository;
import com.imagemanager.service.ImageTableService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 按钉钉通讯录姓名注册本地账号。集团与袜业等分部均在同一通讯录；
 * 姓名未命中时仍可兜底开户。本地 {@code users.company} 固定为宝娜斯集团。
 * 默认密码固定为 {@link #DEFAULT_PASSWORD}，并标记 must_change_password，走现有首次改密流程。
 */
@Slf4j
@Service
public class OrgRegistrationService {

    /** 钉钉姓名注册 / 组织同步创建的本地账号初始密码 */
    public static final String DEFAULT_PASSWORD = "123456";

    public static final String DEFAULT_COMPANY = "宝娜斯集团";

    private static final String PLACEHOLDER_EMAIL_DOMAIN = "@dingtalk.invalid";

    private final OrgDirectory orgDirectory;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DingTalkProperties properties;
    private final TransactionTemplate txTemplate;
    private final ImageTableService imageTableService;

    public OrgRegistrationService(OrgDirectory orgDirectory,
                                  UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  DingTalkProperties properties,
                                  PlatformTransactionManager transactionManager,
                                  @Nullable ImageTableService imageTableService) {
        this.orgDirectory = orgDirectory;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.imageTableService = imageTableService;
    }

    /**
     * 打样推送前幂等开户：仅用已同步的钉钉通讯录（{@code org_users}）按姓名精确匹配。
     * 匹配顺序：已绑定 local_user_id → 已有 users.dingtalk_userid → 本地 username/nickname
     * 唯一同名则复用并回填钉钉字段 → 否则创建。本地同名多人视为歧义，不创建。
     * 同名多人 / 无匹配 / 异常不抛出，由调用方取消工作通知（不得在无账号时推送）。
     */
    public EnsureAccountResult ensureAccountByName(String name) {
        if (OrgNameMatcher.isBlank(name) || OrgNameMatcher.normalize(name).isEmpty()) {
            return EnsureAccountResult.skippedBlank();
        }
        try {
            RegisterRequest request = new RegisterRequest();
            request.setName(name.trim());
            ProvisionResult provisioned = provision(request, false);
            ensureImageTableQuietly(provisioned.user);
            if (provisioned.reusedByName) {
                log.info("打样推送复用同名本地账号: name={}, userId={}, username={}, dingUserId={}",
                        name, provisioned.user.getId(), provisioned.user.getUsername(),
                        provisioned.user.getDingtalkUserid());
                return EnsureAccountResult.alreadyExists(provisioned.user, "reused existing local user by name");
            }
            log.info("打样推送自动开通本地账号: name={}, userId={}, username={}, dingUserId={}",
                    name, provisioned.user.getId(), provisioned.user.getUsername(),
                    provisioned.user.getDingtalkUserid());
            return EnsureAccountResult.created(provisioned.user);
        } catch (RegisterMatchException e) {
            if (e.getKind() == RegisterMatchException.Kind.ALREADY_REGISTERED) {
                log.debug("打样推送跳过自动开户：通讯录成员已绑定本地账号 name={}", name);
                return EnsureAccountResult.alreadyExists(e.getMessage());
            }
            if (e.getKind() == RegisterMatchException.Kind.MULTIPLE) {
                log.info("打样推送跳过自动开户：同名多人无法唯一开户 name={}", name);
                return EnsureAccountResult.skippedAmbiguous(e.getMessage());
            }
            log.info("打样推送跳过自动开户：通讯录无唯一匹配 name={}, kind={}, msg={}",
                    name, e.getKind(), e.getMessage());
            return EnsureAccountResult.skippedNoMatch(e.getMessage());
        } catch (Exception e) {
            log.warn("打样推送自动开户失败（应取消工作通知）: name={}, err={}", name, e.getMessage());
            return EnsureAccountResult.failed(e.getMessage());
        }
    }

    public User register(RegisterRequest request) {
        return provision(request, true).user;
    }

    /**
     * @param allowCustomWithoutOrg true=公开注册：通讯录没有该人仍可开户，公司固定宝娜斯集团。
     *                              false=打样推送开户，未命中通讯录则失败。
     */
    private ProvisionResult provision(RegisterRequest request, boolean allowCustomWithoutOrg) {
        String company = resolveCompany(request == null ? null : request.getCompany());
        String name = OrgNameMatcher.requireName(request == null ? null
                : OrgNameMatcher.firstNonBlank(request.getName(), request.getUsername()));
        String dingUserId = request == null ? null : request.getDingtalkUserid();

        int synced = orgDirectory.countActiveContacts(company);
        if (synced == 0 && !allowCustomWithoutOrg) {
            throw RegisterMatchException.notSynced();
        }

        OrgContact contact = null;
        if (dingUserId != null && !dingUserId.isBlank()) {
            contact = orgDirectory.findByDingUserId(company, dingUserId)
                    .orElseThrow(RegisterMatchException::none);
            if (!OrgNameMatcher.namesEqual(name, contact.getName())) {
                throw new RegisterMatchException(400, "所选成员与姓名不匹配");
            }
        } else if (synced > 0) {
            List<OrgContact> matches = orgDirectory.findActiveByName(company, name);
            if (matches.size() > 1) {
                throw RegisterMatchException.multiple(toCandidates(matches));
            }
            if (matches.size() == 1) {
                contact = matches.get(0);
            } else if (!allowCustomWithoutOrg) {
                throw RegisterMatchException.none();
            }
        }

        if (contact != null) {
            if (contact.getLocalUserId() != null && !contact.getLocalUserId().isBlank()) {
                throw RegisterMatchException.alreadyRegistered();
            }
            OrgContact matched = contact;
            ProvisionResult created = txTemplate.execute(status -> createUser(company, name, matched));
            if (created == null) {
                throw new IllegalStateException("注册事务未提交");
            }
            log.info("钉钉姓名注册{}: name={}, dingUserId={}, username={}",
                    created.reusedByName ? "复用同名账号" : "成功",
                    name, matched.getDingUserId(), created.user.getUsername());
            return created;
        }

        if (!allowCustomWithoutOrg) {
            throw RegisterMatchException.notSynced();
        }

        ProvisionResult created = txTemplate.execute(status -> createCustomUser(company, name));
        if (created == null) {
            throw new IllegalStateException("注册事务未提交");
        }
        log.info("自定义注册成功（未匹配集团通讯录）: name={}, username={}, company={}",
                name, created.user.getUsername(), company);
        return created;
    }

    public List<DingTalkContactCandidate> searchContacts(String company, String keyword) {
        return toCandidates(orgDirectory.searchContacts(resolveCompany(company), keyword, 50));
    }

    private ProvisionResult createUser(String company, String displayName, OrgContact contact) {
        // 1) users.dingtalk_userid already equals this contact → reuse (orphan bind retry).
        Optional<User> existing = userRepository.findByDingtalkUserid(contact.getDingUserId());
        if (existing.isPresent()) {
            User user = existing.get();
            orgDirectory.bindLocalUser(contact.getId(), user.getId());
            return ProvisionResult.reusedDing(user);
        }

        String matchName = OrgNameMatcher.firstNonBlank(contact.getName(), displayName);
        List<User> nameMatches = findLocalUsersByDisplayName(matchName);
        if (nameMatches.size() > 1) {
            // 4) Multiple local same-name users → do not guess, do not allocate dingUserId username.
            throw RegisterMatchException.multipleLocal(matchName);
        }
        if (nameMatches.size() == 1) {
            User candidate = nameMatches.get(0);
            if (isReusableForContact(candidate, contact.getDingUserId())) {
                // 3) Unique username/nickname match → bind and fill missing DingTalk fields.
                User reused = bindExistingLocalUser(company, contact, candidate);
                return ProvisionResult.reusedByName(reused);
            }
            // Preferred username occupied by a different DingTalk person: fall through to allocateUsername.
        }

        String username = allocateUsername(displayName, contact.getDingUserId());
        String email = allocateEmail(contact);
        String orgDeptId = contact.getPrimaryDingDeptId() == null
                ? null
                : orgDirectory.findLocalDeptId(company, contact.getPrimaryDingDeptId()).orElse(null);

        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .email(email)
                .nickname(contact.getName())
                .phone(contact.getMobile())
                .avatarUrl(contact.getAvatarUrl())
                .role("user")
                .company(company)
                .membership("free")
                .storageUsed(0L)
                .storageLimit(1024L * 1024 * 1024 * 10L)
                .createdAt(LocalDateTime.now())
                .mustChangePassword(true)
                .dingtalkUserid(contact.getDingUserId())
                .dingtalkUnionid(contact.getDingUnionId())
                .jobTitle(contact.getJobTitle())
                .orgDeptId(orgDeptId)
                .dingSyncedAt(LocalDateTime.now())
                .build();
        // JdbcTemplate bind runs in the same TX but does not trigger Hibernate auto-flush.
        // Without flush, Postgres rejects org_users.local_user_id FK (user row not visible yet).
        userRepository.saveAndFlush(user);
        orgDirectory.bindLocalUser(contact.getId(), user.getId());
        return ProvisionResult.created(user);
    }

    /**
     * 通讯录未命中时的兜底开户（不绑钉钉）。袜业分部已纳入组织同步，能匹配到的人走 {@link #createUser}。
     */
    private ProvisionResult createCustomUser(String company, String displayName) {
        List<User> nameMatches = findLocalUsersByDisplayName(displayName);
        if (nameMatches.size() > 1) {
            throw RegisterMatchException.multipleLocal(displayName);
        }
        if (nameMatches.size() == 1) {
            throw RegisterMatchException.alreadyRegistered();
        }

        String username = allocateUsername(displayName, null);
        String email = allocatePlaceholderEmail(null);
        User user = User.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .email(email)
                .nickname(displayName)
                .role("user")
                .company(company)
                .membership("free")
                .storageUsed(0L)
                .storageLimit(1024L * 1024 * 1024 * 10L)
                .createdAt(LocalDateTime.now())
                .mustChangePassword(true)
                .build();
        userRepository.saveAndFlush(user);
        return ProvisionResult.created(user);
    }

    /**
     * 按 OrgNameMatcher 规则在 username / nickname 上查找本地用户（去空白、忽略大小写）。
     */
    List<User> findLocalUsersByDisplayName(String name) {
        if (OrgNameMatcher.isBlank(name) || OrgNameMatcher.normalize(name).isEmpty()) {
            return List.of();
        }
        Map<String, User> byId = new LinkedHashMap<>();
        for (String key : OrgNameMatcher.lookupKeys(name)) {
            userRepository.findByUsername(key).ifPresent(u -> {
                if (u.getId() != null) {
                    byId.put(u.getId(), u);
                }
            });
            List<User> nick = userRepository.findByNickname(key);
            if (nick != null) {
                for (User u : nick) {
                    if (u != null && u.getId() != null) {
                        byId.put(u.getId(), u);
                    }
                }
            }
        }
        List<User> matched = new ArrayList<>();
        for (User user : byId.values()) {
            if (OrgNameMatcher.matchesUserDisplayName(name, user.getUsername(), user.getNickname())) {
                matched.add(user);
            }
        }
        return matched;
    }

    private static boolean isReusableForContact(User user, String dingUserId) {
        String existing = user.getDingtalkUserid();
        return OrgNameMatcher.isBlank(existing) || existing.equals(dingUserId);
    }

    private User bindExistingLocalUser(String company, OrgContact contact, User user) {
        if (OrgNameMatcher.isBlank(user.getDingtalkUserid()) && !OrgNameMatcher.isBlank(contact.getDingUserId())) {
            user.setDingtalkUserid(contact.getDingUserId());
        }
        if (OrgNameMatcher.isBlank(user.getDingtalkUnionid()) && !OrgNameMatcher.isBlank(contact.getDingUnionId())) {
            user.setDingtalkUnionid(contact.getDingUnionId());
        }
        if (OrgNameMatcher.isBlank(user.getAvatarUrl()) && !OrgNameMatcher.isBlank(contact.getAvatarUrl())) {
            user.setAvatarUrl(contact.getAvatarUrl());
        }
        if (OrgNameMatcher.isBlank(user.getPhone()) && !OrgNameMatcher.isBlank(contact.getMobile())) {
            user.setPhone(contact.getMobile());
        }
        if (OrgNameMatcher.isBlank(user.getJobTitle()) && !OrgNameMatcher.isBlank(contact.getJobTitle())) {
            user.setJobTitle(contact.getJobTitle());
        }
        if (OrgNameMatcher.isBlank(user.getOrgDeptId()) && contact.getPrimaryDingDeptId() != null) {
            orgDirectory.findLocalDeptId(company, contact.getPrimaryDingDeptId())
                    .ifPresent(user::setOrgDeptId);
        }
        user.setDingSyncedAt(LocalDateTime.now());
        userRepository.save(user);
        orgDirectory.bindLocalUser(contact.getId(), user.getId());
        return user;
    }

    String allocateUsername(String name, String dingUserId) {
        String preferred = name == null ? "" : name.trim();
        if (preferred.length() > 50) {
            preferred = preferred.substring(0, 50);
        }
        if (!preferred.isEmpty() && userRepository.findByUsername(preferred).isEmpty()) {
            return preferred;
        }
        String base = sanitize(dingUserId);
        if (base.isEmpty()) {
            base = "dtuser";
        }
        String candidate = base.length() > 50 ? base.substring(0, 50) : base;
        int i = 1;
        while (userRepository.findByUsername(candidate).isPresent()) {
            String suffix = String.valueOf(i++);
            int keep = Math.max(1, 50 - suffix.length());
            candidate = (base.length() > keep ? base.substring(0, keep) : base) + suffix;
        }
        return candidate;
    }

    String allocateEmail(OrgContact contact) {
        if (contact != null) {
            String fromDing = contact.getEmail();
            if (fromDing != null && !fromDing.isBlank() && userRepository.findByEmail(fromDing.trim()).isEmpty()) {
                return fromDing.trim();
            }
            return allocatePlaceholderEmail(contact.getDingUserId());
        }
        return allocatePlaceholderEmail(null);
    }

    String allocatePlaceholderEmail(String dingUserId) {
        String userid = sanitize(dingUserId);
        if (userid.isEmpty()) {
            userid = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        String placeholder = "dt-" + userid + PLACEHOLDER_EMAIL_DOMAIN;
        if (placeholder.length() > 100) {
            placeholder = "dt-" + userid.substring(0, Math.min(userid.length(), 60)) + PLACEHOLDER_EMAIL_DOMAIN;
        }
        String candidate = placeholder;
        int i = 1;
        while (userRepository.findByEmail(candidate).isPresent()) {
            candidate = "dt-" + userid + i + PLACEHOLDER_EMAIL_DOMAIN;
            i++;
        }
        return candidate;
    }

    private List<DingTalkContactCandidate> toCandidates(List<OrgContact> contacts) {
        List<DingTalkContactCandidate> list = new ArrayList<>();
        for (OrgContact contact : contacts) {
            // Unbound leftover users.dingtalk_userid must not look "already registered",
            // otherwise the UI disables retry for names such as 李彬.
            boolean registered = contact.getLocalUserId() != null && !contact.getLocalUserId().isBlank();
            String path = OrgAffiliation.canonicalPath(contact.getDeptPath());
            String deptName = contact.getDeptName();
            if (OrgAffiliation.isShortCompanyLabel(deptName)) {
                deptName = OrgAffiliation.leafName(path);
            }
            list.add(DingTalkContactCandidate.builder()
                    .dingtalkUserid(contact.getDingUserId())
                    .name(contact.getName())
                    .jobTitle(contact.getJobTitle())
                    .deptName(deptName)
                    .deptPath(path)
                    .alreadyRegistered(registered)
                    .build());
        }
        return list;
    }

    private void ensureImageTableQuietly(User user) {
        if (imageTableService == null || user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return;
        }
        try {
            imageTableService.ensureUserImageTable(user.getUsername());
        } catch (Exception e) {
            log.warn("自动开户后创建用户图片表失败（账号已可用）: username={}, err={}",
                    user.getUsername(), e.getMessage());
        }
    }

    /**
     * 袜业等分部与集团同一租户，本地账号一律归属宝娜斯集团。
     */
    private String resolveCompany(String company) {
        if (company != null && !company.isBlank() && !DEFAULT_COMPANY.equals(company.trim())) {
            log.debug("注册公司入参「{}」已归一为{}", company.trim(), DEFAULT_COMPANY);
        } else if (properties.getCompany() != null && !properties.getCompany().isBlank()
                && !DEFAULT_COMPANY.equals(properties.getCompany().trim())) {
            log.debug("钉钉配置公司「{}」注册时仍写入{}", properties.getCompany().trim(), DEFAULT_COMPANY);
        }
        return DEFAULT_COMPANY;
    }

    /**
     * 打样推送开户结果。仅 {@link Status#CREATED} / {@link Status#ALREADY_EXISTS} 表示本地账号已就绪，
     * 其余状态调用方不得发送工作通知。
     */
    public static final class EnsureAccountResult {
        public enum Status {
            CREATED,
            ALREADY_EXISTS,
            SKIPPED_BLANK,
            SKIPPED_AMBIGUOUS,
            SKIPPED_NO_MATCH,
            FAILED
        }

        private final Status status;
        private final User user;
        private final String message;

        private EnsureAccountResult(Status status, User user, String message) {
            this.status = status;
            this.user = user;
            this.message = message;
        }

        public static EnsureAccountResult created(User user) {
            return new EnsureAccountResult(Status.CREATED, user, "created");
        }

        public static EnsureAccountResult alreadyExists() {
            return alreadyExists("already registered");
        }

        public static EnsureAccountResult alreadyExists(String message) {
            return alreadyExists(null, message);
        }

        public static EnsureAccountResult alreadyExists(User user, String message) {
            return new EnsureAccountResult(Status.ALREADY_EXISTS, user, message);
        }

        public static EnsureAccountResult skippedBlank() {
            return new EnsureAccountResult(Status.SKIPPED_BLANK, null, "姓名为空");
        }

        public static EnsureAccountResult skippedAmbiguous(String message) {
            return new EnsureAccountResult(Status.SKIPPED_AMBIGUOUS, null, message);
        }

        public static EnsureAccountResult skippedNoMatch(String message) {
            return new EnsureAccountResult(Status.SKIPPED_NO_MATCH, null, message);
        }

        public static EnsureAccountResult failed(String message) {
            return new EnsureAccountResult(Status.FAILED, null, message);
        }

        public Status getStatus() {
            return status;
        }

        public User getUser() {
            return user;
        }

        public String getMessage() {
            return message;
        }

        public boolean isCreated() {
            return status == Status.CREATED;
        }

        /** 本地 {@code users} 账号已存在或刚按通讯录创建成功。 */
        public boolean hasLocalAccount() {
            return status == Status.CREATED || status == Status.ALREADY_EXISTS;
        }
    }

    static final class ProvisionResult {
        final User user;
        final boolean reusedByName;

        private ProvisionResult(User user, boolean reusedByName) {
            this.user = user;
            this.reusedByName = reusedByName;
        }

        static ProvisionResult created(User user) {
            return new ProvisionResult(user, false);
        }

        static ProvisionResult reusedDing(User user) {
            return new ProvisionResult(user, false);
        }

        static ProvisionResult reusedByName(User user) {
            return new ProvisionResult(user, true);
        }
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return cleaned.replaceAll("_+", "_");
    }
}
