package com.imagemanager.dingtalk;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.entity.User;
import com.imagemanager.org.OrgContact;
import com.imagemanager.org.OrgDirectory;
import com.imagemanager.org.OrgNameMatcher;
import com.imagemanager.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按姓名解析钉钉 userid：优先 {@code org_users}，其次 {@code users.dingtalk_userid}。
 * 0 人 / 多人同名均视为无法投递，不抛异常。
 */
@Slf4j
@Component
public class DingTalkUseridResolver {

    private final OrgDirectory orgDirectory;
    private final UserRepository userRepository;
    private final DingTalkProperties properties;

    public DingTalkUseridResolver(OrgDirectory orgDirectory,
                                  UserRepository userRepository,
                                  DingTalkProperties properties) {
        this.orgDirectory = orgDirectory;
        this.userRepository = userRepository;
        this.properties = properties;
    }

    public ResolveResult resolveByName(String name) {
        if (OrgNameMatcher.isBlank(name) || OrgNameMatcher.normalize(name).isEmpty()) {
            return ResolveResult.blank();
        }

        String company = properties.getCompany() == null || properties.getCompany().isBlank()
                ? "宝娜斯集团" : properties.getCompany().trim();

        List<OrgContact> orgMatches = orgDirectory.findActiveByName(company, name);
        Set<String> orgUserids = uniqueUserids(orgMatches);
        if (orgUserids.size() == 1) {
            String userid = orgUserids.iterator().next();
            log.debug("钉钉 userid 命中 org_users: name={}, userid={}", name, userid);
            return ResolveResult.found(userid, "org_users");
        }
        if (orgUserids.size() > 1) {
            log.warn("钉钉 userid 解析跳过：org_users 中姓名「{}」对应 {} 个 userid，无法唯一投递",
                    name, orgUserids.size());
            return ResolveResult.ambiguous("org_users 同名 " + orgUserids.size() + " 人");
        }

        List<User> localMatches = matchLocalUsers(name);
        Set<String> localUserids = uniqueLocalUserids(localMatches);
        if (localUserids.size() == 1) {
            String userid = localUserids.iterator().next();
            log.debug("钉钉 userid 命中 users.dingtalk_userid: name={}, userid={}", name, userid);
            return ResolveResult.found(userid, "users.dingtalk_userid");
        }
        if (localUserids.size() > 1) {
            log.warn("钉钉 userid 解析跳过：users 中姓名「{}」对应 {} 个 dingtalk_userid，无法唯一投递",
                    name, localUserids.size());
            return ResolveResult.ambiguous("users 同名 " + localUserids.size() + " 人");
        }

        log.warn("钉钉 userid 解析跳过：未找到打样员「{}」的钉钉 userid（org_users / users.dingtalk_userid）",
                name);
        return ResolveResult.missing();
    }

    private List<User> matchLocalUsers(String name) {
        List<User> bound = userRepository.findByDingtalkUseridIsNotNull();
        if (bound == null || bound.isEmpty()) {
            return List.of();
        }
        List<User> matched = new ArrayList<>();
        for (User user : bound) {
            if (user == null || user.getDingtalkUserid() == null || user.getDingtalkUserid().isBlank()) {
                continue;
            }
            if (OrgNameMatcher.namesEqual(name, user.getUsername())
                    || OrgNameMatcher.namesEqual(name, user.getNickname())) {
                matched.add(user);
            }
        }
        return matched;
    }

    private static Set<String> uniqueUserids(List<OrgContact> contacts) {
        Set<String> ids = new LinkedHashSet<>();
        if (contacts == null) {
            return ids;
        }
        for (OrgContact contact : contacts) {
            if (contact == null) {
                continue;
            }
            String id = contact.getDingUserId();
            if (id != null && !id.isBlank()) {
                ids.add(id.trim());
            }
        }
        return ids;
    }

    private static Set<String> uniqueLocalUserids(List<User> users) {
        Set<String> ids = new LinkedHashSet<>();
        for (User user : users) {
            ids.add(user.getDingtalkUserid().trim());
        }
        return ids;
    }

    public static final class ResolveResult {
        public enum Status { FOUND, MISSING, AMBIGUOUS, BLANK }

        private final Status status;
        private final String userid;
        private final String source;

        private ResolveResult(Status status, String userid, String source) {
            this.status = status;
            this.userid = userid;
            this.source = source;
        }

        public static ResolveResult found(String userid, String source) {
            return new ResolveResult(Status.FOUND, userid, source);
        }

        public static ResolveResult missing() {
            return new ResolveResult(Status.MISSING, null, null);
        }

        public static ResolveResult ambiguous(String source) {
            return new ResolveResult(Status.AMBIGUOUS, null, source);
        }

        public static ResolveResult blank() {
            return new ResolveResult(Status.BLANK, null, null);
        }

        public Status getStatus() {
            return status;
        }

        public String getUserid() {
            return userid;
        }

        public String getSource() {
            return source;
        }

        public boolean isFound() {
            return status == Status.FOUND && userid != null && !userid.isBlank();
        }
    }
}
