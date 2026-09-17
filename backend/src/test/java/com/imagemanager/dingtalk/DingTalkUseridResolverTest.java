package com.imagemanager.dingtalk;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.entity.User;
import com.imagemanager.org.OrgContact;
import com.imagemanager.org.OrgDirectory;
import com.imagemanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DingTalkUseridResolverTest {

    private OrgDirectory orgDirectory;
    private UserRepository userRepository;
    private DingTalkUseridResolver resolver;

    @BeforeEach
    void setUp() {
        orgDirectory = mock(OrgDirectory.class);
        userRepository = mock(UserRepository.class);
        DingTalkProperties properties = new DingTalkProperties();
        properties.setCompany("宝娜斯集团");
        resolver = new DingTalkUseridResolver(orgDirectory, userRepository, properties);
    }

    @Test
    void prefersUniqueOrgUser() {
        when(orgDirectory.findActiveByName("宝娜斯集团", "张三"))
                .thenReturn(List.of(contact("u-org", "张三")));

        DingTalkUseridResolver.ResolveResult result = resolver.resolveByName("张三");

        assertTrue(result.isFound());
        assertEquals("u-org", result.getUserid());
        assertEquals("org_users", result.getSource());
        verify(userRepository, never()).findByDingtalkUseridIsNotNull();
    }

    @Test
    void fallsBackToLocalDingtalkUseridByUsernameOrNickname() {
        when(orgDirectory.findActiveByName("宝娜斯集团", "李四")).thenReturn(List.of());
        when(userRepository.findByDingtalkUseridIsNotNull()).thenReturn(List.of(
                User.builder().id("1").username("李 四").dingtalkUserid("u-local").build(),
                User.builder().id("2").username("王五").nickname("李四丰").dingtalkUserid("u-other").build()));

        DingTalkUseridResolver.ResolveResult result = resolver.resolveByName("李四");

        assertTrue(result.isFound());
        assertEquals("u-local", result.getUserid());
        assertEquals("users.dingtalk_userid", result.getSource());
    }

    @Test
    void matchesLocalNicknameWhenUsernameDiffers() {
        when(orgDirectory.findActiveByName("宝娜斯集团", "小李")).thenReturn(List.of());
        when(userRepository.findByDingtalkUseridIsNotNull()).thenReturn(List.of(
                User.builder().id("1").username("li").nickname("小李").dingtalkUserid("u-nick").build()));

        assertEquals("u-nick", resolver.resolveByName("小李").getUserid());
    }

    @Test
    void missingUseridIsNotFound() {
        when(orgDirectory.findActiveByName("宝娜斯集团", "不存在")).thenReturn(List.of());
        when(userRepository.findByDingtalkUseridIsNotNull()).thenReturn(List.of());

        DingTalkUseridResolver.ResolveResult result = resolver.resolveByName("不存在");

        assertFalse(result.isFound());
        assertEquals(DingTalkUseridResolver.ResolveResult.Status.MISSING, result.getStatus());
        assertNull(result.getUserid());
    }

    @Test
    void ambiguousOrgUsersSkipWithoutFallback() {
        when(orgDirectory.findActiveByName("宝娜斯集团", "张三")).thenReturn(List.of(
                contact("u-a", "张三"), contact("u-b", "张 三")));

        DingTalkUseridResolver.ResolveResult result = resolver.resolveByName("张三");

        assertEquals(DingTalkUseridResolver.ResolveResult.Status.AMBIGUOUS, result.getStatus());
        verify(userRepository, never()).findByDingtalkUseridIsNotNull();
    }

    @Test
    void blankNameIsBlank() {
        assertEquals(DingTalkUseridResolver.ResolveResult.Status.BLANK, resolver.resolveByName("  ").getStatus());
        assertEquals(DingTalkUseridResolver.ResolveResult.Status.BLANK, resolver.resolveByName(null).getStatus());
    }

    @Test
    void orgUserWithoutUseridFallsThroughToLocal() {
        when(orgDirectory.findActiveByName("宝娜斯集团", "赵六"))
                .thenReturn(List.of(contact("  ", "赵六")));
        when(userRepository.findByDingtalkUseridIsNotNull()).thenReturn(List.of(
                User.builder().id("1").username("赵六").dingtalkUserid("u-local").build()));

        assertEquals("u-local", resolver.resolveByName("赵六").getUserid());
    }

    private static OrgContact contact(String dingUserId, String name) {
        return OrgContact.builder().dingUserId(dingUserId).name(name).active(true).build();
    }
}
