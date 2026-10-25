package com.lootrush.config;

import tools.jackson.databind.ObjectMapper;
import com.lootrush.common.ApiResponse;
import com.lootrush.user.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 通行证检查岗：拦在 /api/** 的门口（白名单除外）。
 * 验证 Authorization: Bearer <token>，通过后把 userId 交给后面的业务代码。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return reject(response, "未登录");
        }
        Long userId;
        try {
            userId = jwtUtil.parseUserId(auth.substring(7));
        } catch (Exception e) {
            return reject(response, "通行证无效或已过期");
        }
        request.setAttribute("userId", userId);
        return true;
    }

    private boolean reject(HttpServletResponse response, String msg) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(401, msg)));
        return false;
    }
}
