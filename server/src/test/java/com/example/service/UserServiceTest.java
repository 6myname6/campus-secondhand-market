package com.example.service;

import com.example.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserService 单元测试 — JUnit 5。
 *
 * 测试覆盖: 注册、登录、Token 校验、状态变更、分页查询。
 */
class UserServiceTest extends BaseTest {

    private final UserService userService = new UserService();
    private final AuthService authService = new AuthService();

    private String uid() { return "u_" + UUID.randomUUID().toString().substring(0, 8); }

    /**
     * TC-01: testRegisterAndLogin
     *
     * 测试流程:
     * 1. 注册新用户 → 验证返回有效 userId
     * 2. 用正确密码登录 → 验证返回的 User 对象信息正确
     * 3. 登录成功后生成 Token → 验证 Token 有效且可校验出 userId
     * 4. 用错误密码登录 → 验证抛出 RuntimeException
     */
    @Test
    void testRegisterAndLogin() {
        // 1. 注册
        String uname = uid();
        int userId = userService.register(uname, "pass123", "13811111111", "j@test.com");
        assertTrue(userId > 0, "注册应返回有效ID");

        // 2. 正确登录 — 校验用户信息
        User user = userService.login(uname, "pass123");
        assertNotNull(user, "登录应成功");
        assertEquals(uname, user.getUsername(), "用户名应匹配");
        assertEquals("13811111111", user.getPhone(), "手机号应匹配");
        assertEquals("j@test.com", user.getEmail(), "邮箱应匹配");

        // 3. 校验 Token 有效性
        String token = authService.login(user.getUserId());
        assertNotNull(token, "Token 不应为空");
        assertFalse(token.isEmpty(), "Token 不应为空字符串");
        Integer validatedUserId = authService.validate(token);
        assertNotNull(validatedUserId, "Token 校验应返回有效 userId");
        assertEquals(user.getUserId(), validatedUserId.intValue(), "Token 校验出的 userId 应与登录用户一致");

        // 4. 错误密码应被拒绝
        assertThrows(RuntimeException.class,
                () -> userService.login(uname, "wrong_pass"),
                "错误密码应抛异常");
    }

    /**
     * TC-02: testUpdateStatus
     *
     * 测试流程:
     * 1. 注册新用户 → 正常登录成功
     * 2. 禁用用户 (status=0) → 尝试登录被拒绝
     * 3. 启用用户 (status=1) → 登录恢复成功
     */
    @Test
    void testUpdateStatus() {
        String uname = uid();
        int userId = userService.register(uname, "pass456", null, null);

        // 1. 初始状态正常，可登录
        User user = userService.login(uname, "pass456");
        assertNotNull(user, "初始应可正常登录");
        assertEquals(1, user.getStatus(), "默认状态应为1(正常)");

        // 2. 禁用用户
        userService.updateStatus(userId, 0);
        // 禁用后登录应被拒绝
        assertThrows(RuntimeException.class,
                () -> userService.login(uname, "pass456"),
                "禁用后登录应被拒绝");

        // 3. 重新启用
        userService.updateStatus(userId, 1);
        // 启用后登录恢复
        User reEnabled = userService.login(uname, "pass456");
        assertNotNull(reEnabled, "启用后应可重新登录");
        assertEquals(1, reEnabled.getStatus(), "状态应为1(正常)");
    }

    /**
     * TC-03: testListUsers
     *
     * 测试流程:
     * 1. 注册至少3个用户
     * 2. 分页查询 (page=1, pageSize=2) → 验证返回 ≤ pageSize 条
     * 3. countUsers → 验证总数 ≥ 3
     */
    @Test
    void testListUsers() {
        // 批量注册
        userService.register(uid(), "pass", null, null);
        userService.register(uid(), "pass", null, null);
        userService.register(uid(), "pass", null, null);

        // 分页查询
        List<User> page = userService.listUsers(1, 2);
        assertTrue(page.size() <= 2, "分页最多返回2条");

        // 总数统计
        long count = userService.countUsers();
        assertTrue(count >= 3, "至少应有3个用户");
    }
}
