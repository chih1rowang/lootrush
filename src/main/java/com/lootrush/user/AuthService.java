package com.lootrush.user;

import com.lootrush.common.BizException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** 注册与登录的核心逻辑 */
@Service
public class AuthService {

    /** 注册赠送的钻石数 */
    public static final int INIT_DIAMOND = 1000;

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    public UserEntity register(String username, String rawPassword) {
        if (userMapper.selectByUsername(username) != null) {
            throw BizException.of(409, "用户名已被占用");
        }
        UserEntity u = new UserEntity();
        u.setUsername(username);
        // BCrypt：随机盐 + 故意放慢的哈希。同一个密码每次加密结果都不同，
        // 数据库被拖走也无法反推出明文，这正是它优于 MD5 的地方。
        u.setPasswordHash(encoder.encode(rawPassword));
        u.setDiamond(INIT_DIAMOND);
        userMapper.insert(u);
        u.setPasswordHash(null); // 密码哈希永远不出服务层
        return u;
    }

    public String login(String username, String rawPassword) {
        UserEntity u = userMapper.selectByUsername(username);
        if (u == null || !encoder.matches(rawPassword, u.getPasswordHash())) {
            // "用户不存在"和"密码错误"对外是同一句话：不给撞库的人任何提示
            throw BizException.of(401, "用户名或密码错误");
        }
        return jwtUtil.issue(u.getId());
    }

    /** 实名认证：校验（姓名长度＋身份证校验位算法）后落库（落库前由插件自动加密） */
    public void realname(Long userId, String realName, String idCard) {
        if (realName == null || realName.length() < 2 || realName.length() > 20) {
            throw com.lootrush.common.BizException.of(400, "姓名需2-20位");
        }
        if (!com.lootrush.common.IdCardUtil.isValid(idCard)) {
            throw com.lootrush.common.BizException.of(400, "身份证号校验位不合法");
        }
        UserEntity u = new UserEntity();
        u.setId(userId);
        u.setRealName(realName);
        u.setIdCard(idCard);
        userMapper.updateRealName(u);
    }

    /** 脱敏后的实名信息（对外展示用：库里密文→内存明文→出参遮罩） */
    public record RealNameView(String realNameMasked, String idCardMasked) {
    }

    public RealNameView realnameView(Long userId) {
        UserEntity u = userMapper.selectById(userId);
        if (u == null || u.getIdCard() == null) {
            return new RealNameView(null, null);
        }
        return new RealNameView(
                com.lootrush.common.MaskUtil.maskName(u.getRealName()),
                com.lootrush.common.MaskUtil.maskIdCard(u.getIdCard()));
    }
}
