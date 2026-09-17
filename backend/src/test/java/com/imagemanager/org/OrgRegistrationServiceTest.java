package com.imagemanager.org;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dto.RegisterRequest;
import com.imagemanager.entity.User;
import com.imagemanager.exception.RegisterMatchException;
import com.imagemanager.repository.UserRepository;
import com.imagemanager.service.ImageTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrgRegistrationServiceTest {

    private OrgDirectory directory;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private DingTalkProperties properties;
    private PlatformTransactionManager txManager;
    private OrgRegistrationService service;

    @BeforeEach
    void setUp() {
        directory = mock(OrgDirectory.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        properties = new DingTalkProperties();
        properties.setCompany("宝娜斯集团");
        txManager = noopTxManager();
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "ENC:" + inv.getArgument(0));
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findByDingtalkUserid(anyString())).thenReturn(Optional.empty());
        when(directory.countActiveContacts("宝娜斯集团")).thenReturn(3);
        when(directory.findLocalDeptId(anyString(), any())).thenReturn(Optional.of("dept-1"));

        service = newService(null);
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
        InOrder inOrder = inOrder(userRepository, directory);
        inOrder.verify(userRepository).saveAndFlush(any(User.class));
        inOrder.verify(directory).bindLocalUser("org-1", user.getId());
    }

    @Test
    void saveAndFlushRunsBeforeBindSoJdbcSeesUserRow() {
        OrgContact contact = sample("u-libin", "李彬", null);
        when(directory.findActiveByName("宝娜斯集团", "李彬")).thenReturn(List.of(contact));
        RegisterRequest req = new RegisterRequest();
        req.setName("李彬");
        User user = service.register(req);

        assertEquals("李彬", user.getUsername());
        assertEquals(Boolean.TRUE, user.getMustChangePassword());
        assertEquals("ENC:123456", user.getPassword());
        InOrder inOrder = inOrder(userRepository, directory);
        inOrder.verify(userRepository).saveAndFlush(any(User.class));
        inOrder.verify(directory).bindLocalUser("org-1", user.getId());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void unboundOrphanUserIsReusedAndBoundOnRetry() {
        OrgContact contact = sample("u-libin", "李彬", null);
        User orphan = User.builder()
                .id("b7fa3d3d-orphan-user")
                .username("李彬")
                .password("ENC:123456")
                .email("dt-u-libin@dingtalk.invalid")
                .nickname("李彬")
                .role("user")
                .mustChangePassword(true)
                .dingtalkUserid("u-libin")
                .build();
        when(directory.findActiveByName("宝娜斯集团", "李彬")).thenReturn(List.of(contact));
        when(userRepository.findByDingtalkUserid("u-libin")).thenReturn(Optional.of(orphan));

        RegisterRequest req = new RegisterRequest();
        req.setName("李彬");
        User user = service.register(req);

        assertEquals("b7fa3d3d-orphan-user", user.getId());
        assertEquals("李彬", user.getUsername());
        assertEquals(Boolean.TRUE, user.getMustChangePassword());
        verify(directory).bindLocalUser("org-1", "b7fa3d3d-orphan-user");
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void searchLeavesUnboundOrphanSelectableForRetry() {
        OrgContact contact = sample("u-libin", "李彬", null);
        when(directory.searchContacts("宝娜斯集团", "李彬", 50)).thenReturn(List.of(contact));
        when(userRepository.findByDingtalkUserid("u-libin")).thenReturn(Optional.of(
                User.builder().id("orphan").username("李彬").dingtalkUserid("u-libin").build()));

        var candidates = service.searchContacts("宝娜斯集团", "李彬");

        assertEquals(1, candidates.size());
        assertEquals("李彬", candidates.get(0).getName());
        assertEquals(false, candidates.get(0).isAlreadyRegistered());
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
        assertEquals(RegisterMatchException.Kind.MULTIPLE, ex.getKind());
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
        assertEquals(RegisterMatchException.Kind.ALREADY_REGISTERED, ex.getKind());
        assertTrue(ex.getMessage().contains("已注册"));
    }

    @Test
    void ensureAccountUniqueMatchCreatesUserThenBinds() {
        OrgContact contact = sample("u1", "张三", null);
        when(directory.findActiveByName("宝娜斯集团", "张三")).thenReturn(List.of(contact));

        OrgRegistrationService.EnsureAccountResult result = service.ensureAccountByName("张三");

        assertEquals(OrgRegistrationService.EnsureAccountResult.Status.CREATED, result.getStatus());
        assertTrue(result.isCreated());
        assertEquals("张三", result.getUser().getUsername());
        assertEquals("u1", result.getUser().getDingtalkUserid());
        assertEquals(Boolean.TRUE, result.getUser().getMustChangePassword());
        assertEquals("ENC:123456", result.getUser().getPassword());
        verify(directory).bindLocalUser("org-1", result.getUser().getId());
        verify(userRepository).saveAndFlush(any(User.class));
    }

    @Test
    void ensureAccountAlreadyBoundDoesNotCreateDuplicate() {
        OrgContact contact = sample("u1", "张三", null);
        contact.setLocalUserId("existing");
        when(directory.findActiveByName("宝娜斯集团", "张三")).thenReturn(List.of(contact));

        OrgRegistrationService.EnsureAccountResult result = service.ensureAccountByName("张三");

        assertEquals(OrgRegistrationService.EnsureAccountResult.Status.ALREADY_EXISTS, result.getStatus());
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(directory, never()).bindLocalUser(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void ensureAccountReusesOrphanDingtalkUserWithoutDuplicate() {
        OrgContact contact = sample("u-libin", "李彬", null);
        User orphan = User.builder()
                .id("b7fa3d3d-orphan-user")
                .username("李彬")
                .password("ENC:123456")
                .email("dt-u-libin@dingtalk.invalid")
                .nickname("李彬")
                .role("user")
                .mustChangePassword(true)
                .dingtalkUserid("u-libin")
                .build();
        when(directory.findActiveByName("宝娜斯集团", "李彬")).thenReturn(List.of(contact));
        when(userRepository.findByDingtalkUserid("u-libin")).thenReturn(Optional.of(orphan));

        OrgRegistrationService.EnsureAccountResult result = service.ensureAccountByName("李彬");

        assertTrue(result.isCreated());
        assertEquals("b7fa3d3d-orphan-user", result.getUser().getId());
        verify(directory).bindLocalUser("org-1", "b7fa3d3d-orphan-user");
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void ensureAccountAmbiguousNameDoesNotCreate() {
        when(directory.findActiveByName("宝娜斯集团", "张三")).thenReturn(List.of(
                sample("u1", "张三", "技术部"),
                sample("u2", "张三", "财务部")));

        OrgRegistrationService.EnsureAccountResult result = service.ensureAccountByName("张三");

        assertEquals(OrgRegistrationService.EnsureAccountResult.Status.SKIPPED_AMBIGUOUS, result.getStatus());
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(directory, never()).bindLocalUser(anyString(), anyString());
    }

    @Test
    void ensureAccountMissingOrgContactDoesNotCreate() {
        when(directory.findActiveByName("宝娜斯集团", "王五")).thenReturn(List.of());

        OrgRegistrationService.EnsureAccountResult result = service.ensureAccountByName("王五");

        assertEquals(OrgRegistrationService.EnsureAccountResult.Status.SKIPPED_NO_MATCH, result.getStatus());
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(directory, never()).bindLocalUser(anyString(), anyString());
    }

    @Test
    void ensureAccountBlankNameDoesNotTouchDirectory() {
        OrgRegistrationService.EnsureAccountResult result = service.ensureAccountByName("  ");
        assertEquals(OrgRegistrationService.EnsureAccountResult.Status.SKIPPED_BLANK, result.getStatus());
        verify(directory, never()).findActiveByName(anyString(), anyString());
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void ensureAccountCreatesUserImageTableAfterCommit() {
        ImageTableService imageTableService = mock(ImageTableService.class);
        when(imageTableService.ensureUserImageTable(anyString())).thenReturn(true);
        OrgRegistrationService withTable = newService(imageTableService);
        OrgContact contact = sample("u1", "张三", null);
        when(directory.findActiveByName("宝娜斯集团", "张三")).thenReturn(List.of(contact));

        OrgRegistrationService.EnsureAccountResult result = withTable.ensureAccountByName("张三");

        assertTrue(result.isCreated());
        verify(imageTableService).ensureUserImageTable("张三");
    }

    @Test
    void ensureAccountImageTableFailureStillReturnsCreated() {
        ImageTableService imageTableService = mock(ImageTableService.class);
        when(imageTableService.ensureUserImageTable(anyString())).thenThrow(new RuntimeException("ddl failed"));
        OrgRegistrationService withTable = newService(imageTableService);
        OrgContact contact = sample("u1", "张三", null);
        when(directory.findActiveByName("宝娜斯集团", "张三")).thenReturn(List.of(contact));

        OrgRegistrationService.EnsureAccountResult result = withTable.ensureAccountByName("张三");

        assertTrue(result.isCreated());
        assertEquals("张三", result.getUser().getUsername());
    }

    private OrgRegistrationService newService(ImageTableService imageTableService) {
        return new OrgRegistrationService(directory, userRepository, passwordEncoder, properties, txManager,
                imageTableService);
    }

    private static PlatformTransactionManager noopTxManager() {
        return new PlatformTransactionManager() {
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
