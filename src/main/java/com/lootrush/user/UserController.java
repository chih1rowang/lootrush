package com.lootrush.user;

import com.lootrush.common.ApiResponse;
import com.lootrush.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 需要登录才能访问的接口（JwtInterceptor 验完通行证后把 userId 放进请求属性） */
@RestController
@RequestMapping("/api")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserEntity u = userMapper.selectById(userId);
        if (u == null) {
            throw BizException.of(401, "账号不存在");
        }
        return ApiResponse.ok(Map.of(
                "userId", u.getId(),
                "username", u.getUsername(),
                "diamond", u.getDiamond()));
    }
}
