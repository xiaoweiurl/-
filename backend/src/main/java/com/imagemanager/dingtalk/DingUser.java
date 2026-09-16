package com.imagemanager.dingtalk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 钉钉通讯录用户（topapi/v2/user/list）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DingUser {
    private String userid;
    private String unionid;
    private String name;
    private String title;
    private String mobile;
    private String email;
    private String avatar;
    @Builder.Default
    private List<Long> deptIdList = new ArrayList<>();
    @Builder.Default
    private boolean active = true;
}
