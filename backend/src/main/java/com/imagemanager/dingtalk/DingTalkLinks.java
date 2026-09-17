package com.imagemanager.dingtalk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 钉钉工作通知跳转链接。
 * <p>
 * 裸 HTTP URL 在 PC 钉钉里常被工作台拦截成<strong>应用首页</strong>
 * （本项目里就是商品库列表 {@code /goods-library}），丢掉路径上的商品 id。
 * 按官方「消息链接说明」包一层协议，强制打开<strong>指定</strong> H5 地址。
 */
public final class DingTalkLinks {

    private DingTalkLinks() {
    }

    /**
     * 工作通知按钮 URL。
     * <ul>
     *   <li>已配置 corpId + agentId：工作台打开微应用并 {@code redirect_url} 到表单</li>
     *   <li>否则：侧边栏 / 内置浏览器打开该 HTTP 地址（{@code page/link}）</li>
     * </ul>
     */
    public static String workNoticeUrl(String httpUrl, String corpId, Long agentId) {
        if (httpUrl == null || httpUrl.isBlank()) {
            return "";
        }
        String target = httpUrl.trim();
        String encodedTarget = encode(target);
        if (corpId != null && !corpId.isBlank() && agentId != null) {
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
