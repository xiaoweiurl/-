package com.imagemanager.dingtalk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 钉钉工作通知跳转链接。
 * <p>
 * 默认使用裸 HTTP(S) 表单地址：官方 action_card {@code single_url} 支持
 * {@code https://open.dingtalk.com} 这类直链，手机钉钉可直接打开 H5，
 * 无需 {@code openapp}/{@code page/link} 白名单体操。
 * <p>
 * 仅当同时配置了 corpId 且打开 {@code dingtalk.work-notice-protocol-links}
 * 时才包一层 {@code dingtalk://}。那时必须把 FRONTEND_URL 主机写入企业内部应用
 * 「H5 可信域名 / 安全域名」，否则会出现「后续页面非钉钉提供」。
 */
public final class DingTalkLinks {

    private DingTalkLinks() {
    }

    /**
     * 工作通知按钮 URL。
     * <ul>
     *   <li>默认（{@code protocolLinks=false} 或缺 corpId）：原样返回 HTTP(S) 表单地址</li>
     *   <li>{@code protocolLinks=true} + corpId + agentId：工作台 {@code openapp} 并
     *       {@code redirect_url} 到表单</li>
     *   <li>{@code protocolLinks=true} + corpId、无 agentId：{@code page/link} 侧边栏打开</li>
     * </ul>
     */
    public static String workNoticeUrl(String httpUrl, String corpId, Long agentId,
                                       boolean protocolLinks) {
        if (httpUrl == null || httpUrl.isBlank()) {
            return "";
        }
        String target = httpUrl.trim();
        if (!protocolLinks || corpId == null || corpId.isBlank()) {
            return target;
        }
        String encodedTarget = encode(target);
        if (agentId != null) {
            return "dingtalk://dingtalkclient/action/openapp"
                    + "?corpid=" + encode(corpId.trim())
                    + "&container_type=work_platform"
                    + "&app_id=0_" + agentId
                    + "&redirect_type=jump"
                    + "&redirect_url=" + encodedTarget;
        }
        return "dingtalk://dingtalkclient/page/link?url=" + encodedTarget + "&pc_slide=true";
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
