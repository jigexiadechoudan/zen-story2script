package dev.zen.story2script.auth;

import dev.zen.story2script.api.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

@Service
class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, AuthProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    AuthUser register(AuthDtos.RegisterRequest request) {
        if (!properties.registrationInviteCode().equals(request.inviteCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "invalid_invite_code", "Invalid registration invite code.");
        }

        String email = normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "email_already_registered", "Email is already registered.");
        }

        AppUser user = new AppUser(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                "USER",
                Instant.now(clock)
        );

        try {
            return AuthUser.from(users.save(user));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "email_already_registered", "Email is already registered.");
        }
    }

    @Transactional
    AuthUser login(AuthDtos.LoginRequest request) {
        AppUser user = users.findByEmail(normalizeEmail(request.email()))
                .filter(AppUser::enabled)
                .orElseThrow(this::badCredentials);

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw badCredentials();
        }

        user.markLoggedIn(Instant.now(clock));
        return AuthUser.from(user);
    }

    @Transactional(readOnly = true)
    AuthUser requireUser(Long userId) {
        return users.findById(userId)
                .filter(AppUser::enabled)
                .map(AuthUser::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication is required."));
    }

    private ApiException badCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid email or password.");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
