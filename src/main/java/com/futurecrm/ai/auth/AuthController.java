package com.futurecrm.ai.auth;

import com.futurecrm.ai.common.ApiResponse;
import com.futurecrm.ai.common.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthModels.LoginResponse> login(@Valid @RequestBody AuthModels.LoginRequest request) {
        return authService.login(request.username(), request.password())
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.fail("用户名或密码错误"));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "X-Auth-Token", required = false) String token) {
        authService.logout(token);
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUser> me(HttpServletRequest request) {
        return ApiResponse.ok((CurrentUser) request.getAttribute("currentUser"));
    }
}
