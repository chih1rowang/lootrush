package com.lootrush.user;

import com.lootrush.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 注册登录接口（无需通行证即可访问） */
@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 请求体：用户名 + 明文密码（HTTPS 环境下传输，入库前立刻变成哈希） */
    public record AuthReq(
            @NotBlank(message = "用户名不能为空")
            @Size(min = 3, max = 20, message = "用户名需3-20位") String username,
            @NotBlank(message = "密码不能为空")
            @Size(min = 6, max = 32, message = "密码需6-32位") String password) {
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Validated @RequestBody AuthReq req) {
        UserEntity u = authService.register(req.username(), req.password());
        return ApiResponse.ok(Map.of(
                "userId", u.getId(),
                "username", u.getUsername(),
                "diamond", u.getDiamond()));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@Validated @RequestBody AuthReq req) {
        return ApiResponse.ok(Map.of("token", authService.login(req.username(), req.password())));
    }
}
