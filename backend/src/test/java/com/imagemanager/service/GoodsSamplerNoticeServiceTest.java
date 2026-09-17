package com.imagemanager.service;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dingtalk.DingTalkClient;
import com.imagemanager.dingtalk.DingTalkException;
import com.imagemanager.dingtalk.DingTalkUseridResolver;
import com.imagemanager.dingtalk.DingTalkWorkNotice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsSamplerNoticeServiceTest {

    private DingTalkClient dingTalkClient;
    private DingTalkUseridResolver useridResolver;
    private DingTalkProperties properties;
    private GoodsSamplerNoticeService service;

    @BeforeEach
    void setUp() {
        dingTalkClient = mock(DingTalkClient.class);
        useridResolver = mock(DingTalkUseridResolver.class);
        properties = new DingTalkProperties();
        properties.setAppKey("key");
        properties.setAppSecret("secret");
        properties.setAgentId("123456");
        service = new GoodsSamplerNoticeService(
                dingTalkClient, useridResolver, properties, "http://localhost:5000");
    }

    @Test
    void skipsWhenSamplerBlank() {
        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "  ", sampleGoods());
        assertEquals(SamplerNoticeResult.Status.SKIPPED_BLANK, result.getStatus());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
    }

    @Test
    void skipsWhenSamplerUnchangedIncludingInnerSpace() {
        SamplerNoticeResult result = service.notifyIfSamplerChanged("张 三", "张三", sampleGoods());
        assertEquals(SamplerNoticeResult.Status.SKIPPED_UNCHANGED, result.getStatus());
        verify(useridResolver, never()).resolveByName(anyString());
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
    }

    @Test
    void skipsWhenAppKeyMissingFeatureOff() {
        properties.setAppKey("");
        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());
        assertEquals(SamplerNoticeResult.Status.SKIPPED_DISABLED, result.getStatus());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
    }

    @Test
    void skipsWhenAgentIdNotNumeric() {
        properties.setAgentId("not-a-number");
        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());
        assertEquals(SamplerNoticeResult.Status.SKIPPED_DISABLED, result.getStatus());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
    }

    @Test
    void skipsWhenUseridMissing() {
        when(useridResolver.resolveByName("王五")).thenReturn(
                DingTalkUseridResolver.ResolveResult.missing());

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "王五", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SKIPPED_NO_USERID, result.getStatus());
        verify(dingTalkClient, never()).sendWorkNotice(anyString(), any());
    }

    @Test
    void skipsWhenNameAmbiguous() {
        when(useridResolver.resolveByName("张三")).thenReturn(
                DingTalkUseridResolver.ResolveResult.ambiguous("org_users 同名 2 人"));

        SamplerNoticeResult result = service.notifyIfSamplerChanged("", "张三", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SKIPPED_AMBIGUOUS, result.getStatus());
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

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), captor.capture());
        DingTalkWorkNotice notice = captor.getValue();
        assertEquals("您被指定为打样员", notice.getTitle());
        assertTrue(notice.getBody().contains("BN-001真丝吊带"));
        assertTrue(notice.getBody().contains("打样员"));
        assertTrue(notice.hasLink());
        assertTrue(notice.getBody().contains("http://localhost:5000/sampler/9"));
        assertTrue(notice.getSingleUrl().contains("sampler%2F9")
                || notice.getSingleUrl().contains("/sampler/9"));
        assertTrue(notice.getSingleUrl().startsWith("dingtalk://"));
        assertEquals("填写打样表单", notice.getSingleTitle());
    }

    @Test
    void sendsWhenSamplerChangedToDifferentPerson() {
        when(useridResolver.resolveByName("王五")).thenReturn(found("u-wang"));
        when(dingTalkClient.sendWorkNotice(eq("u-wang"), any())).thenReturn(7L);

        SamplerNoticeResult result = service.notifyIfSamplerChanged("李四", "王五", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.SENT, result.getStatus());
        verify(dingTalkClient).sendWorkNotice(eq("u-wang"), any());
    }

    @Test
    void sendFailureDoesNotThrow() {
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(anyString(), any()))
                .thenThrow(new DingTalkException("网络超时"));

        SamplerNoticeResult result = service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        assertEquals(SamplerNoticeResult.Status.FAILED, result.getStatus());
        assertTrue(result.getMessage().contains("网络超时"));
    }

    @Test
    void formUrlFallsBackToTextWhenFrontendBlank() {
        GoodsSamplerNoticeService noUrl = new GoodsSamplerNoticeService(
                dingTalkClient, useridResolver, properties, "  ");
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
        assertTrue(notice.getBody().contains("未命名商品"));
        assertTrue(notice.getBody().contains("http://localhost:5000/sampler/77"));
        assertTrue(notice.getSingleUrl().contains("sampler%2F77")
                || notice.getSingleUrl().contains("/sampler/77"));
        assertTrue(!notice.getSingleUrl().contains("goods-library"));
    }

    @Test
    void openAppWrapWhenCorpIdConfigured() {
        properties.setCorpId("dingcorp");
        when(useridResolver.resolveByName("李四")).thenReturn(found("u-li"));
        when(dingTalkClient.sendWorkNotice(eq("u-li"), any())).thenReturn(1L);

        service.notifyIfSamplerChanged(null, "李四", sampleGoods());

        ArgumentCaptor<DingTalkWorkNotice> captor = ArgumentCaptor.forClass(DingTalkWorkNotice.class);
        verify(dingTalkClient).sendWorkNotice(eq("u-li"), captor.capture());
        String click = captor.getValue().getSingleUrl();
        assertTrue(click.contains("action/openapp"));
        assertTrue(click.contains("redirect_url="));
        assertTrue(click.contains("sampler%2F9"));
    }

    @Test
    void formUrlStripsTrailingSlash() {
        GoodsSamplerNoticeService trailing = new GoodsSamplerNoticeService(
                dingTalkClient, useridResolver, properties, "http://ai.bonasoma.com/");
        assertEquals("http://ai.bonasoma.com/sampler/9", trailing.formUrl(9L));
    }

    private static GoodsSamplerNotice sampleGoods() {
        return new GoodsSamplerNotice(9L, "BN-001真丝吊带", "BN-001", "真丝吊带", "张三");
    }

    private static DingTalkUseridResolver.ResolveResult found(String userid) {
        return DingTalkUseridResolver.ResolveResult.found(userid, "org_users");
    }
}
