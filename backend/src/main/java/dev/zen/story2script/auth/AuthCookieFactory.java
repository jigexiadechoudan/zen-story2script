package dev.zen.story2script.auth;

import jakarta.servlet.http.Cookie;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Component
class AuthCookieFactory {

    private final AuthProperties properties;

    AuthCookieFactory(AuthProperties properties) {
        this.properties = properties;
    }

    ResponseCookie create(String token) {
        return ResponseCookie.from(properties.cookieName(), token)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(properties.tokenTtlSeconds()))
                .build();
    }

    ResponseCookie clear() {
        return ResponseCookie.from(properties.cookieName(), "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    Optional<String> read(Cookie[] cookies) {
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> properties.cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
