package com.lootrush.config;

import com.lootrush.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

/**
 * 管理端保安：只认 X-Admin-Token 这一个暗号（配置在 application.yml）。
 * 管理端不走玩家 JWT——运营和玩家是两套身份体系。
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    private final String adminToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminInterceptor(@Value("${admin.token}") String adminToken) {
        this.adminToken = adminToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String token = request.getHeader("X-Admin-Token");
        if (token == null || !token.equals(adminToken)) {
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error(401, "管理端暗号不对")));
            return false;
        }
        return true;
    }
}
