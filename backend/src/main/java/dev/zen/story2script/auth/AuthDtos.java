package dev.zen.story2script.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

final class AuthDtos {

    private AuthDtos() {
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank String inviteCode
    ) {
    }

    record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password
    ) {
    }

    record AuthResponse(AuthUser user) {
    }
}
