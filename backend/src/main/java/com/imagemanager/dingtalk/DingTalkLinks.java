package com.imagemanager.dingtalk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 钉钉工作通知跳转链接。
 * <p>
 * 已配置 corpId 且 {@code dingtalk.work-notice-protocol-links=true}（默认）时包一层
 * {@code dingtalk://openapp}，让 H5 落在微应用容器内，避免裸 HTTP 无法绑定域名
 * （requestAuthCode error 3：「对应企业没有…域名微应用」）。
 * <p>
 * 可用 {@code DINGTALK_WORK_NOTICE_PROTOCOL_LINKS=false} 强制裸 HTTP(S)。
 * 包装时必须把 FRONTEND_URL 主机（不要带 {@code http://}）写入企业内部应用
 * 「H5 可信域名 / 安全域名」。
 */
public final class DingTalkLinks {

    private DingTalkLinks() {
    }

    /**
     * 工作通知按钮 URL。
     * <ul>
     *   <li>{@code protocolLinks=false} 或缺 corpId：原样返回 HTTP(S) 表单地址</li>
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

    /**
     * 日志用 URL 前缀：去掉 query，避免把 corpid / agentId 打进日志。
     * HTTP(S) 直链无 query 时即完整表单地址。
     */
    public static String logSafePrefix(String clickUrl) {
        if (clickUrl == null || clickUrl.isBlank()) {
            return "";
        }
        String target = clickUrl.trim();
        int query = target.indexOf('?');
        return query >= 0 ? target.substring(0, query) : target;
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
