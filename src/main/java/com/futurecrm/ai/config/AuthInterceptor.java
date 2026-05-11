package com.futurecrm.ai.config;

import com.futurecrm.ai.auth.AuthService;
import com.futurecrm.ai.common.ApiResponse;
import com.futurecrm.ai.common.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class AuthInterceptor implements HandlerInterceptor {
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    private static final Set<String> ADMIN_PATHS = Set.of(
            "/api/users",
            "/api/users/",
            "/api/system/ai-config"
    );

    public AuthInterceptor(AuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("X-Auth-Token");
        return authService.findByToken(token)
                .map(user -> {
                    request.setAttribute("currentUser", user);
                    // ADMIN route check
                    if (requiresAdmin(request.getRequestURI()) && !"ADMIN".equals(user.role())) {
                        try {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("权限不足，仅管理员可操作")));
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                        return false;
                    }
                    return true;
                })
                .orElseGet(() -> {
                    try {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail("请先登录")));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    return false;
                });
    }

    private boolean requiresAdmin(String uri) {
        for (String adminPath : ADMIN_PATHS) {
            if (uri.startsWith(adminPath)) {
                return true;
            }
        }
        return false;
    }
}
