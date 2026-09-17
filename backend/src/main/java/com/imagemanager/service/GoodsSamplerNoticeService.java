package com.imagemanager.service;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dingtalk.DingTalkClient;
import com.imagemanager.dingtalk.DingTalkLinks;
import com.imagemanager.dingtalk.DingTalkSamplerTicketService;
import com.imagemanager.dingtalk.DingTalkUseridResolver;
import com.imagemanager.dingtalk.DingTalkWorkNotice;
import com.imagemanager.org.OrgNameMatcher;
import com.imagemanager.org.OrgRegistrationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 商品库打样员工作通知：仅在 sampler 新设或变更为他人时投递。
 * AgentId 未配置时功能关闭（记日志、不抛异常、不影响商品保存）。
 * <p>固定顺序：按钉钉已同步通讯录姓名开户（{@link OrgRegistrationService#ensureAccountByName}）
 * → 再解析 userid / 签发 ticket / 发送工作通知。开户未成功（同名、无匹配、异常）则取消推送。
 */
@Slf4j
@Service
public class GoodsSamplerNoticeService {

    private final DingTalkClient dingTalkClient;
    private final DingTalkUseridResolver useridResolver;
    private final OrgRegistrationService orgRegistrationService;
    private final DingTalkProperties properties;
    private final DingTalkSamplerTicketService ticketService;
    private final String frontendUrl;

    public GoodsSamplerNoticeService(DingTalkClient dingTalkClient,
                                     DingTalkUseridResolver useridResolver,
                                     @Nullable OrgRegistrationService orgRegistrationService,
                                     DingTalkProperties properties,
                                     DingTalkSamplerTicketService ticketService,
                                     @Value("${app.frontend.url:http://localhost:5000}") String frontendUrl) {
        this.dingTalkClient = dingTalkClient;
        this.useridResolver = useridResolver;
        this.orgRegistrationService = orgRegistrationService;
        this.properties = properties;
        this.ticketService = ticketService;
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

        // 推送前必须先用已同步的钉钉通讯录开户；未成功则不发通知。
        OrgRegistrationService.EnsureAccountResult ensured = ensureAccountFromOrg(next);
        SamplerNoticeResult blocked = skipIfAccountMissing(next, ensured);
        if (blocked != null) {
            return blocked;
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

        DingTalkWorkNotice notice = buildNotice(next, goods, resolved.getUserid());
        if (notice.hasLink()) {
            String clickUrl = notice.getSingleUrl();
            log.info("打样工作通知 single_url: goodsId={}, wrap={}, prefix={}",
                    goods == null ? null : goods.getGoodsId(),
                    properties.shouldWrapWorkNoticeProtocolLinks(),
                    DingTalkLinks.logSafePrefix(clickUrl));
        }
        long taskId = dingTalkClient.sendWorkNotice(resolved.getUserid(), notice);
        log.info("已向打样员发送钉钉工作通知: goodsId={}, sampler={}, userid={}, source={}, taskId={}",
                goods == null ? null : goods.getGoodsId(), next, resolved.getUserid(),
                resolved.getSource(), taskId);
        return SamplerNoticeResult.sent(resolved.getUserid(), taskId);
    }

    /**
     * 推送前按钉钉已同步通讯录开户。仅 CREATED / ALREADY_EXISTS 视为本地账号已就绪。
     */
    OrgRegistrationService.EnsureAccountResult ensureAccountFromOrg(String samplerName) {
        if (orgRegistrationService == null) {
            log.warn("打样推送取消：开户服务不可用 sampler={}", samplerName);
            return OrgRegistrationService.EnsureAccountResult.failed("开户服务不可用");
        }
        try {
            OrgRegistrationService.EnsureAccountResult result =
                    orgRegistrationService.ensureAccountByName(samplerName);
            if (result == null) {
                return OrgRegistrationService.EnsureAccountResult.failed("开户结果为空");
            }
            return result;
        } catch (Exception e) {
            log.warn("打样推送自动开户失败，取消工作通知: sampler={}, err={}",
                    samplerName, e.getMessage());
            return OrgRegistrationService.EnsureAccountResult.failed(e.getMessage());
        }
    }

    private static SamplerNoticeResult skipIfAccountMissing(
            String samplerName, OrgRegistrationService.EnsureAccountResult ensured) {
        if (ensured != null && ensured.hasLocalAccount()) {
            return null;
        }
        OrgRegistrationService.EnsureAccountResult.Status status =
                ensured == null ? OrgRegistrationService.EnsureAccountResult.Status.FAILED : ensured.getStatus();
        String detail = ensured == null ? "开户结果为空" : ensured.getMessage();
        if (status == OrgRegistrationService.EnsureAccountResult.Status.SKIPPED_AMBIGUOUS) {
            log.info("打样推送取消：通讯录同名多人无法唯一开户 sampler={}, detail={}", samplerName, detail);
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_AMBIGUOUS, detail);
        }
        if (status == OrgRegistrationService.EnsureAccountResult.Status.SKIPPED_NO_MATCH
                || status == OrgRegistrationService.EnsureAccountResult.Status.SKIPPED_BLANK) {
            log.info("打样推送取消：通讯录无唯一匹配，未创建账号 sampler={}, detail={}", samplerName, detail);
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_NO_USERID, detail);
        }
        log.warn("打样推送取消：自动开户未成功，不发送工作通知 sampler={}, status={}, detail={}",
                samplerName, status, detail);
        return SamplerNoticeResult.failed(null, detail == null ? "自动开户失败，取消工作通知" : detail);
    }

    DingTalkWorkNotice buildNotice(String samplerName, GoodsSamplerNotice goods, String dingUserId) {
        String display = goods == null ? "未命名商品" : goods.displayName();
        String goodsNo = goods == null || goods.getGoodsNo() == null || goods.getGoodsNo().isBlank()
                ? "未填写" : goods.getGoodsNo().trim();
        String productName = goods == null || goods.getProductName() == null || goods.getProductName().isBlank()
                ? "未填写" : goods.getProductName().trim();
        String initiator = goods == null || goods.getInitiator() == null || goods.getInitiator().isBlank()
                ? "未填写" : goods.getInitiator().trim();
        String title = "您被指定为打样员";
        String formHttp = goods == null ? "" : formUrl(goods.getGoodsId(), dingUserId);
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
        // 默认 wrap=true + corpId → dingtalk://openapp（H5 免登域名绑定）。
        // DINGTALK_WORK_NOTICE_PROTOCOL_LINKS=false 时保持裸 HTTP(S)。
        String clickUrl = DingTalkLinks.workNoticeUrl(
                formHttp, properties.getCorpId(), properties.resolveAgentId(),
                properties.shouldWrapWorkNoticeProtocolLinks());
        return DingTalkWorkNotice.actionCard(title, markdown, "填写打样表单", clickUrl);
    }

    /**
     * 打样员专用表单（手机优先）。带 HMAC ticket 时打开即可免登，不依赖 JSAPI 域名微应用。
     * 路径：{@code {FRONTEND_URL}/sampler/{goodsId}?ticket=...}
     */
    String formUrl(long goodsId) {
        return formUrl(goodsId, null);
    }

    String formUrl(long goodsId, String dingUserId) {
        String base = frontendUrl == null ? "" : frontendUrl.trim();
        if (base.isEmpty()) {
            return "";
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String url = base + "/sampler/" + goodsId;
        if (ticketService == null || dingUserId == null || dingUserId.isBlank()) {
            return url;
        }
        return ticketService.mint(dingUserId, goodsId)
                .map(ticket -> url + "?ticket=" + DingTalkLinks.encode(ticket))
                .orElse(url);
    }
}
