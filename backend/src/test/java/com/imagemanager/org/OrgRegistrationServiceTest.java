package com.imagemanager.org;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dto.RegisterRequest;
import com.imagemanager.entity.User;
import com.imagemanager.exception.RegisterMatchException;
import com.imagemanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrgRegistrationServiceTest {

    private OrgDirectory directory;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private OrgRegistrationService service;

    @BeforeEach
    void setUp() {
        directory = mock(OrgDirectory.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        DingTalkProperties properties = new DingTalkProperties();
        properties.setCompany("宝娜斯集团");
        PlatformTransactionManager txManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "ENC:" + inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByDingtalkUserid(anyString())).thenReturn(Optional.empty());
        when(directory.countActiveContacts("宝娜斯集团")).thenReturn(3);
        when(directory.findLocalDeptId(anyString(), any())).thenReturn(Optional.of("dept-1"));

        service = new OrgRegistrationService(directory, userRepository, passwordEncoder, properties, txManager);
    }

    @Test
    void zeroMatchesReturns404() {
        when(directory.findActiveByName("宝娜斯集团", "张三")).thenReturn(List.of());
        RegisterRequest req = new RegisterRequest();
        req.setName("张三");
        RegisterMatchException ex = assertThrows(RegisterMatchException.class, () -> service.register(req));
        assertEquals(404, ex.getHttpStatus());
    }

    @Test
    void notSyncedReturns404() {
        when(directory.countActiveContacts("宝娜斯集团")).thenReturn(0);
        RegisterRequest req = new RegisterRequest();
        req.setName("张三");
        RegisterMatchException ex = assertThrows(RegisterMatchException.class, () -> service.register(req));
        assertEquals(404, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("同步"));
    }

    @Test
    void uniqueMatchCreatesUserWithDefaultPasswordAndMustChange() {
        OrgContact contact = sample("u1", "张三", null);
        when(directory.findActiveByName("宝娜斯集团", "张三")).thenReturn(List.of(contact));

        RegisterRequest req = new RegisterRequest();
        req.setName("张三");
        User user = service.register(req);

        assertEquals("张三", user.getUsername());
        assertEquals("张三", user.getNickname());
        assertEquals("总监", user.getJobTitle());
        assertEquals("u1", user.getDingtalkUserid());
        assertEquals(Boolean.TRUE, user.getMustChangePassword());
        assertEquals("ENC:123456", user.getPassword());
        assertTrue(user.getEmail() == null || user.getEmail().contains("dingtalk.invalid")
                || user.getEmail().contains("@"));
        verify(passwordEncoder).encode(OrgRegistrationService.DEFAULT_PASSWORD);
        verify(directory).bindLocalUser("org-1", user.getId());
        assertEquals("user", user.getRole());
    }

    @Test
    void emailIsNotRequiredUsesPlaceholder() {
        OrgContact contact = sample("u2", "王五", null);
        contact.setEmail(null);
        when(directory.findActiveByName("宝娜斯集团", "王五")).thenReturn(List.of(contact));
        RegisterRequest req = new RegisterRequest();
        req.setName("王五");
        User user = service.register(req);
        assertTrue(user.getEmail().startsWith("dt-"));
        assertTrue(user.getEmail().endsWith("@dingtalk.invalid"));
    }

    @Test
    void multipleMatchesReturn409Candidates() {
        when(directory.findActiveByName("宝娜斯集团", "张三")).thenReturn(List.of(
                sample("u1", "张三", "技术部"),
                sample("u2", "张三", "财务部")));
        RegisterRequest req = new RegisterRequest();
        req.setName("张三");
        RegisterMatchException ex = assertThrows(RegisterMatchException.class, () -> service.register(req));
        assertEquals(409, ex.getHttpStatus());
        assertEquals(2, ex.getCandidates().size());
    }

    @Test
    void pickCandidateByDingtalkUserid() {
        OrgContact contact = sample("u2", "张三", "财务部");
        when(directory.findByDingUserId("宝娜斯集团", "u2")).thenReturn(Optional.of(contact));
        RegisterRequest req = new RegisterRequest();
        req.setName("张三");
        req.setDingtalkUserid("u2");
        User user = service.register(req);
        assertEquals("u2", user.getDingtalkUserid());
        assertEquals("张三", user.getNickname());
        assertEquals(Boolean.TRUE, user.getMustChangePassword());
    }

    @Test
    void alreadyBoundContactRejected() {
        OrgContact contact = sample("u1", "张三", null);
        contact.setLocalUserId("existing");
        when(directory.findActiveByName("宝娜斯集团", "张三")).thenReturn(List.of(contact));
        RegisterRequest req = new RegisterRequest();
        req.setName("张三");
        RegisterMatchException ex = assertThrows(RegisterMatchException.class, () -> service.register(req));
        assertEquals(409, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("已注册"));
    }

    private static OrgContact sample(String dingUserId, String name, String dept) {
        return OrgContact.builder()
                .id("org-1")
                .dingUserId(dingUserId)
                .name(name)
                .jobTitle("总监")
                .deptName(dept)
                .deptPath(dept == null ? "/宝娜斯集团" : "/宝娜斯集团/" + dept)
                .primaryDingDeptId(2L)
                .active(true)
                .build();
    }
}
