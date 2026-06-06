package dev.zen.story2script.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
class AuthTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AuthProperties properties;
    private final Clock clock;

    AuthTokenService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    String issueToken(Long userId) {
        long expiresAt = Instant.now(clock).plusSeconds(properties.tokenTtlSeconds()).getEpochSecond();
        String payload = userId + ":" + expiresAt;
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        String signature = base64Url(sign(encodedPayload));
        return encodedPayload + "." + signature;
    }

    Optional<Long> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            return Optional.empty();
        }

        byte[] expected = sign(parts[0]);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            return Optional.empty();
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }

        String[] values = payload.split(":", -1);
        if (values.length != 2) {
            return Optional.empty();
        }

        try {
            Long userId = Long.valueOf(values[0]);
            long expiresAt = Long.parseLong(values[1]);
            if (Instant.now(clock).getEpochSecond() >= expiresAt) {
                return Optional.empty();
            }
            return Optional.of(userId);
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.tokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign auth token.", ex);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
