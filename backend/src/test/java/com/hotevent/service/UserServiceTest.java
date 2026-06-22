package com.hotevent.service;

import com.hotevent.dto.PasswordUpdateRequest;
import com.hotevent.dto.ProfileUpdateRequest;
import com.hotevent.dto.UserCreateRequest;
import com.hotevent.dto.UserUpdateRequest;
import com.hotevent.dto.UserVO;
import com.hotevent.entity.Role;
import com.hotevent.entity.User;
import com.hotevent.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务测试 - 登录错误统计")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SysConfigService sysConfigService;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private User lockedUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("encodedPassword123");
        testUser.setNickname("测试用户");
        testUser.setEmail("test@example.com");
        testUser.setRole(Role.USER);
        testUser.setEnabled(true);
        testUser.setLoginFailCount(0);
        testUser.setLastLoginFailTime(null);
        testUser.setLockTime(null);
        testUser.setDeleted(false);
        testUser.setCreateTime(LocalDateTime.now().minusDays(30));
        testUser.setUpdateTime(LocalDateTime.now());

        lockedUser = new User();
        lockedUser.setId(2L);
        lockedUser.setUsername("lockeduser");
        lockedUser.setPassword("encodedPassword456");
        lockedUser.setRole(Role.USER);
        lockedUser.setEnabled(true);
        lockedUser.setLoginFailCount(5);
        lockedUser.setLastLoginFailTime(LocalDateTime.now().minusMinutes(5));
        lockedUser.setLockTime(LocalDateTime.now().plusMinutes(25));
        lockedUser.setDeleted(false);
    }

    @Nested
    @DisplayName("登录失败记录")
    class RecordLoginFailureTests {

        @Test
        @DisplayName("正常场景 - 首次登录失败，计数置为1")
        void testRecordLoginFailure_FirstFailure() {
            when(userRepository.findByUsername(eq("testuser"))).thenReturn(Optional.of(testUser));
            when(sysConfigService.getLoginAttemptWindowMinutes()).thenReturn(30);
            when(sysConfigService.getMaxLoginAttempts()).thenReturn(5);
            when(sysConfigService.getLoginLockMinutes()).thenReturn(30);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userService.recordLoginFailure("testuser");

            assertEquals(1, testUser.getLoginFailCount());
            assertNotNull(testUser.getLastLoginFailTime());
            assertNull(testUser.getLockTime());
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("正常场景 - 连续登录失败，计数累加")
        void testRecordLoginFailure_ConsecutiveFailures() {
            testUser.setLoginFailCount(2);
            testUser.setLastLoginFailTime(LocalDateTime.now().minusMinutes(5));
            when(userRepository.findByUsername(eq("testuser"))).thenReturn(Optional.of(testUser));
            when(sysConfigService.getLoginAttemptWindowMinutes()).thenReturn(30);
            when(sysConfigService.getMaxLoginAttempts()).thenReturn(5);
            when(sysConfigService.getLoginLockMinutes()).thenReturn(30);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userService.recordLoginFailure("testuser");

            assertEquals(3, testUser.getLoginFailCount());
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("正常场景 - 达到最大尝试次数，账号被锁定")
        void testRecordLoginFailure_AccountLocked() {
            testUser.setLoginFailCount(4);
            testUser.setLastLoginFailTime(LocalDateTime.now().minusMinutes(2));
            when(userRepository.findByUsername(eq("testuser"))).thenReturn(Optional.of(testUser));
            when(sysConfigService.getLoginAttemptWindowMinutes()).thenReturn(30);
            when(sysConfigService.getMaxLoginAttempts()).thenReturn(5);
            when(sysConfigService.getLoginLockMinutes()).thenReturn(30);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userService.recordLoginFailure("testuser");

            assertEquals(5, testUser.getLoginFailCount());
            assertNotNull(testUser.getLockTime());
            assertTrue(testUser.getLockTime().isAfter(LocalDateTime.now()));
            assertTrue(testUser.isLocked());
        }

        @Test
        @DisplayName("边界条件 - 超出时间窗口后重置计数")
        void testRecordLoginFailure_OutsideWindow() {
            testUser.setLoginFailCount(4);
            testUser.setLastLoginFailTime(LocalDateTime.now().minusMinutes(45));
            when(userRepository.findByUsername(eq("testuser"))).thenReturn(Optional.of(testUser));
            when(sysConfigService.getLoginAttemptWindowMinutes()).thenReturn(30);
            when(sysConfigService.getMaxLoginAttempts()).thenReturn(5);
            when(sysConfigService.getLoginLockMinutes()).thenReturn(30);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userService.recordLoginFailure("testuser");

            assertEquals(1, testUser.getLoginFailCount());
        }

        @Test
        @DisplayName("边界条件 - 用户不存在时静默返回")
        void testRecordLoginFailure_UserNotFound() {
            when(userRepository.findByUsername(eq("nonexistent"))).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> {
                userService.recordLoginFailure("nonexistent");
            });
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("边界条件 - 用户已删除时忽略")
        void testRecordLoginFailure_DeletedUser() {
            testUser.setDeleted(true);
            when(userRepository.findByUsername(eq("testuser"))).thenReturn(Optional.of(testUser));

            userService.recordLoginFailure("testuser");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("边界条件 - 登录失败次数为null时初始化为1")
        void testRecordLoginFailure_NullFailCount() {
            testUser.setLoginFailCount(null);
            when(userRepository.findByUsername(eq("testuser"))).thenReturn(Optional.of(testUser));
            when(sysConfigService.getLoginAttemptWindowMinutes()).thenReturn(30);
            when(sysConfigService.getMaxLoginAttempts()).thenReturn(5);
            when(sysConfigService.getLoginLockMinutes()).thenReturn(30);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userService.recordLoginFailure("testuser");

            assertEquals(1, testUser.getLoginFailCount());
        }

        @Test
        @DisplayName("异常情况 - 数据库保存异常")
        void testRecordLoginFailure_DatabaseException() {
            when(userRepository.findByUsername(eq("testuser"))).thenReturn(Optional.of(testUser));
            when(sysConfigService.getLoginAttemptWindowMinutes()).thenReturn(30);
            when(sysConfigService.getMaxLoginAttempts()).thenReturn(5);
            when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("数据库写入失败"));

            assertThrows(RuntimeException.class, () -> {
                userService.recordLoginFailure("testuser");
            });
        }
    }

    @Nested
    @DisplayName("登录失败重置")
    class ResetLoginFailuresTests {

        @Test
        @DisplayName("正常场景 - 重置用户登录失败状态")
        void testResetLoginFailures_Success() {
            testUser.setLoginFailCount(5);
            testUser.setLastLoginFailTime(LocalDateTime.now());
            testUser.setLockTime(LocalDateTime.now().plusMinutes(30));
            when(userRepository.findById(eq(1L))).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userService.resetLoginFailures(1L);

            assertEquals(0, testUser.getLoginFailCount());
            assertNull(testUser.getLastLoginFailTime());
            assertNull(testUser.getLockTime());
            assertFalse(testUser.isLocked());
        }

        @Test
        @DisplayName("边界条件 - 用户不存在时抛出异常")
        void testResetLoginFailures_UserNotFound() {
            when(userRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> {
                userService.resetLoginFailures(999L);
            });
        }

        @Test
        @DisplayName("正常场景 - 登录成功时重置失败状态")
        void testResetLoginFailuresOnSuccess_Success() {
            testUser.setLoginFailCount(3);
            testUser.setLastLoginFailTime(LocalDateTime.now().minusMinutes(10));
            when(userRepository.findByUsername(eq("testuser"))).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            userService.resetLoginFailuresOnSuccess("testuser");

            assertEquals(0, testUser.getLoginFailCount());
            assertNull(testUser.getLastLoginFailTime());
            assertNull(testUser.getLockTime());
        }

        @Test
        @DisplayName("边界条件 - 用户不存在时静默返回")
        void testResetLoginFailuresOnSuccess_UserNotFound() {
            when(userRepository.findByUsername(eq("nonexistent"))).thenReturn(Optional.empty());

            assertDoesNotThrow(() -> {
                userService.resetLoginFailuresOnSuccess("nonexistent");
            });
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("边界条件 - 用户失败次数为0时不保存")
        void testResetLoginFailuresOnSuccess_AlreadyZero() {
            testUser.setLoginFailCount(0);
            when(userRepository.findByUsername(eq("testuser"))).thenReturn(Optional.of(testUser));

            userService.resetLoginFailuresOnSuccess("testuser");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("边界条件 - 用户失败次数为null时不保存")
        void testResetLoginFailuresOnSuccess_NullFailCount() {
            testUser.setLoginFailCount(null);
            when(userRepository.findByUsername(eq("testuser"))).thenReturn(Optional.of(testUser));

            userService.resetLoginFailuresOnSuccess("testuser");

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("用户解锁")
    class UnlockUserTests {

        @Test
        @DisplayName("正常场景 - 解锁被锁定的用户")
        void testUnlockUser_Success() {
            lockedUser.setLoginFailCount(5);
            lockedUser.setLockTime(LocalDateTime.now().plusMinutes(30));
            when(userRepository.findById(eq(2L))).thenReturn(Optional.of(lockedUser));
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserVO result = userService.unlockUser(2L);

            assertNotNull(result);
            assertEquals(0, result.getLoginFailCount());
            assertFalse(result.isLocked());
            assertNull(result.getLockTime());
        }

        @Test
        @DisplayName("边界条件 - 用户不存在时抛出异常")
        void testUnlockUser_UserNotFound() {
            when(userRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> {
                userService.unlockUser(999L);
            });
        }
    }

    @Nested
    @DisplayName("账号锁定状态检查")
    class AccountLockStatusTests {

        @Test
        @DisplayName("正常场景 - 未锁定账号返回未锁定")
        void testIsAccountNonLocked_NotLocked() {
            assertTrue(testUser.isAccountNonLocked());
            assertFalse(testUser.isLocked());
        }

        @Test
        @DisplayName("正常场景 - 锁定中账号返回已锁定")
        void testIsAccountNonLocked_Locked() {
            testUser.setLockTime(LocalDateTime.now().plusMinutes(30));
            assertFalse(testUser.isAccountNonLocked());
            assertTrue(testUser.isLocked());
        }

        @Test
        @DisplayName("边界条件 - 锁定时间已过自动解锁")
        void testIsAccountNonLocked_LockExpired() {
            testUser.setLockTime(LocalDateTime.now().minusMinutes(5));
            assertTrue(testUser.isAccountNonLocked());
            assertFalse(testUser.isLocked());
        }

        @Test
        @DisplayName("边界条件 - lockTime为null时未锁定")
        void testIsAccountNonLocked_NullLockTime() {
            testUser.setLockTime(null);
            assertTrue(testUser.isAccountNonLocked());
        }
    }

    @Nested
    @DisplayName("用户创建")
    class CreateUserTests {

        @Test
        @DisplayName("正常场景 - 创建用户成功")
        void testCreateUser_Success() {
            UserCreateRequest request = new UserCreateRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setNickname("新用户");
            request.setEmail("new@example.com");

            when(userRepository.existsByUsernameAndDeletedFalse(eq("newuser"))).thenReturn(false);
            when(passwordEncoder.encode(eq("password123"))).thenReturn("encodedNewPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(100L);
                saved.setCreateTime(LocalDateTime.now());
                return saved;
            });

            UserVO result = userService.create(request);

            assertNotNull(result);
            assertEquals("newuser", result.getUsername());
            assertEquals("新用户", result.getNickname());
            assertEquals(0, result.getLoginFailCount());
        }

        @Test
        @DisplayName("边界条件 - 用户名已存在")
        void testCreateUser_DuplicateUsername() {
            UserCreateRequest request = new UserCreateRequest();
            request.setUsername("existing");
            request.setPassword("password123");

            when(userRepository.existsByUsernameAndDeletedFalse(eq("existing"))).thenReturn(true);

            assertThrows(RuntimeException.class, () -> {
                userService.create(request);
            });
        }

        @Test
        @DisplayName("边界条件 - 角色为null时默认使用USER角色")
        void testCreateUser_NullRole() {
            UserCreateRequest request = new UserCreateRequest();
            request.setUsername("newuser");
            request.setPassword("password123");
            request.setRole(null);

            when(userRepository.existsByUsernameAndDeletedFalse(eq("newuser"))).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(100L);
                return saved;
            });

            UserVO result = userService.create(request);

            assertNotNull(result);
            assertEquals(Role.USER, result.getRole());
        }
    }

    @Nested
    @DisplayName("密码更新")
    class UpdatePasswordTests {

        @Test
        @DisplayName("正常场景 - 更新密码成功")
        void testUpdatePassword_Success() {
            PasswordUpdateRequest request = new PasswordUpdateRequest();
            request.setOldPassword("oldPassword");
            request.setNewPassword("newPassword123");
            request.setConfirmPassword("newPassword123");

            when(userRepository.findById(eq(1L))).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(eq("oldPassword"), eq("encodedPassword123"))).thenReturn(true);
            when(passwordEncoder.encode(eq("newPassword123"))).thenReturn("encodedNewPassword");
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            assertDoesNotThrow(() -> {
                userService.updatePassword(1L, request);
            });
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("异常情况 - 原密码错误")
        void testUpdatePassword_WrongOldPassword() {
            PasswordUpdateRequest request = new PasswordUpdateRequest();
            request.setOldPassword("wrongPassword");
            request.setNewPassword("newPassword123");
            request.setConfirmPassword("newPassword123");

            when(userRepository.findById(eq(1L))).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(eq("wrongPassword"), eq("encodedPassword123"))).thenReturn(false);

            assertThrows(RuntimeException.class, () -> {
                userService.updatePassword(1L, request);
            });
        }

        @Test
        @DisplayName("边界条件 - 两次新密码不一致")
        void testUpdatePassword_PasswordMismatch() {
            PasswordUpdateRequest request = new PasswordUpdateRequest();
            request.setOldPassword("oldPassword");
            request.setNewPassword("newPassword123");
            request.setConfirmPassword("differentPassword");

            when(userRepository.findById(eq(1L))).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(eq("oldPassword"), eq("encodedPassword123"))).thenReturn(true);

            assertThrows(RuntimeException.class, () -> {
                userService.updatePassword(1L, request);
            });
        }

        @Test
        @DisplayName("边界条件 - 用户不存在")
        void testUpdatePassword_UserNotFound() {
            PasswordUpdateRequest request = new PasswordUpdateRequest();
            request.setOldPassword("oldPassword");
            request.setNewPassword("newPassword123");
            request.setConfirmPassword("newPassword123");

            when(userRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> {
                userService.updatePassword(999L, request);
            });
        }
    }

    @Nested
    @DisplayName("用户查询")
    class UserQueryTests {

        @Test
        @DisplayName("正常场景 - 根据ID查询用户")
        void testGetById_Success() {
            when(userRepository.findById(eq(1L))).thenReturn(Optional.of(testUser));

            UserVO result = userService.getById(1L);

            assertNotNull(result);
            assertEquals("testuser", result.getUsername());
        }

        @Test
        @DisplayName("边界条件 - 用户不存在时抛出异常")
        void testGetById_NotFound() {
            when(userRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> {
                userService.getById(999L);
            });
        }

        @Test
        @DisplayName("边界条件 - 用户已删除时抛出异常")
        void testGetById_DeletedUser() {
            testUser.setDeleted(true);
            when(userRepository.findById(eq(1L))).thenReturn(Optional.of(testUser));

            assertThrows(RuntimeException.class, () -> {
                userService.getById(1L);
            });
        }

        @Test
        @DisplayName("正常场景 - 根据用户名查询用户")
        void testGetByUsername_Success() {
            when(userRepository.findByUsernameAndDeletedFalse(eq("testuser"))).thenReturn(Optional.of(testUser));

            UserVO result = userService.getByUsername("testuser");

            assertNotNull(result);
            assertEquals("testuser", result.getUsername());
        }

        @Test
        @DisplayName("正常场景 - 查询所有用户列表")
        void testListAll_Success() {
            List<User> users = Arrays.asList(testUser, lockedUser);
            when(userRepository.findByDeletedFalse()).thenReturn(users);

            List<UserVO> result = userService.listAll();

            assertNotNull(result);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("边界条件 - 空用户列表")
        void testListAll_Empty() {
            when(userRepository.findByDeletedFalse()).thenReturn(Collections.emptyList());

            List<UserVO> result = userService.listAll();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("正常场景 - 分页查询用户")
        void testPage_Success() {
            Page<User> userPage = new PageImpl<>(Arrays.asList(testUser, lockedUser), PageRequest.of(0, 10), 2);
            when(userRepository.findByDeletedFalse(any(PageRequest.class))).thenReturn(userPage);

            Page<UserVO> result = userService.page(PageRequest.of(0, 10));

            assertNotNull(result);
            assertEquals(2, result.getTotalElements());
            assertEquals(2, result.getContent().size());
        }
    }

    @Nested
    @DisplayName("用户删除")
    class DeleteUserTests {

        @Test
        @DisplayName("正常场景 - 删除普通用户成功")
        void testDeleteUser_Success() {
            when(userRepository.findById(eq(1L))).thenReturn(Optional.of(testUser));
            when(userRepository.save(any(User.class))).thenReturn(testUser);

            assertDoesNotThrow(() -> {
                userService.delete(1L);
            });
            assertTrue(testUser.getDeleted());
        }

        @Test
        @DisplayName("边界条件 - 不能删除超级管理员")
        void testDeleteUser_AdminUser() {
            testUser.setUsername("admin");
            when(userRepository.findById(eq(1L))).thenReturn(Optional.of(testUser));

            assertThrows(RuntimeException.class, () -> {
                userService.delete(1L);
            });
        }

        @Test
        @DisplayName("边界条件 - 用户不存在时抛出异常")
        void testDeleteUser_NotFound() {
            when(userRepository.findById(eq(999L))).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> {
                userService.delete(999L);
            });
        }
    }
}
