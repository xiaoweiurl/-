package com.imagemanager.org;

import java.util.List;
import java.util.Optional;

/**
 * 本地钉钉通讯录查询/绑定。
 */
public interface OrgDirectory {

    int countActiveContacts(String company);

    List<OrgContact> findActiveByName(String company, String name);

    Optional<OrgContact> findByDingUserId(String company, String dingUserId);

    Optional<String> findLocalDeptId(String company, Long dingDeptId);

    List<OrgDepartmentView> listActiveDepartments(String company);

    List<OrgContact> searchContacts(String company, String keyword, int limit);

    void bindLocalUser(String orgUserId, String localUserId);

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class OrgDepartmentView {
        private String id;
        private Long dingDeptId;
        private Long parentDingDeptId;
        private String name;
        private String path;
        private int userCount;
    }
}
