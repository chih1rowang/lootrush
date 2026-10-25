package com.lootrush.user;

import com.lootrush.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 循环1 自动化测试：注册登录。
 * @Transactional 让每个测试结束时自动回滚，数据库不留测试脏数据——
 * 以后任何改动，跑一遍 mvnw test 就知道有没有改坏这里。
 */
@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired AuthService authService;
    @Autowired UserMapper userMapper;

    @Test
    void register_then_login_success() {
        UserEntity u = authService.register("tester1", "pass123");
        assertNotNull(u.getId(), "注册后应生成用户ID");
        assertEquals(AuthService.INIT_DIAMOND, u.getDiamond(), "注册应赠送初始钻石");

        String token = authService.login("tester1", "pass123");
        assertNotNull(token, "登录应签发JWT通行证");
    }

    @Test
    void duplicate_username_rejected() {
        authService.register("tester2", "pass123");
        assertThrows(BizException.class, () -> authService.register("tester2", "any123"),
                "重复用户名必须被拒绝");
    }

    @Test
    void wrong_password_rejected() {
        authService.register("tester3", "pass123");
        assertThrows(BizException.class, () -> authService.login("tester3", "wrong1"),
                "错误密码必须被拒绝");
    }

    @Test
    void password_stored_as_bcrypt_hash_not_plaintext() {
        authService.register("tester4", "pass123");
        String hash = userMapper.selectByUsername("tester4").getPasswordHash();
        assertNotEquals("pass123", hash, "库里绝不能有明文密码");
        assertTrue(hash.startsWith("$2"), "BCrypt结果固定以$2开头，实际: " + hash);
    }
}
