package com.imagemanager.service;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dingtalk.DingTalkClient;
import com.imagemanager.dingtalk.DingTalkException;
import com.imagemanager.dingtalk.DingTalkSamplerTicketService;
import com.imagemanager.dingtalk.DingTalkUseridResolver;
import com.imagemanager.dingtalk.DingTalkWorkNotice;
import com.imagemanager.entity.User;
import com.imagemanager.org.OrgRegistrationService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsSamplerNoticeServiceTest {

    private DingTalkClient dingTalkClient;
    private DingTalkUseridResolver useridResolver;
    private OrgRegistrationService orgRegistrationService;
    private DingTalkProperties properties;
    private DingTalkSamplerTicketService ticketService;
    private InMemoryGoodsSamplerNoticeStore noticeStore;
    private GoodsSamplerNoticeService service;

    @BeforeEach
    void setUp() {
        dingTalkClient = mock(DingTalkClient.class);
        useridResolver = mock(DingTalkUseridResolver.class);
        orgRegistrationService = mock(OrgRegistrationService.class);
        ticketService = mock(DingTalkSamplerTicketService.class);
        noticeStore = new InMemoryGoodsSamplerNoticeStore();
        properties = new DingTalkProperties();
        properties.setAppKey("key");
        properties.setAppSecret("secret");
        properties.setAgentId("123456");
        when(ticketService.mint(anyString(), anyLong())).thenReturn(Optional.of("v1.payload.sig"));
        when(orgRegistrationService.ensureAccountByName(anyString()))
                .thenReturn(OrgRegistrationService.EnsureAccountResult.alreadyExists());
        service = newService("http://localhost:5000");
    }

    private GoodsSamplerNoticeService newService(String frontendUrl) {
        return new GoodsSamplerNoticeService(
                dingTalkClient, useridResolver, orgRegistrationService, properties, ticketService,
                noticeStore, frontendUrl);
    }

    @Test
    void skipsWhenSamplerBlank() {
        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "  ", sampleGoods());
        assertEquals(SamplerNoticeResult.Status.SKIPPED_BLANK, result.getStatus());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
        verify(orgRegistrationService, never()).ensureAccountByName(anyString());
    }

    @Test
    void skipsWhenSamplerUnchangedIncludingInnerSpace() {
        SamplerNoticeResult result = service.notifyIfSamplerChanged("张 三", "张三", sampleGoods());
        assertEquals(SamplerNoticeResult.Status.SKIPPED_UNCHANGED, result.getStatus());
        verify(useridResolver, never()).resolveByName(anyString());
        verify(orgRegistrationService, never()).ensureAccountByName(anyString());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
    }

    @Test
    void skipsWhenAgentIdMissingFeatureOff() {
        properties.setAgentId("");
        when(useridResolver.resolveByName("李四")).thenReturn(
                found("u-li"));

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SKIPPED_DISABLED, result.getStatus());
        assertTrue(result.getMessage().contains("AgentId"));
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
        verify(useridResolver, never()).resolveByName(anyString());
        verify(orgRegistrationService, never()).ensureAccountByName(anyString());
    }

    @Test
    void skipsWhenAppKeyMissingFeatureOff() {
        properties.setAppKey("");
        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());
        assertEquals(SamplerNoticeResult.Status.SKIPPED_DISABLED, result.getStatus());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
        verify(orgRegistrationService, never()).ensureAccountByName(anyString());
    }

    @Test
    void skipsWhenAgentIdNotNumeric() {
        properties.setAgentId("not-a-number");
        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());
        assertEquals(SamplerNoticeResult.Status.SKIPPED_DISABLED, result.getStatus());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
        verify(orgRegistrationService, never()).ensureAccountByName(anyString());
    }

    @Test
    void skipsWhenUseridMissingAfterAccountReady() {
        when(useridResolver.resolveByName("王五")).thenReturn(
                DingTalkUseridResolver.ResolveResult.missing());

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "王五", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SKIPPED_NO_USERID, result.getStatus());
        verify(orgRegistrationService).ensureAccountByName("王五");
        InOrder order = inOrder(orgRegistrationService, useridResolver);
        order.verify(orgRegistrationService).ensureAccountByName("王五");
        order.verify(useridResolver).resolveByName("王五");
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
    }

    @Test
    void skipsWhenNameAmbiguous() {
        when(orgRegistrationService.ensureAccountByName("张三")).thenReturn(
                OrgRegistrationService.EnsureAccountResult.skippedAmbiguous("org_users 同名 2 人"));

        SamplerNoticeResult result = service.notifyIfSamplerChanged("", "张三", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SKIPPED_AMBIGUOUS, result.getStatus());
        verify(orgRegistrationService).ensureAccountByName("张三");
        verify(useridResolver, never()).resolveByName(anyString());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
    }

    @Test
    void sendsWhenSamplerNewlySet() {
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(42L);

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SENT, result.getStatus());
        assertEquals("u-li", result.getDingUserId());
        assertEquals(42L, result.getTaskId());
        verify(orgRegistrationService).ensureAccountByName("李四");

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), captor.capture());
        DingTalkWorkNotice notice = captor.getValue();
        assertEquals("打样任务 · BN-001 真丝吊带", notice.getTitle());
        assertTrue(notice.getBody().contains("**BN-001 真丝吊带**"));
        assertTrue(notice.getBody().contains("- **货号：** BN-001"));
        assertTrue(notice.getBody().contains("- **品名：** 真丝吊带"));
        assertTrue(notice.getBody().contains("打样员"));
        assertTrue(notice.hasLink());
        assertTrue(notice.getBody().contains("[立即填写打样表单](http://localhost:5000/sampler/9?ticket=v1.payload.sig)"));
        assertEquals("http://localhost:5000/sampler/9?ticket=v1.payload.sig", notice.getSingleUrl());
        assertEquals("立即填写打样表单", notice.getSingleTitle());
        assertTrue(noticeStore.findByGoodsId(9L).isPresent());
        assertEquals(42L, noticeStore.findByGoodsId(9L).get().getTaskId());
        assertEnsureThenResolveThenSend("李四", "u-li");
    }

    @Test
    void sendsWhenSamplerChangedToDifferentPerson() {
        when(useridResolver.resolveByName("王五")).thenReturn(found("u-wang"));
        when(dingTalkClient.sendWorkNotice(eq("u-wang"), any())).thenReturn(7L);

        SamplerNoticeResult result = service.notifyIfSamplerChanged("李四", "王五", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SENT, result.getStatus());
        assertEnsureThenResolveThenSend("王五", "u-wang");
    }

    @Test
    void sendFailureDoesNotThrow() {
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(anyString(), any()))
                .thenThrow(new DingTalkException("网络超时"));

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("网络超时"));
        assertEnsureThenResolveThenSend("李四", "u-li");
    }

    @Test
    void formUrlFallsBackToTextWhenFrontendBlank() {
        GoodsSamplerNoticeService noUrl = newService("  ");
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(1L);

        noUrl.notifyIfSamplerChanged(null, "李四", sampleGoods());

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), captor.capture());
        assertTrue(!captor.getValue().hasLink());
        assertEquals("text", captor.getValue().toMsgMap().get("msgtype"));
    }

    @Test
    void unnamedGoodsStillLinksByNumericId() {
        when(useridResolver.resolveByName("肖伟")).thenReturn(found("u-xiao"));
        when(dingTalkClient.sendWorkNotice(eq("u-xiao"), any())).thenReturn(1L);

        GoodsSamplerNotice unnamed = new GoodsSamplerNotice(77L, "未命名商品", "", "", "肖伟");
        service.notifyIfSamplerChanged(null, "肖伟", unnamed);

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-xiao"), captor.capture());
        DingTalkWorkNotice notice = captor.getValue();
        assertTrue(notice.getBody().contains("待完善货号与品名"));
        assertTrue(notice.getBody().contains("- **货号：** 待填写"));
        assertTrue(notice.getBody().contains("- **品名：** 待填写"));
        assertFalse(notice.getBody().contains("未命名商品"));
        assertFalse(notice.getBody().contains("未填写"));
        assertTrue(notice.getBody().contains("[立即填写打样表单](http://localhost:5000/sampler/77?ticket=v1.payload.sig)"));
        assertEquals("http://localhost:5000/sampler/77?ticket=v1.payload.sig", notice.getSingleUrl());
        assertTrue(!notice.getSingleUrl().contains("goods-library"));
        assertEquals("打样任务 · 待完善货号与品名", notice.getTitle());
    }

    @Test
    void protocolFlagFalseKeepsPlainHttpEvenWithCorpId() {
        properties.setCorpId("dingcorp");
        properties.setWorkNoticeProtocolLinks(false);
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(1L);

        service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), captor.capture());
        assertEquals("http://localhost:5000/sampler/9?ticket=v1.payload.sig", captor.getValue().getSingleUrl());
    }

    @Test
    void defaultProtocolFlagWrapsOpenAppWhenCorpIdPresent() {
        properties.setCorpId("dingcorp");
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(1L);

        service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), captor.capture());
        String click = captor.getValue().getSingleUrl();
        assertTrue(click.startsWith("dingtalk://dingtalkclient/action/openapp"));
        assertTrue(click.contains("redirect_url="));
        assertTrue(click.contains("sampler%2F9"));
        assertTrue(captor.getValue().getBody().contains("[立即填写打样表单](http://localhost:5000/sampler/9?ticket=v1.payload.sig)"));
    }

    @Test
    void logsClickUrlPrefixWithoutQuerySecrets() {
        properties.setCorpId("dingcorp");
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(1L);

        Logger logger = (Logger) LoggerFactory.getLogger(GoodsSamplerNoticeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.notifyIfSamplerChanged(null, "李四", sampleGoods());
            assertTrue(appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.INFO
                            && e.getFormattedMessage().contains("single_url")
                            && e.getFormattedMessage().contains("goodsId=9")
                            && e.getFormattedMessage().contains(
                                    "prefix=dingtalk://dingtalkclient/action/openapp")
                            && !e.getFormattedMessage().contains("corpid")
                            && !e.getFormattedMessage().contains("secret")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void openAppWrapWhenCorpIdAndProtocolFlag() {
        properties.setCorpId("dingcorp");
        properties.setWorkNoticeProtocolLinks(true);
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(1L);

        service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), captor.capture());
        String click = captor.getValue().getSingleUrl();
        assertTrue(click.contains("action/openapp"));
        assertTrue(click.contains("redirect_url="));
        assertTrue(click.contains("sampler%2F9"));
        assertTrue(captor.getValue().getBody().contains("[立即填写打样表单](http://localhost:5000/sampler/9?ticket=v1.payload.sig)"));
    }

    @Test
    void formUrlStripsTrailingSlash() {
        GoodsSamplerNoticeService trailing = newService("http://ai.bonasoma.com/");
        assertEquals("http://ai.bonasoma.com/sampler/9", trailing.formUrl(9L));
    }

    @Test
    void appendsSignedTicketToFormUrlAndMarkdown() {
        when(ticketService.mint("u-li", 9L)).thenReturn(Optional.of("v1.payload.sig"));
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(1L);

        service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), captor.capture());
        DingTalkWorkNotice notice = captor.getValue();
        String expected = "http://localhost:5000/sampler/9?ticket=v1.payload.sig";
        assertEquals(expected, notice.getSingleUrl());
        assertTrue(notice.getBody().contains("[立即填写打样表单](" + expected + ")"));
        InOrder order = inOrder(orgRegistrationService, useridResolver, ticketService, dingTalkClient);
        order.verify(orgRegistrationService).ensureAccountByName("李四");
        order.verify(useridResolver).resolveByName("李四");
        order.verify(ticketService).mint("u-li", 9L);
        order.verify(dingTalkClient).sendWorkNotice(eq("u-li"), any());
    }

    @Test
    void ticketOnHttpUrlIsWrappedByOpenApp() {
        properties.setCorpId("dingcorp");
        when(ticketService.mint("u-li", 9L)).thenReturn(Optional.of("v1.payload.sig"));
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(1L);

        service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), captor.capture());
        String click = captor.getValue().getSingleUrl();
        assertTrue(click.startsWith("dingtalk://dingtalkclient/action/openapp"));
        assertTrue(click.contains("sampler%2F9"));
        assertTrue(click.contains("ticket%3Dv1.payload.sig"));
        assertTrue(captor.getValue().getBody().contains(
                "http://localhost:5000/sampler/9?ticket=v1.payload.sig"));
    }

    @Test
    void clickUrlLogOmitsTicketQuery() {
        properties.setCorpId("dingcorp");
        when(ticketService.mint("u-li", 9L)).thenReturn(Optional.of("v1.payload.sig"));
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(1L);

        Logger logger = (Logger) LoggerFactory.getLogger(GoodsSamplerNoticeService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.notifyIfSamplerChanged(null, "李四", sampleGoods());
            assertTrue(appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.INFO
                            && e.getFormattedMessage().contains("single_url")
                            && e.getFormattedMessage().contains(
                                    "prefix=dingtalk://dingtalkclient/action/openapp")
                            && !e.getFormattedMessage().contains("v1.payload.sig")
                            && !e.getFormattedMessage().contains("ticket=")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void mintEmptyCancelsNotice() {
        when(ticketService.mint("u-li", 9L)).thenReturn(Optional.empty());
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("ticket"));
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
        assertEquals(SamplerNoticeResult.Status.FAILED.name(),
                noticeStore.findByGoodsId(9L).get().getLastStatus());
    }

    @Test
    void alreadyHasAccountStillSendsNotice() {
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(orgRegistrationService.ensureAccountByName("李四"))
                .thenReturn(OrgRegistrationService.EnsureAccountResult.alreadyExists());
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(11L);

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SENT, result.getStatus());
        assertEquals(11L, result.getTaskId());
        assertEnsureThenResolveThenSend("李四", "u-li");
    }

    @Test
    void uniqueOrgMatchCreatesAccountThenNoticeProceeds() {
        User created = User.builder()
                .id("new-user")
                .username("王五")
                .dingtalkUserid("u-wang")
                .mustChangePassword(true)
                .build();
        when(useridResolver.resolveByName("王五")).thenReturn(found("u-wang"));
        when(orgRegistrationService.ensureAccountByName("王五"))
                .thenReturn(OrgRegistrationService.EnsureAccountResult.created(created));
        when(dingTalkClient.sendWorkNotice(eq("u-wang"), any())).thenReturn(8L);

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "王五", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SENT, result.getStatus());
        assertEquals("u-wang", result.getDingUserId());
        assertEnsureThenResolveThenSend("王五", "u-wang");
    }

    @Test
    void ambiguousNameDoesNotCreateAccountOrSend() {
        when(orgRegistrationService.ensureAccountByName("张三")).thenReturn(
                OrgRegistrationService.EnsureAccountResult.skippedAmbiguous("找到多名同名员工，请选择您的身份"));

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "张三", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SKIPPED_AMBIGUOUS, result.getStatus());
        verify(orgRegistrationService).ensureAccountByName("张三");
        verify(useridResolver, never()).resolveByName(anyString());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
    }

    @Test
    void missingOrgContactFallsBackToLocalUserid() {
        when(orgRegistrationService.ensureAccountByName("赵六")).thenReturn(
                OrgRegistrationService.EnsureAccountResult.skippedNoMatch("未在钉钉通讯录中找到该姓名"));
        when(useridResolver.resolveByName("赵六")).thenReturn(found("u-zhao"));
        when(dingTalkClient.sendWorkNotice(eq("u-zhao"), any())).thenReturn(5L);

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "赵六", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SENT, result.getStatus());
        assertEquals("u-zhao", result.getDingUserId());
        verify(orgRegistrationService).ensureAccountByName("赵六");
        verify(useridResolver).resolveByName("赵六");
        verify(dingTalkClient).sendWorkNotice(eq("u-zhao"), any());
    }

    @Test
    void missingOrgContactAndLocalUseridSkips() {
        when(orgRegistrationService.ensureAccountByName("赵六")).thenReturn(
                OrgRegistrationService.EnsureAccountResult.skippedNoMatch("未在钉钉通讯录中找到该姓名"));
        when(useridResolver.resolveByName("赵六")).thenReturn(
                DingTalkUseridResolver.ResolveResult.missing());

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "赵六", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SKIPPED_NO_USERID, result.getStatus());
        verify(orgRegistrationService).ensureAccountByName("赵六");
        verify(useridResolver).resolveByName("赵六");
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
        assertEquals(SamplerNoticeResult.Status.SKIPPED_NO_USERID.name(),
                noticeStore.findByGoodsId(9L).get().getLastStatus());
    }

    @Test
    void createAccountFailureFallsBackToUserid() {
        when(orgRegistrationService.ensureAccountByName("李四"))
                .thenReturn(OrgRegistrationService.EnsureAccountResult.failed("db down"));
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(3L);

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SENT, result.getStatus());
        verify(orgRegistrationService).ensureAccountByName("李四");
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), any());
    }

    @Test
    void createAccountThrowFallsBackToUserid() {
        when(orgRegistrationService.ensureAccountByName("李四"))
                .thenThrow(new RuntimeException("unexpected"));
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(4L);

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SENT, result.getStatus());
        verify(orgRegistrationService).ensureAccountByName("李四");
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), any());
    }

    @Test
    void formFilledSendsFollowupWithFilledFieldsAndKeepsProtocolLink() {
        properties.setCorpId("dingcorp");
        when(useridResolver.resolveByName("肖伟")).thenReturn(found("u-xiao"));
        when(dingTalkClient.sendWorkNotice(eq("u-xiao"), any())).thenReturn(11L, 22L);

        GoodsSamplerNotice unnamed = new GoodsSamplerNotice(77L, "未命名商品", "", "", "肖伟");
        assertEquals(SamplerNoticeResult.Status.SENT,
                service.notifyIfSamplerChanged(null, "肖伟", unnamed).getStatus());

        GoodsSamplerNotice filled = new GoodsSamplerNotice(
                77L, "A1吊带", "A1", "吊带", "肖伟", "客户甲", "SO-9");
        SamplerNoticeResult followup = service.notifyFormFilled(filled, "肖伟");

        assertEquals(SamplerNoticeResult.Status.SENT, followup.getStatus());
        assertEquals(22L, followup.getTaskId());
        assertEquals("u-xiao", followup.getDingUserId());

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient, times(2)).sendWorkNotice(eq("u-xiao"), captor.capture());
        DingTalkWorkNotice card = captor.getAllValues().get(1);
        assertTrue(card.getTitle().startsWith("打样信息已更新"));
        assertTrue(card.getBody().contains("**A1 吊带**"));
        assertTrue(card.getBody().contains("- **货号：** A1"));
        assertTrue(card.getBody().contains("- **品名：** 吊带"));
        assertTrue(card.getBody().contains("- **客户：** 客户甲"));
        assertFalse(card.getBody().contains("待填写"));
        assertFalse(card.getBody().contains("未命名商品"));
        assertEquals("查看打样表单", card.getSingleTitle());
        assertTrue(card.getSingleUrl().startsWith("dingtalk://dingtalkclient/action/openapp"));
        assertTrue(card.getSingleUrl().contains("sampler%2F77"));
        assertTrue(noticeStore.findByGoodsId(77L).get().hasFollowup());
        verify(useridResolver, times(1)).resolveByName("肖伟");
        verify(orgRegistrationService).ensureAccountByName("肖伟");
    }

    @Test
    void formFilledSkipsWhenNoAssignmentRecord() {
        GoodsSamplerNotice filled = new GoodsSamplerNotice(9L, "BN-001真丝吊带", "BN-001", "真丝吊带", "张三");
        SamplerNoticeResult result = service.notifyFormFilled(filled, "李四");
        assertEquals(SamplerNoticeResult.Status.SKIPPED_NO_ASSIGNMENT, result.getStatus());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
        verify(orgRegistrationService, never()).ensureAccountByName(anyString());
    }

    @Test
    void formFilledSkipsWhenOriginalCardAlreadyHadIdentity() {
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(42L);
        service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        SamplerNoticeResult result = service.notifyFormFilled(sampleGoods(), "李四");
        assertEquals(SamplerNoticeResult.Status.SKIPPED_NO_BACKFILL, result.getStatus());
        verify(dingTalkClient, times(1)).sendWorkNotice(anyString(), any());
    }

    @Test
    void formFilledDoesNotSendTwiceForSameAssignment() {
        when(useridResolver.resolveByName("肖伟")).thenReturn(found("u-xiao"));
        when(dingTalkClient.sendWorkNotice(eq("u-xiao"), any())).thenReturn(11L, 22L, 33L);

        GoodsSamplerNotice unnamed = new GoodsSamplerNotice(77L, "未命名商品", "", "", "肖伟");
        service.notifyIfSamplerChanged(null, "肖伟", unnamed);
        GoodsSamplerNotice filled = new GoodsSamplerNotice(77L, "A1吊带", "A1", "吊带", "肖伟");
        assertEquals(SamplerNoticeResult.Status.SENT, service.notifyFormFilled(filled, "肖伟").getStatus());
        assertEquals(SamplerNoticeResult.Status.SKIPPED_ALREADY_FOLLOWED_UP,
                service.notifyFormFilled(filled, "肖伟").getStatus());
        verify(dingTalkClient, times(2)).sendWorkNotice(eq("u-xiao"), any());
    }

    @Test
    void formFilledFailureDoesNotThrow() {
        when(useridResolver.resolveByName("肖伟")).thenReturn(found("u-xiao"));
        when(dingTalkClient.sendWorkNotice(eq("u-xiao"), any()))
                .thenReturn(11L)
                .thenThrow(new DingTalkException("网络超时"));
        service.notifyIfSamplerChanged(null, "肖伟",
                new GoodsSamplerNotice(77L, "未命名商品", "", "", "肖伟"));

        SamplerNoticeResult result = service.notifyFormFilled(
                new GoodsSamplerNotice(77L, "A1吊带", "A1", "吊带", "肖伟"), "肖伟");
        assertEquals(SamplerNoticeResult.Status.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("网络超时"));
        assertFalse(noticeStore.findByGoodsId(77L).get().hasFollowup());
    }

    @Test
    void formFilledSkipsWhenWorkNoticeDisabled() {
        when(useridResolver.resolveByName("肖伟")).thenReturn(found("u-xiao"));
        when(dingTalkClient.sendWorkNotice(eq("u-xiao"), any())).thenReturn(11L);
        service.notifyIfSamplerChanged(null, "肖伟",
                new GoodsSamplerNotice(77L, "未命名商品", "", "", "肖伟"));
        properties.setAgentId("");

        SamplerNoticeResult result = service.notifyFormFilled(
                new GoodsSamplerNotice(77L, "A1吊带", "A1", "吊带", "肖伟"), "肖伟");
        assertEquals(SamplerNoticeResult.Status.SKIPPED_DISABLED, result.getStatus());
        verify(dingTalkClient, times(1)).sendWorkNotice(anyString(), any());
    }

    @Test
    void reassignmentNotifiesPreviousSampler() {
        when(useridResolver.resolveByName("王五")).thenReturn(found("u-wang"));
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-wang"), any())).thenReturn(7L);
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(8L);

        SamplerNoticeResult result = service.notifyIfSamplerChanged("李四", "王五", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SENT, result.getStatus());
        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), captor.capture());
        DingTalkWorkNotice cancel = captor.getValue();
        assertTrue(cancel.getTitle().startsWith("打样任务已改派"));
        assertTrue(cancel.getBody().contains("王五"));
        assertTrue(cancel.getBody().contains("无需继续填写"));
        assertFalse(cancel.hasLink());
    }

    @Test
    void resendSendsAgainEvenWhenSamplerUnchanged() {
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(1L, 2L);
        assertEquals(SamplerNoticeResult.Status.SENT,
                service.notifyIfSamplerChanged(null, "李四", sampleGoods()).getStatus());

        SamplerNoticeResult again = service.resendAssignment(sampleGoods(), "李四");
        assertEquals(SamplerNoticeResult.Status.SENT, again.getStatus());
        assertEquals(2L, again.getTaskId());
        verify(dingTalkClient, times(2)).sendWorkNotice(eq("u-li"), any());
    }

    @Test
    void sendFailurePersistsFailedStatus() {
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(anyString(), any()))
                .thenThrow(new DingTalkException("网络超时"));

        service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        GoodsSamplerNoticeRecord stored = noticeStore.findByGoodsId(9L).orElseThrow();
        assertEquals(SamplerNoticeResult.Status.FAILED.name(), stored.getLastStatus());
        assertTrue(stored.getLastMessage().contains("网络超时"));
        assertTrue(GoodsSamplerNoticeService.canRetry(stored.getLastStatus()));
    }

    private void assertEnsureThenResolveThenSend(String samplerName, String userid) {
        InOrder order = inOrder(orgRegistrationService, useridResolver, dingTalkClient);
        order.verify(orgRegistrationService).ensureAccountByName(samplerName);
        order.verify(useridResolver).resolveByName(samplerName);
        order.verify(dingTalkClient).sendWorkNotice(eq(userid), any());
    }

    private static GoodsSamplerNotice sampleGoods() {
        return new GoodsSamplerNotice(9L, "BN-001真丝吊带", "BN-001", "真丝吊带", "张三");
    }

    private static DingTalkUseridResolver.ResolveResult found(String userid) {
        return DingTalkUseridResolver.ResolveResult.found(userid, "org_users");
    }
}
