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
    private final AuthService authService;

    public UserController(UserMapper userMapper, AuthService authService) {
        this.userMapper = userMapper;
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        UserEntity u = userMapper.selectById(userId);
        if (u == null) {
            throw BizException.of(401, "账号不存在");
        }
        AuthService.RealNameView real = authService.realnameView(userId);
        return ApiResponse.ok(Map.of(
                "userId", u.getId(),
                "username", u.getUsername(),
                "diamond", u.getDiamond(),
                "realNameMasked", real.realNameMasked() == null ? "" : real.realNameMasked(),
                "idCardMasked", real.idCardMasked() == null ? "" : real.idCardMasked()));
    }

    /** 实名认证（游戏防沉迷合规）：body 带 realName 与 idCard */
    public record RealNameReq(
            @jakarta.validation.constraints.NotBlank(message = "姓名不能为空") String realName,
            @jakarta.validation.constraints.NotBlank(message = "身份证号不能为空") String idCard) {
    }

    @org.springframework.web.bind.annotation.PostMapping("/me/realname")
    public ApiResponse<Void> realname(@org.springframework.validation.annotation.Validated
                                      @org.springframework.web.bind.annotation.RequestBody RealNameReq req,
                                      HttpServletRequest request) {
        authService.realname((Long) request.getAttribute("userId"), req.realName(), req.idCard());
        return ApiResponse.ok();
    }
}
