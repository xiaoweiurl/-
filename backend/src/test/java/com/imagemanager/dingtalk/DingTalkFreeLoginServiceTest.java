package com.imagemanager.dingtalk;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dto.LoginResponse;
import com.imagemanager.entity.User;
import com.imagemanager.org.OrgContact;
import com.imagemanager.org.OrgDirectory;
import com.imagemanager.repository.UserRepository;
import com.imagemanager.service.AuthService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DingTalkFreeLoginServiceTest {

    private DingTalkClient dingTalkClient;
    private UserRepository userRepository;
    private OrgDirectory orgDirectory;
    private AuthService authService;
    private DingTalkProperties properties;
    private DingTalkFreeLoginService service;

    @BeforeEach
    void setUp() {
        dingTalkClient = mock(DingTalkClient.class);
        userRepository = mock(UserRepository.class);
        orgDirectory = mock(OrgDirectory.class);
        authService = mock(AuthService.class);
        properties = new DingTalkProperties();
        properties.setCompany("宝娜斯集团");
        service = new DingTalkFreeLoginService(
                dingTalkClient, userRepository, orgDirectory, authService, properties);
        when(authService.issueSession(any(), anyBoolean(), anyBoolean()))
                .thenAnswer(inv -> LoginResponse.builder()
                        .sessionId("sess-full")
                        .user(LoginResponse.UserInfo.builder()
                                .id(((User) inv.getArgument(0)).getId())
                                .username(((User) inv.getArgument(0)).getUsername())
                                .role("user")
                                .scope("full")
                                .build())
                        .build());
        when(authService.issueSamplerSession(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(LoginResponse.builder()
                        .sessionId("sess-sampler")
                        .user(LoginResponse.UserInfo.builder()
                                .id("dt:u-org")
                                .username("王五")
                                .role("sampler")
                                .scope("sampler")
                                .samplerGoodsId("9")
                                .build())
                        .build());
    }

    @Test
    void boundDingtalkUseridCreatesFullSession() {
        when(dingTalkClient.getUserByAuthCode("code-1")).thenReturn(
                DingTalkAuthUser.builder().userid("u-zhang").name("张三").build());
        User user = User.builder().id("id-1").username("张三").dingtalkUserid("u-zhang").role("user").build();
        when(userRepository.findByDingtalkUserid("u-zhang")).thenReturn(Optional.of(user));

        LoginResponse response = service.login("code-1", 9L);

        assertEquals("sess-full", response.getSessionId());
        assertEquals("id-1", response.getUser().getId());
        verify(authService).issueSession(user, false, true);
        verify(authService, never()).issueSamplerSession(anyString(), anyString(), anyString(), anyLong());
        verify(orgDirectory, never()).findByDingUserId(anyString(), anyString());
    }

    @Test
    void orgContactBoundLocalUserCreatesFullSession() {
        when(dingTalkClient.getUserByAuthCode("code-2")).thenReturn(
                DingTalkAuthUser.builder().userid("u-li").name("李四").unionid("un").build());
        when(userRepository.findByDingtalkUserid("u-li")).thenReturn(Optional.empty());
        when(orgDirectory.findByDingUserId("宝娜斯集团", "u-li")).thenReturn(Optional.of(
                OrgContact.builder().id("org-1").dingUserId("u-li").name("李四").localUserId("id-2").active(true).build()));
        User user = User.builder().id("id-2").username("李四").role("user").build();
        when(userRepository.findById("id-2")).thenReturn(Optional.of(user));

        LoginResponse response = service.login("code-2", 9L);

        assertEquals("sess-full", response.getSessionId());
        verify(userRepository).save(user);
        assertEquals("u-li", user.getDingtalkUserid());
        verify(authService).issueSession(user, false, true);
    }

    @Test
    void uniqueNameMatchBindsAndCreatesFullSession() {
        when(dingTalkClient.getUserByAuthCode("code-3")).thenReturn(
                DingTalkAuthUser.builder().userid("u-zhao").name("赵六").build());
        when(userRepository.findByDingtalkUserid("u-zhao")).thenReturn(Optional.empty());
        when(orgDirectory.findByDingUserId("宝娜斯集团", "u-zhao")).thenReturn(Optional.of(
                OrgContact.builder().id("org-z").dingUserId("u-zhao").name("赵六").active(true).build()));
        User user = User.builder().id("id-z").username("赵六").nickname("赵六").role("user").build();
        when(userRepository.findByUsername("赵六")).thenReturn(Optional.of(user));
        when(userRepository.findByNickname("赵六")).thenReturn(List.of(user));

        LoginResponse response = service.login("code-3", 9L);

        assertEquals("sess-full", response.getSessionId());
        verify(orgDirectory).bindLocalUser("org-z", "id-z");
        verify(userRepository).save(user);
        assertEquals("u-zhao", user.getDingtalkUserid());
    }

    @Test
    void orgContactWithoutUserGetsSamplerSession() {
        when(dingTalkClient.getUserByAuthCode("code-4")).thenReturn(
                DingTalkAuthUser.builder().userid("u-wang").name("王五").build());
        when(userRepository.findByDingtalkUserid("u-wang")).thenReturn(Optional.empty());
        when(orgDirectory.findByDingUserId("宝娜斯集团", "u-wang")).thenReturn(Optional.of(
                OrgContact.builder().id("org-w").dingUserId("u-wang").name("王五").active(true).build()));
        when(userRepository.findByUsername("王五")).thenReturn(Optional.empty());
        when(userRepository.findByNickname("王五")).thenReturn(List.of());

        LoginResponse response = service.login("code-4", 9L);

        assertEquals("sess-sampler", response.getSessionId());
        assertEquals("sampler", response.getUser().getScope());
        verify(authService).issueSamplerSession("u-wang", "王五", "宝娜斯集团", 9L);
        verify(authService, never()).issueSession(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void orgContactWithoutGoodsIdRequiresGoods() {
        when(dingTalkClient.getUserByAuthCode("code-5")).thenReturn(
                DingTalkAuthUser.builder().userid("u-wang").name("王五").build());
        when(userRepository.findByDingtalkUserid("u-wang")).thenReturn(Optional.empty());
        when(orgDirectory.findByDingUserId("宝娜斯集团", "u-wang")).thenReturn(Optional.of(
                OrgContact.builder().dingUserId("u-wang").name("王五").active(true).build()));
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByNickname(anyString())).thenReturn(List.of());

        DingTalkFreeLoginException ex = assertThrows(DingTalkFreeLoginException.class,
                () -> service.login("code-5", null));
        assertEquals(400, ex.getHttpStatus());
    }

    @Test
    void unmatchedThrows401() {
        when(dingTalkClient.getUserByAuthCode("code-6")).thenReturn(
                DingTalkAuthUser.builder().userid("u-unknown").name("路人").build());
        when(userRepository.findByDingtalkUserid("u-unknown")).thenReturn(Optional.empty());
        when(orgDirectory.findByDingUserId(eq("宝娜斯集团"), eq("u-unknown"))).thenReturn(Optional.empty());
        when(userRepository.findByUsername("路人")).thenReturn(Optional.empty());
        when(userRepository.findByNickname("路人")).thenReturn(List.of());

        DingTalkFreeLoginException ex = assertThrows(DingTalkFreeLoginException.class,
                () -> service.login("code-6", 9L));
        assertEquals(401, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("匹配"));
    }

    @Test
    void blankAuthCodeRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.login("  ", 9L));
        verify(dingTalkClient, never()).getUserByAuthCode(anyString());
    }

    @Test
    void nameAlreadyBoundToOtherDingUserFallsThroughToSampler() {
        when(dingTalkClient.getUserByAuthCode("code-7")).thenReturn(
                DingTalkAuthUser.builder().userid("u-new").name("张三").build());
        when(userRepository.findByDingtalkUserid("u-new")).thenReturn(Optional.empty());
        when(orgDirectory.findByDingUserId("宝娜斯集团", "u-new")).thenReturn(Optional.of(
                OrgContact.builder().id("org-n").dingUserId("u-new").name("张三").active(true).build()));
        User other = User.builder().id("id-other").username("张三").dingtalkUserid("u-old").role("user").build();
        when(userRepository.findByUsername("张三")).thenReturn(Optional.of(other));
        when(userRepository.findByNickname("张三")).thenReturn(List.of(other));

        LoginResponse response = service.login("code-7", 12L);

        assertEquals("sess-sampler", response.getSessionId());
        verify(authService).issueSamplerSession("u-new", "张三", "宝娜斯集团", 12L);
        verify(userRepository, never()).save(other);
    }

    @Test
    void loginWarnsWhenCorpIdBlankButStillExchangesAuthCode() {
        Logger logger = (Logger) LoggerFactory.getLogger(DingTalkFreeLoginService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(dingTalkClient.getUserByAuthCode("code-1")).thenReturn(
                    DingTalkAuthUser.builder().userid("u-zhang").name("张三").build());
            User user = User.builder().id("id-1").username("张三").dingtalkUserid("u-zhang").role("user").build();
            when(userRepository.findByDingtalkUserid("u-zhang")).thenReturn(Optional.of(user));

            assertTrue(properties.getCorpId() == null || properties.getCorpId().isBlank());
            LoginResponse response = service.login("code-1", 9L);

            assertEquals("sess-full", response.getSessionId());
            verify(dingTalkClient).getUserByAuthCode("code-1");
            assertTrue(appender.list.stream().anyMatch(e ->
                    e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("DINGTALK_CORP_ID")
                            && e.getFormattedMessage().contains("40078")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void loginDoesNotWarnWhenCorpIdConfigured() {
        properties.setCorpId("dingcorp");
        Logger logger = (Logger) LoggerFactory.getLogger(DingTalkFreeLoginService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(dingTalkClient.getUserByAuthCode("code-1")).thenReturn(
                    DingTalkAuthUser.builder().userid("u-zhang").name("张三").build());
            User user = User.builder().id("id-1").username("张三").dingtalkUserid("u-zhang").role("user").build();
            when(userRepository.findByDingtalkUserid("u-zhang")).thenReturn(Optional.of(user));

            service.login("code-1", 9L);

            assertTrue(appender.list.stream().noneMatch(e ->
                    e.getLevel() == Level.WARN
                            && e.getFormattedMessage().contains("DINGTALK_CORP_ID")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void loginByUseridReusesFullSessionRulesWithoutAuthCode() {
        User user = User.builder().id("id-1").username("张三").dingtalkUserid("u-zhang").role("user").build();
        when(userRepository.findByDingtalkUserid("u-zhang")).thenReturn(Optional.of(user));

        LoginResponse response = service.loginByUserid("u-zhang", 9L);

        assertEquals("sess-full", response.getSessionId());
        verify(dingTalkClient, never()).getUserByAuthCode(anyString());
        verify(authService).issueSession(user, false, true);
    }

    @Test
    void loginByUseridOrgOnlyIssuesSamplerSession() {
        when(userRepository.findByDingtalkUserid("u-wang")).thenReturn(Optional.empty());
        when(orgDirectory.findByDingUserId("宝娜斯集团", "u-wang")).thenReturn(Optional.of(
                OrgContact.builder().id("org-w").dingUserId("u-wang").name("王五").active(true).build()));
        when(userRepository.findByUsername("王五")).thenReturn(Optional.empty());
        when(userRepository.findByNickname("王五")).thenReturn(List.of());

        LoginResponse response = service.loginByUserid("u-wang", 9L);

        assertEquals("sess-sampler", response.getSessionId());
        verify(authService).issueSamplerSession("u-wang", "王五", "宝娜斯集团", 9L);
        verify(dingTalkClient, never()).getUserByAuthCode(anyString());
    }

    @Test
    void loginByUseridUnmatchedDoesNotEscalate() {
        when(userRepository.findByDingtalkUserid("u-unknown")).thenReturn(Optional.empty());
        when(orgDirectory.findByDingUserId(eq("宝娜斯集团"), eq("u-unknown"))).thenReturn(Optional.empty());
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByNickname(anyString())).thenReturn(List.of());

        DingTalkFreeLoginException ex = assertThrows(DingTalkFreeLoginException.class,
                () -> service.loginByUserid("u-unknown", 9L));
        assertEquals(401, ex.getHttpStatus());
        verify(authService, never()).issueSession(any(), anyBoolean(), anyBoolean());
        verify(authService, never()).issueSamplerSession(anyString(), anyString(), anyString(), anyLong());
    }
}
