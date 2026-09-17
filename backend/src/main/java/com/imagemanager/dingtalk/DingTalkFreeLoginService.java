package com.imagemanager.dingtalk;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.entity.User;
import com.imagemanager.org.OrgContact;
import com.imagemanager.org.OrgDirectory;
import com.imagemanager.org.OrgNameMatcher;
import com.imagemanager.org.OrgRegistrationService;
import com.imagemanager.repository.UserRepository;
import com.imagemanager.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 钉钉 H5 免登：authCode → userid → 本地会话。
 * <ol>
 *   <li>{@code users.dingtalk_userid} 命中 → 完整中台会话</li>
 *   <li>org_users 已绑定 local_user_id → 完整会话</li>
 *   <li>通讯录姓名与已注册用户唯一匹配 → 绑定 userid 后完整会话</li>
 *   <li>通讯录有人但无 users 行 → 仅该商品的 sampler 作用域会话（无需密码）</li>
 * </ol>
 * 不提升角色：admin/org/ERP 仍走原 RBAC。
 */
@Slf4j
@Service
public class DingTalkFreeLoginService {

    private final DingTalkClient dingTalkClient;
    private final UserRepository userRepository;
    private final OrgDirectory orgDirectory;
    private final AuthService authService;
    private final DingTalkProperties properties;

    public DingTalkFreeLoginService(DingTalkClient dingTalkClient,
                                    UserRepository userRepository,
                                    OrgDirectory orgDirectory,
                                    AuthService authService,
                                    DingTalkProperties properties) {
        this.dingTalkClient = dingTalkClient;
        this.userRepository = userRepository;
        this.orgDirectory = orgDirectory;
        this.authService = authService;
        this.properties = properties;
    }

    public LoginResponse login(String authCode, Long goodsId) {
        if (authCode == null || authCode.isBlank()) {
            throw new IllegalArgumentException("缺少钉钉授权码");
        }
        DingTalkAuthUser dingUser = dingTalkClient.getUserByAuthCode(authCode.trim());
        String userid = dingUser.getUserid();
        if (userid == null || userid.isBlank()) {
            throw DingTalkFreeLoginException.unmatched();
        }
        log.info("钉钉免登换取 userid 成功: userid={}*** name={}",
                userid.substring(0, Math.min(4, userid.length())), dingUser.getName());

        Optional<User> bound = userRepository.findByDingtalkUserid(userid);
        if (bound.isPresent()) {
            return fullSession(bound.get(), "users.dingtalk_userid");
        }

        String company = resolveCompany();
        Optional<OrgContact> contactOpt = orgDirectory.findByDingUserId(company, userid);
        if (contactOpt.isPresent()) {
            OrgContact contact = contactOpt.get();
            if (contact.getLocalUserId() != null && !contact.getLocalUserId().isBlank()) {
                Optional<User> local = userRepository.findById(contact.getLocalUserId());
                if (local.isPresent()) {
                    bindDingTalkIfAbsent(local.get(), dingUser);
                    return fullSession(local.get(), "org_users.local_user_id");
                }
            }
            Optional<User> unique = uniqueRegisteredByName(contact.getName(), userid);
            if (unique.isPresent()) {
                User user = unique.get();
                bindDingTalkIfAbsent(user, dingUser);
                if (contact.getId() != null) {
                    orgDirectory.bindLocalUser(contact.getId(), user.getId());
                }
                return fullSession(user, "org_users+unique_name");
            }
            if (goodsId == null || goodsId <= 0) {
                throw DingTalkFreeLoginException.goodsRequired();
            }
            String display = OrgNameMatcher.firstNonBlank(contact.getName(), dingUser.getName(), userid);
            log.info("钉钉免登：通讯录成员未注册，签发 sampler 作用域会话: userid={}*** goodsId={}",
                    userid.substring(0, Math.min(4, userid.length())), goodsId);
            return authService.issueSamplerSession(userid, display, company, goodsId);
        }

        Optional<User> byDingName = uniqueRegisteredByName(dingUser.getName(), userid);
        if (byDingName.isPresent()) {
            User user = byDingName.get();
            bindDingTalkIfAbsent(user, dingUser);
            return fullSession(user, "unique_name");
        }

        throw DingTalkFreeLoginException.unmatched();
    }

    private LoginResponse fullSession(User user, String source) {
        log.info("钉钉免登匹配已注册用户: userId={}, username={}, source={}",
                user.getId(), user.getUsername(), source);
        return authService.issueSession(user, false, true);
    }

    private void bindDingTalkIfAbsent(User user, DingTalkAuthUser dingUser) {
        boolean dirty = false;
        if (user.getDingtalkUserid() == null || user.getDingtalkUserid().isBlank()) {
            user.setDingtalkUserid(dingUser.getUserid());
            dirty = true;
        }
        if ((user.getDingtalkUnionid() == null || user.getDingtalkUnionid().isBlank())
                && dingUser.getUnionid() != null && !dingUser.getUnionid().isBlank()) {
            user.setDingtalkUnionid(dingUser.getUnionid());
            dirty = true;
        }
        if (dirty) {
            userRepository.save(user);
        }
    }

    /**
     * 已注册用户按姓名唯一匹配，且未绑定其他钉钉 userid。
     */
    Optional<User> uniqueRegisteredByName(String name, String dingUserId) {
        if (OrgNameMatcher.isBlank(name)) {
            return Optional.empty();
        }
        Map<String, User> byId = new LinkedHashMap<>();
        for (String candidate : nameVariants(name)) {
            userRepository.findByUsername(candidate).ifPresent(u -> byId.put(u.getId(), u));
            List<User> nick = userRepository.findByNickname(candidate);
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
            if (user == null) {
                continue;
            }
            if (!OrgNameMatcher.namesEqual(name, user.getUsername())
                    && !OrgNameMatcher.namesEqual(name, user.getNickname())) {
                continue;
            }
            String existing = user.getDingtalkUserid();
            if (existing != null && !existing.isBlank() && !existing.equals(dingUserId)) {
                continue;
            }
            matched.add(user);
        }
        if (matched.size() == 1) {
            return Optional.of(matched.get(0));
        }
        return Optional.empty();
    }

    private static List<String> nameVariants(String name) {
        List<String> variants = new ArrayList<>();
        String trimmed = name.trim();
        variants.add(trimmed);
        String normalized = OrgNameMatcher.normalize(name);
        if (!normalized.isEmpty() && !normalized.equals(trimmed)) {
            variants.add(normalized);
        }
        return variants;
    }

    private String resolveCompany() {
        String company = properties.getCompany();
        if (company == null || company.isBlank()) {
            return OrgRegistrationService.DEFAULT_COMPANY;
        }
        return company.trim();
    }
}
