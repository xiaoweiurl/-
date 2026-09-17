package com.imagemanager.dingtalk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 企业内部应用免登：{@code topapi/v2/user/getuserinfo} 返回的身份。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DingTalkAuthUser {
    private String userid;
    private String unionid;
    private String name;
    private String deviceId;
}
