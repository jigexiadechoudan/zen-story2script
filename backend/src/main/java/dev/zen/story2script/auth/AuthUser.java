package dev.zen.story2script.auth;

public record AuthUser(
        Long id,
        String email,
        String displayName,
        String role
) {

    static AuthUser from(AppUser user) {
        return new AuthUser(user.id(), user.email(), user.displayName(), user.role());
    }
}
