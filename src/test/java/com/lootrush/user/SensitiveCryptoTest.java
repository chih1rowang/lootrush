package com.lootrush.user;

import com.lootrush.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 循环9C 测试：敏感字段透明加解密。
 * 核心断言是"库是密文、内存是明文"——用 JdbcTemplate 裸查绕开插件看真实存储。
 */
@SpringBootTest
@Transactional
class SensitiveCryptoTest {

    @Autowired AuthService authService;
    @Autowired UserMapper userMapper;
    @Autowired JdbcTemplate jdbc;

    private UserEntity newUser() {
        return authService.register("crypto" + System.currentTimeMillis() % 1000000, "pass123");
    }

    @Test
    void realname_stores_ciphertext_and_reads_back_plaintext() {
        UserEntity u = newUser();
        String name = "张三";
        String idCard = "310110199001011230"; // 末位校验码=0，算法验证合法

        authService.realname(u.getId(), name, idCard);

        // 裸查（绕开MyBatis插件）：库里必须是密文
        String raw = jdbc.queryForObject(
                "SELECT id_card FROM user WHERE id = ?", String.class, u.getId());
        assertNotNull2(raw);
        assertTrue(raw.startsWith("enc:v1:"), "库里必须是AES-GCM密文（enc:v1:前缀），实际=" + raw);
        assertNotEquals(idCard, raw, "库里绝不能有明文身份证");
        String rawName = jdbc.queryForObject(
                "SELECT real_name FROM user WHERE id = ?", String.class, u.getId());
        assertTrue(rawName.startsWith("enc:v1:"), "姓名同样是密文");

        // 读回：业务层拿到的永远是明文（插件自动解密）
        UserEntity read = userMapper.selectById(u.getId());
        assertEquals(name, read.getRealName(), "读回应为明文姓名");
        assertEquals(idCard, read.getIdCard(), "读回应为明文身份证");
    }

    private void assertNotNull2(String raw) {
        org.junit.jupiter.api.Assertions.assertNotNull(raw);
    }

    @Test
    void read_twice_does_not_double_decrypt() {
        UserEntity u = newUser();
        authService.realname(u.getId(), "李四", "310110199001011230");
        assertEquals("李四", userMapper.selectById(u.getId()).getRealName());
        assertEquals("李四", userMapper.selectById(u.getId()).getRealName(),
                "第二次读取不得把明文再'解密'一遍（前缀守卫）");
    }

    @Test
    void invalid_checksum_idcard_rejected() {
        UserEntity u = newUser();
        String badId = "310110199001011234"; // 末位应为0，校验码错误
        assertThrows(BizException.class, () -> authService.realname(u.getId(), "王五", badId),
                "校验位不合法的证号必须被拒");
        String raw = jdbc.queryForObject(
                "SELECT id_card FROM user WHERE id = ?", String.class, u.getId());
        assertEquals(null, raw, "被拒的实名不得留下任何落库痕迹");
    }

    @Test
    void masked_views_hide_middle_digits() {
        assertEquals("310******1230", com.lootrush.common.MaskUtil.maskIdCard("310110199001011230"));
        assertEquals("张*", com.lootrush.common.MaskUtil.maskName("张三"));
        assertEquals("王**", com.lootrush.common.MaskUtil.maskName("王小红"));
    }
}
