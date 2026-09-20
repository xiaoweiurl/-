package com.imagemanager.service;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dingtalk.DingTalkClient;
import com.imagemanager.dingtalk.DingTalkException;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 商品库打样员工作通知：仅在 sampler 新设或变更为他人时投递指派卡；
 * 打样表单补全货号/品名后补发摘要卡（钉钉 ActionCard 发出后不能改正文）。
 * AgentId 未配置时功能关闭（记日志、不抛异常、不影响商品保存）。
 * <p>顺序：按钉钉已同步通讯录姓名开户（{@link OrgRegistrationService#ensureAccountByName}）
 * → 再解析 userid / 签发 ticket / 发送工作通知。开户未成功时，若 {@code users.dingtalk_userid}
 * 仍能唯一解析则兜底推送；同名多人不投递。
 */
@Slf4j
@Service
public class GoodsSamplerNoticeService {

    private static final int PERSIST_RETRIES = 3;

    private final DingTalkClient dingTalkClient;
    private final DingTalkUseridResolver useridResolver;
    private final OrgRegistrationService orgRegistrationService;
    private final DingTalkProperties properties;
    private final DingTalkSamplerTicketService ticketService;
    private final GoodsSamplerNoticeStore noticeStore;
    private final String frontendUrl;

    public GoodsSamplerNoticeService(DingTalkClient dingTalkClient,
                                     DingTalkUseridResolver useridResolver,
                                     @Nullable OrgRegistrationService orgRegistrationService,
                                     DingTalkProperties properties,
                                     DingTalkSamplerTicketService ticketService,
                                     GoodsSamplerNoticeStore noticeStore,
                                     @Value("${app.frontend.url:http://localhost:5000}") String frontendUrl) {
        this.dingTalkClient = dingTalkClient;
        this.useridResolver = useridResolver;
        this.orgRegistrationService = orgRegistrationService;
        this.properties = properties;
        this.ticketService = ticketService;
        this.noticeStore = noticeStore;
        this.frontendUrl = frontendUrl;
    }

    /** 异步入口：保存成功后调用，不阻塞商品库写路径。 */
    @Async("taskExecutor")
    public void notifySamplerAssignedAsync(String previousSampler, String newSampler,
                                           GoodsSamplerNotice goods) {
        notifyIfSamplerChanged(previousSampler, newSampler, goods);
    }

    /** 异步入口：打样表单保存成功后，尝试按已发出的指派卡补发回填摘要。 */
    @Async("taskExecutor")
    public void notifySamplerFormFilledAsync(GoodsSamplerNotice goods, String samplerName) {
        notifyFormFilled(goods, samplerName);
    }

    /**
     * 同步投递（单测直接调用）。任何失败只记日志，不向上抛。
     */
    public SamplerNoticeResult notifyIfSamplerChanged(String previousSampler, String newSampler,
                                                      GoodsSamplerNotice goods) {
        return notifyIfSamplerChanged(previousSampler, newSampler, goods, false);
    }

    /** 商品页手动补发：忽略「打样员未变更」，也不通知旧打样员。 */
    public SamplerNoticeResult resendAssignment(GoodsSamplerNotice goods, String samplerName) {
        return notifyIfSamplerChanged(null, samplerName, goods, true);
    }

    public SamplerNoticeResult notifyIfSamplerChanged(String previousSampler, String newSampler,
                                                      GoodsSamplerNotice goods, boolean force) {
        try {
            return doNotify(previousSampler, newSampler, goods, force);
        } catch (Exception e) {
            log.warn("钉钉工作通知发送失败（不影响商品保存）: goodsId={}, sampler={}, err={}",
                    goods == null ? null : goods.getGoodsId(), newSampler, e.getMessage());
            SamplerNoticeResult failed = SamplerNoticeResult.failed(null, e.getMessage());
            persistOutcome(failed, goods, newSampler, SamplerNoticeResult.KIND_ASSIGNMENT);
            return failed;
        }
    }

    /**
     * 表单保存后补发。钉钉不能改已发出 ActionCard 的货号/品名，故发一封「打样信息已更新」。
     */
    public SamplerNoticeResult notifyFormFilled(GoodsSamplerNotice goods, String samplerName) {
        try {
            return doNotifyFormFilled(goods, samplerName);
        } catch (Exception e) {
            log.warn("钉钉打样回填通知发送失败（不影响商品保存）: goodsId={}, err={}",
                    goods == null ? null : goods.getGoodsId(), e.getMessage());
            SamplerNoticeResult failed = SamplerNoticeResult.failed(null, e.getMessage());
            persistOutcome(failed, goods, samplerName, SamplerNoticeResult.KIND_FOLLOWUP);
            return failed;
        }
    }

    /** 商品详情用的投递状态，无记录时返回 null。 */
    public Map<String, Object> statusView(long goodsId) {
        if (noticeStore == null) {
            return null;
        }
        return noticeStore.findByGoodsId(goodsId).map(GoodsSamplerNoticeService::toStatusView).orElse(null);
    }

    private SamplerNoticeResult doNotify(String previousSampler, String newSampler,
                                         GoodsSamplerNotice goods, boolean force) {
        String next = newSampler == null ? "" : newSampler.trim();
        if (OrgNameMatcher.normalize(next).isEmpty()) {
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_BLANK, "打样员为空");
        }
        if (!force && OrgNameMatcher.namesEqual(previousSampler, next)) {
            log.debug("打样员未变更，跳过工作通知: goodsId={}, sampler={}",
                    goods == null ? null : goods.getGoodsId(), next);
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_UNCHANGED, "打样员未变更");
        }

        persistOutcome(SamplerNoticeResult.pending("正在发送钉钉工作通知"), goods, next,
                SamplerNoticeResult.KIND_ASSIGNMENT);

        if (!properties.isWorkNoticeEnabled()) {
            logWorkNoticeDisabled();
            SamplerNoticeResult skipped = SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_DISABLED,
                    "工作通知未启用（缺少 AgentId 或 AppKey/Secret）");
            persistOutcome(skipped, goods, next, SamplerNoticeResult.KIND_ASSIGNMENT);
            return skipped;
        }

        OrgRegistrationService.EnsureAccountResult ensured = ensureAccountFromOrg(next);
        if (isAmbiguousAccount(ensured)) {
            String detail = ensured == null ? "通讯录同名多人" : ensured.getMessage();
            log.info("打样推送取消：通讯录同名多人无法唯一开户 sampler={}, detail={}", next, detail);
            SamplerNoticeResult skipped = SamplerNoticeResult.skipped(
                    SamplerNoticeResult.Status.SKIPPED_AMBIGUOUS, detail);
            persistOutcome(skipped, goods, next, SamplerNoticeResult.KIND_ASSIGNMENT);
            return skipped;
        }
        if (ensured == null || !ensured.hasLocalAccount()) {
            log.info("打样推送开户未就绪，改用已绑定 userid 兜底: sampler={}, status={}, detail={}",
                    next,
                    ensured == null ? null : ensured.getStatus(),
                    ensured == null ? "开户结果为空" : ensured.getMessage());
        }

        DingTalkUseridResolver.ResolveResult resolved = useridResolver.resolveByName(next);
        if (resolved != null && resolved.getStatus() == DingTalkUseridResolver.ResolveResult.Status.AMBIGUOUS) {
            SamplerNoticeResult skipped = SamplerNoticeResult.skipped(
                    SamplerNoticeResult.Status.SKIPPED_AMBIGUOUS, resolved.getSource());
            persistOutcome(skipped, goods, next, SamplerNoticeResult.KIND_ASSIGNMENT);
            return skipped;
        }
        if (resolved == null || !resolved.isFound()) {
            SamplerNoticeResult skipped = SamplerNoticeResult.skipped(
                    SamplerNoticeResult.Status.SKIPPED_NO_USERID,
                    "未找到打样员「" + next + "」的钉钉 userid");
            persistOutcome(skipped, goods, next, SamplerNoticeResult.KIND_ASSIGNMENT);
            return skipped;
        }

        DingTalkWorkNotice notice = buildNotice(next, goods, resolved.getUserid());
        logClickUrl(goods, notice);
        long taskId = dingTalkClient.sendWorkNotice(resolved.getUserid(), notice);
        persistAssignment(next, goods, resolved.getUserid(), taskId);
        if (!force) {
            notifyPreviousSamplerCancelled(previousSampler, next, goods);
        }
        log.info("已向打样员发送钉钉工作通知: goodsId={}, sampler={}, userid={}, source={}, taskId={}",
                goods == null ? null : goods.getGoodsId(), next, resolved.getUserid(),
                resolved.getSource(), taskId);
        return SamplerNoticeResult.sent(resolved.getUserid(), taskId);
    }

    /**
     * 推送前按钉钉已同步通讯录开户。未成功时由调用方改走 userid 兜底。
     */
    OrgRegistrationService.EnsureAccountResult ensureAccountFromOrg(String samplerName) {
        if (orgRegistrationService == null) {
            log.warn("打样推送开户服务不可用，将尝试 userid 兜底 sampler={}", samplerName);
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
            log.warn("打样推送自动开户失败，将尝试 userid 兜底: sampler={}, err={}",
                    samplerName, e.getMessage());
            return OrgRegistrationService.EnsureAccountResult.failed(e.getMessage());
        }
    }

    private static boolean isAmbiguousAccount(OrgRegistrationService.EnsureAccountResult ensured) {
        return ensured != null
                && ensured.getStatus() == OrgRegistrationService.EnsureAccountResult.Status.SKIPPED_AMBIGUOUS;
    }

    private SamplerNoticeResult doNotifyFormFilled(GoodsSamplerNotice goods, String samplerName) {
        if (goods == null) {
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_NO_ASSIGNMENT, "商品为空");
        }
        if (!properties.isWorkNoticeEnabled()) {
            logWorkNoticeDisabled();
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_DISABLED,
                    "工作通知未启用（缺少 AgentId 或 AppKey/Secret）");
        }
        if (noticeStore == null) {
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_NO_ASSIGNMENT, "无投递记录存储");
        }
        GoodsSamplerNoticeRecord previous = noticeStore.findByGoodsId(goods.getGoodsId()).orElse(null);
        if (previous == null || !previous.hasAssignment()) {
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_NO_ASSIGNMENT,
                    "没有可回填的打样指派通知");
        }
        if (previous.hasFollowup()) {
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_ALREADY_FOLLOWED_UP,
                    "该指派通知已补发过回填卡");
        }
        if (!SamplerWorkNoticeCards.needsBackfill(previous.toSentGoods(), goods)) {
            return SamplerNoticeResult.skipped(SamplerNoticeResult.Status.SKIPPED_NO_BACKFILL,
                    "原通知已含货号/品名，无需补发");
        }

        String displaySampler = (samplerName == null || samplerName.isBlank())
                ? previous.getSamplerName() : samplerName.trim();
        persistOutcome(SamplerNoticeResult.pending("正在补发打样信息回填通知"), goods, displaySampler,
                SamplerNoticeResult.KIND_FOLLOWUP);
        DingTalkWorkNotice notice = buildFollowupNotice(displaySampler, goods, previous.getDingUserId());
        logClickUrl(goods, notice);
        long followupTaskId = dingTalkClient.sendWorkNotice(previous.getDingUserId(), notice);
        if (!markFollowupWithRetry(goods.getGoodsId(), previous.getTaskId(), followupTaskId)) {
            persistOutcome(SamplerNoticeResult.sent(previous.getDingUserId(), followupTaskId),
                    goods, displaySampler, SamplerNoticeResult.KIND_FOLLOWUP, false,
                    "回填通知已发出但未能记下 followup_task_id");
        }
        log.info("已向打样员补发打样信息回填通知: goodsId={}, userid={}, assignmentTaskId={}, followupTaskId={}",
                goods.getGoodsId(), previous.getDingUserId(), previous.getTaskId(), followupTaskId);
        return SamplerNoticeResult.sent(previous.getDingUserId(), followupTaskId);
    }

    DingTalkWorkNotice buildNotice(String samplerName, GoodsSamplerNotice goods, String dingUserId) {
        String title = SamplerWorkNoticeCards.assignmentSessionTitle(goods);
        String formHttp = goods == null ? "" : formUrl(goods.getGoodsId(), dingUserId);
        String markdown = SamplerWorkNoticeCards.assignmentMarkdown(samplerName, goods, formHttp);
        return toWorkNotice(title, markdown, SamplerWorkNoticeCards.CTA_ASSIGN, formHttp);
    }

    DingTalkWorkNotice buildFollowupNotice(String samplerName, GoodsSamplerNotice goods, String dingUserId) {
        String title = SamplerWorkNoticeCards.followupSessionTitle(goods);
        String formHttp = goods == null ? "" : formUrl(goods.getGoodsId(), dingUserId);
        String markdown = SamplerWorkNoticeCards.followupMarkdown(samplerName, goods, formHttp);
        return toWorkNotice(title, markdown, SamplerWorkNoticeCards.CTA_FOLLOWUP, formHttp);
    }

    DingTalkWorkNotice buildCancelNotice(String previousSampler, String nextSampler, GoodsSamplerNotice goods) {
        String title = SamplerWorkNoticeCards.cancelSessionTitle(goods);
        String markdown = SamplerWorkNoticeCards.cancelMarkdown(previousSampler, nextSampler, goods);
        return DingTalkWorkNotice.text(title, SamplerWorkNoticeCards.toPlainText(markdown));
    }

    private DingTalkWorkNotice toWorkNotice(String title, String markdown, String cta, String formHttp) {
        if (formHttp == null || formHttp.isBlank()) {
            return DingTalkWorkNotice.text(title, SamplerWorkNoticeCards.toPlainText(markdown));
        }
        String clickUrl = DingTalkLinks.workNoticeUrl(
                formHttp, properties.getCorpId(), properties.resolveAgentId(),
                properties.shouldWrapWorkNoticeProtocolLinks());
        return DingTalkWorkNotice.actionCard(title, markdown, cta, clickUrl);
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
        Optional<String> ticket = mintTicket(dingUserId, goodsId);
        if (ticket.isEmpty()) {
            throw new DingTalkException("打样免登 ticket 签发失败，已取消工作通知以免发出无法打开的链接");
        }
        return url + "?ticket=" + DingTalkLinks.encode(ticket.get());
    }

    private Optional<String> mintTicket(String dingUserId, long goodsId) {
        Optional<String> minted = Optional.empty();
        for (int i = 0; i < 2 && minted.isEmpty(); i++) {
            minted = ticketService.mint(dingUserId, goodsId);
        }
        return minted;
    }

    private void notifyPreviousSamplerCancelled(String previousSampler, String nextSampler,
                                                GoodsSamplerNotice goods) {
        String previous = previousSampler == null ? "" : previousSampler.trim();
        if (OrgNameMatcher.normalize(previous).isEmpty()) {
            return;
        }
        if (OrgNameMatcher.namesEqual(previous, nextSampler)) {
            return;
        }
        try {
            DingTalkUseridResolver.ResolveResult resolved = useridResolver.resolveByName(previous);
            if (resolved == null || !resolved.isFound()) {
                log.info("打样改派未通知原打样员：找不到 userid sampler={}", previous);
                return;
            }
            DingTalkWorkNotice notice = buildCancelNotice(previous, nextSampler, goods);
            long taskId = dingTalkClient.sendWorkNotice(resolved.getUserid(), notice);
            log.info("已通知原打样员任务改派: goodsId={}, sampler={}, userid={}, taskId={}",
                    goods == null ? null : goods.getGoodsId(), previous, resolved.getUserid(), taskId);
        } catch (Exception e) {
            log.warn("通知原打样员改派失败（新指派已发出）: goodsId={}, sampler={}, err={}",
                    goods == null ? null : goods.getGoodsId(), previous, e.getMessage());
        }
    }

    private void persistAssignment(String samplerName, GoodsSamplerNotice goods, String dingUserId, long taskId) {
        if (noticeStore == null || goods == null) {
            return;
        }
        GoodsSamplerNoticeRecord record = new GoodsSamplerNoticeRecord(
                goods.getGoodsId(), dingUserId, samplerName, taskId,
                goods.getFolderName(), goods.getGoodsNo(), goods.getProductName(),
                goods.getInitiator(), null,
                SamplerNoticeResult.Status.SENT.name(), "ok",
                SamplerNoticeResult.KIND_ASSIGNMENT, true);
        Exception last = null;
        for (int i = 0; i < PERSIST_RETRIES; i++) {
            try {
                noticeStore.saveAssignment(record);
                return;
            } catch (Exception e) {
                last = e;
            }
        }
        log.warn("打样工作通知已发出但未能记下 task_id（后续无法补发回填卡）: goodsId={}, taskId={}, err={}",
                goods.getGoodsId(), taskId, last == null ? null : last.getMessage());
        try {
            noticeStore.saveAssignment(new GoodsSamplerNoticeRecord(
                    goods.getGoodsId(), dingUserId, samplerName, taskId,
                    goods.getFolderName(), goods.getGoodsNo(), goods.getProductName(),
                    goods.getInitiator(), null,
                    SamplerNoticeResult.Status.SENT.name(), "通知已发出但未能记下 task_id，可补发",
                    SamplerNoticeResult.KIND_ASSIGNMENT, false));
        } catch (Exception e) {
            log.warn("补偿记下打样通知结果仍失败: goodsId={}, err={}", goods.getGoodsId(), e.getMessage());
        }
    }

    private boolean markFollowupWithRetry(long goodsId, long assignmentTaskId, long followupTaskId) {
        if (noticeStore == null) {
            return true;
        }
        Exception last = null;
        for (int i = 0; i < PERSIST_RETRIES; i++) {
            try {
                noticeStore.markFollowupSent(goodsId, assignmentTaskId, followupTaskId);
                return true;
            } catch (Exception e) {
                last = e;
            }
        }
        log.warn("打样回填通知已发出但未能记下 followup_task_id: goodsId={}, taskId={}, err={}",
                goodsId, followupTaskId, last == null ? null : last.getMessage());
        return false;
    }

    private void persistOutcome(SamplerNoticeResult result, GoodsSamplerNotice goods, String samplerName,
                                String kind) {
        persistOutcome(result, goods, samplerName, kind, true, result == null ? "" : result.getMessage());
    }

    private void persistOutcome(SamplerNoticeResult result, GoodsSamplerNotice goods, String samplerName,
                                String kind, boolean persistOk, String message) {
        if (noticeStore == null || goods == null || result == null) {
            return;
        }
        SamplerNoticeResult.Status status = result.getStatus();
        if (status == SamplerNoticeResult.Status.SKIPPED_BLANK
                || status == SamplerNoticeResult.Status.SKIPPED_UNCHANGED
                || status == SamplerNoticeResult.Status.SKIPPED_NO_ASSIGNMENT
                || status == SamplerNoticeResult.Status.SKIPPED_NO_BACKFILL
                || status == SamplerNoticeResult.Status.SKIPPED_ALREADY_FOLLOWED_UP) {
            return;
        }
        try {
            noticeStore.saveLastResult(goods.getGoodsId(), samplerName,
                    status.name(), message == null ? "" : message, kind, persistOk);
        } catch (Exception e) {
            log.warn("记下打样通知结果失败: goodsId={}, status={}, err={}",
                    goods.getGoodsId(), status, e.getMessage());
        }
    }

    static Map<String, Object> toStatusView(GoodsSamplerNoticeRecord record) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("status", record.getLastStatus());
        view.put("message", record.getLastMessage());
        view.put("kind", record.getLastKind());
        view.put("samplerName", record.getSamplerName());
        view.put("persistOk", record.isPersistOk());
        view.put("canRetry", canRetry(record.getLastStatus()));
        view.put("sent", SamplerNoticeResult.Status.SENT.name().equals(record.getLastStatus()));
        return view;
    }

    static boolean canRetry(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return !SamplerNoticeResult.Status.SKIPPED_BLANK.name().equals(status)
                && !SamplerNoticeResult.Status.SKIPPED_UNCHANGED.name().equals(status)
                && !SamplerNoticeResult.Status.SKIPPED_NO_ASSIGNMENT.name().equals(status)
                && !SamplerNoticeResult.Status.SKIPPED_NO_BACKFILL.name().equals(status)
                && !SamplerNoticeResult.Status.SKIPPED_ALREADY_FOLLOWED_UP.name().equals(status);
    }

    private void logClickUrl(GoodsSamplerNotice goods, DingTalkWorkNotice notice) {
        if (notice == null || !notice.hasLink()) {
            return;
        }
        String clickUrl = notice.getSingleUrl();
        log.info("打样工作通知 single_url: goodsId={}, wrap={}, prefix={}",
                goods == null ? null : goods.getGoodsId(),
                properties.shouldWrapWorkNoticeProtocolLinks(),
                DingTalkLinks.logSafePrefix(clickUrl));
    }

    private void logWorkNoticeDisabled() {
        if (!properties.isConfigured()) {
            log.info("钉钉工作通知未启用：缺少 DINGTALK_APP_KEY / DINGTALK_APP_SECRET，跳过发送");
        } else if (properties.getAgentId() == null || properties.getAgentId().isBlank()) {
            log.info("钉钉工作通知未启用：未配置 DINGTALK_AGENT_ID，跳过发送");
        } else {
            log.info("钉钉工作通知未启用：DINGTALK_AGENT_ID={} 不是有效数字，跳过发送",
                    properties.getAgentId());
        }
    }
}
