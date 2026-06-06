package dev.zen.story2script.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "story2script.auth")
public record AuthProperties(
        String cookieName,
        boolean cookieSecure,
        String tokenSecret,
        long tokenTtlSeconds,
        String registrationInviteCode
) {
}
