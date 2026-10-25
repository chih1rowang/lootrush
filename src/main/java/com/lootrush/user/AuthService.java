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
}
