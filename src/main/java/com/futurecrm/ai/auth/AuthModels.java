package com.futurecrm.ai.auth;

import jakarta.validation.constraints.NotBlank;

public final class AuthModels {
    private AuthModels() {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, Long userId, String username, String realName, String role) {
    }
}
