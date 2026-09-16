package com.imagemanager.org;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dto.DingTalkContactCandidate;
import com.imagemanager.dto.RegisterRequest;
import com.imagemanager.entity.User;
import com.imagemanager.exception.RegisterMatchException;
import com.imagemanager.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 按钉钉通讯录姓名注册本地账号。
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

    public OrgRegistrationService(OrgDirectory orgDirectory,
                                  UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  DingTalkProperties properties,
                                  PlatformTransactionManager transactionManager) {
        this.orgDirectory = orgDirectory;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.txTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    }

    public User register(RegisterRequest request) {
        String company = resolveCompany(request == null ? null : request.getCompany());
        String name = OrgNameMatcher.requireName(request == null ? null
                : OrgNameMatcher.firstNonBlank(request.getName(), request.getUsername()));
        String dingUserId = request == null ? null : request.getDingtalkUserid();

        if (orgDirectory.countActiveContacts(company) == 0) {
            throw RegisterMatchException.notSynced();
        }

        OrgContact contact;
        if (dingUserId != null && !dingUserId.isBlank()) {
            contact = orgDirectory.findByDingUserId(company, dingUserId)
                    .orElseThrow(RegisterMatchException::none);
            if (!OrgNameMatcher.namesEqual(name, contact.getName())) {
                throw new RegisterMatchException(400, "所选成员与姓名不匹配");
            }
        } else {
            List<OrgContact> matches = orgDirectory.findActiveByName(company, name);
            if (matches.isEmpty()) {
                throw RegisterMatchException.none();
            }
            if (matches.size() > 1) {
                throw RegisterMatchException.multiple(toCandidates(matches));
            }
            contact = matches.get(0);
        }

        if (contact.getLocalUserId() != null && !contact.getLocalUserId().isBlank()) {
            throw RegisterMatchException.alreadyRegistered();
        }
        if (userRepository.findByDingtalkUserid(contact.getDingUserId()).isPresent()) {
            throw RegisterMatchException.alreadyRegistered();
        }

        User created = txTemplate.execute(status -> createUser(company, name, contact));
        if (created == null) {
            throw new IllegalStateException("注册事务未提交");
        }
        log.info("钉钉姓名注册成功: name={}, dingUserId={}, username={}", name, contact.getDingUserId(), created.getUsername());
        return created;
    }

    public List<DingTalkContactCandidate> searchContacts(String company, String keyword) {
        return toCandidates(orgDirectory.searchContacts(resolveCompany(company), keyword, 50));
    }

    private User createUser(String company, String displayName, OrgContact contact) {
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
        String fromDing = contact.getEmail();
        if (fromDing != null && !fromDing.isBlank() && userRepository.findByEmail(fromDing.trim()).isEmpty()) {
            return fromDing.trim();
        }
        String userid = sanitize(contact.getDingUserId());
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
            boolean registered = (contact.getLocalUserId() != null && !contact.getLocalUserId().isBlank())
                    || userRepository.findByDingtalkUserid(contact.getDingUserId()).isPresent();
            list.add(DingTalkContactCandidate.builder()
                    .dingtalkUserid(contact.getDingUserId())
                    .name(contact.getName())
                    .jobTitle(contact.getJobTitle())
                    .deptName(contact.getDeptName())
                    .deptPath(contact.getDeptPath())
                    .alreadyRegistered(registered)
                    .build());
        }
        return list;
    }

    private String resolveCompany(String company) {
        String value = company == null || company.isBlank() ? properties.getCompany() : company.trim();
        if (value == null || value.isBlank()) {
            value = DEFAULT_COMPANY;
        }
        if (!DEFAULT_COMPANY.equals(value)) {
            throw new IllegalArgumentException("公司只能选择宝娜斯集团");
        }
        return value;
    }

    private static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        return cleaned.replaceAll("_+", "_");
    }
}
