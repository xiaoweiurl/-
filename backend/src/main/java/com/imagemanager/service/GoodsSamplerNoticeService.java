package com.imagemanager.service;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dingtalk.DingTalkClient;
import com.imagemanager.dingtalk.DingTalkUseridResolver;
import com.imagemanager.dingtalk.DingTalkWorkNotice;
import com.imagemanager.org.OrgNameMatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 商品库打样员工作通知：仅在 sampler 新设或变更为他人时投递。
 * AgentId 未配置时功能关闭（记日志、不抛异常、不影响商品保存）。
 */
@Slf4j
@Service
public class GoodsSamplerNoticeService {

    private final DingTalkClient dingTalkClient;
    private final DingTalkUseridResolver useridResolver;
    private final DingTalkProperties properties;
    private final String frontendUrl;

    public GoodsSamplerNoticeService(DingTalkClient dingTalkClient,
                                     DingTalkUseridResolver useridResolver,
                                     DingTalkProperties properties,
                                     @Value("${app.frontend.url:http://localhost:5000}") String frontendUrl) {
        this.dingTalkClient = dingTalkClient;
        this.useridResolver = useridResolver;
        this.properties = properties;
        this.frontendUrl = frontendUrl;
    }

    /** 异步入口：保存成功后调用，不阻塞商品库写路径。 */
    @Async("taskExecutor")
    public void notifySamplerAssignedAsync(String previousSampler, String newSampler,
                                           GoodsSamplerNotice goods) {
        notifyIfSamplerChanged(previousSampler, newSampler, goods);
    }

    /**
     * 同步投递（单测直接调用）。任何失败只记日志，不向上抛。
     */
    public SamplerNoticeResult notifyIfSamplerChanged(String previousSampler, String newSampler,
                                                      GoodsSamplerNotice goods) {
        try {
            return doNotify(previousSampler, newSampler, goods);
        } catch (Exception e) {
            log.warn("钉钉工作通知发送失败（不影响商品保存）: goodsId={}, sampler={}, err={}",
                    goods == null ? null : goods.getGoodsId(), newSampler, e.getMessage());
            return SamplerNoticeResult.failed(null, e.getMessage());
        }
    }

    private SamplerNoticeResult doNotify(String previousSampler, String newSampler,
                                         GoodsSamplerNotice goods) {
        String next = newSampler == null ? "" : newSampler.trim();
        if (OrgNameMatcher.normalize(next).isEmpty()) {
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_BLANK, "打样员为空");
        }
        if (OrgNameMatcher.namesEqual(previousSampler, next)) {
            log.debug("打样员未变更，跳过工作通知: goodsId={}, sampler={}",
                    goods == null ? null : goods.getGoodsId(), next);
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_UNCHANGED, "打样员未变更");
        }

        if (!properties.isWorkNoticeEnabled()) {
            if (!properties.isConfigured()) {
                log.info("钉钉工作通知未启用：缺少 DINGTALK_APP_KEY / DINGTALK_APP_SECRET，跳过发送");
            } else if (properties.getAgentId() == null || properties.getAgentId().isBlank()) {
                log.info("钉钉工作通知未启用：未配置 DINGTALK_AGENT_ID，跳过发送");
            } else {
                log.info("钉钉工作通知未启用：DINGTALK_AGENT_ID={} 不是有效数字，跳过发送",
                        properties.getAgentId());
            }
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_DISABLED,
                    "工作通知未启用（缺少 AgentId 或 AppKey/Secret）");
        }

        DingTalkUseridResolver.ResolveResult resolved = useridResolver.resolveByName(next);
        if (resolved.getStatus() == DingTalkUseridResolver.ResolveResult.Status.AMBIGUOUS) {
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_AMBIGUOUS,
                    resolved.getSource());
        }
        if (!resolved.isFound()) {
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_NO_USERID,
                    "未找到打样员「" + next + "」的钉钉 userid");
        }

        DingTalkWorkNotice notice = buildNotice(next, goods);
        long taskId = dingTalkClient.sendWorkNotice(resolved.getUserid(), notice);
        log.info("已向打样员发送钉钉工作通知: goodsId={}, sampler={}, userid={}, source={}, taskId={}",
                goods == null ? null : goods.getGoodsId(), next, resolved.getUserid(),
                resolved.getSource(), taskId);
        return SamplerNoticeResult.sent(resolved.getUserid(), taskId);
    }

    DingTalkWorkNotice buildNotice(String samplerName, GoodsSamplerNotice goods) {
        String display = goods == null ? "未命名商品" : goods.displayName();
        String goodsNo = goods == null || goods.getGoodsNo() == null || goods.getGoodsNo().isBlank()
                ? "未填写" : goods.getGoodsNo().trim();
        String productName = goods == null || goods.getProductName() == null || goods.getProductName().isBlank()
                ? "未填写" : goods.getProductName().trim();
        String initiator = goods == null || goods.getInitiator() == null || goods.getInitiator().isBlank()
                ? "未填写" : goods.getInitiator().trim();
        String title = "您被指定为打样员";
        String formHttp = goods == null ? "" : formUrl(goods.getGoodsId());
        String markdown = "### 打样任务\n\n"
                + "您被指定为商品 **" + display + "** 的打样员。\n\n"
                + "- 货号：" + goodsNo + "\n"
                + "- 品名：" + productName + "\n"
                + "- 发起人：" + initiator + "\n"
                + "- 打样员：" + samplerName + "\n\n"
                + "请及时填写打样信息。";
        if (!formHttp.isBlank()) {
            markdown += "\n\n[填写打样表单](" + formHttp + ")";
        }
        if (formHttp.isBlank()) {
            return DingTalkWorkNotice.text(title, markdown.replace("**", "").replace("### ", ""));
        }
        // 使用裸 HTTP 链接（非 dingtalk:// 包装）。#19 的 openapp/page/link 协议在应用未发布时
        // 会提示「非钉钉页面」；H5 可信域名发布后，钉钉内置浏览器直接打开 /sampler/{id} 并走免登。
        return DingTalkWorkNotice.actionCard(title, markdown, "填写打样表单", formHttp);
    }

    /**
     * 打样员专用表单（手机优先）。钉钉内打开后走 H5 免登，无需中台密码。
     * 路径：{@code {FRONTEND_URL}/sampler/{goodsId}}
     */
    String formUrl(long goodsId) {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        if (base.isEmpty()) {
            return "";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/sampler/" + goodsId;
    }
}
