package dev.zen.story2script.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 32)
    private String role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastLoginAt;

    protected AppUser() {
    }

    AppUser(String email, String passwordHash, String displayName, String role, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.enabled = true;
        this.createdAt = createdAt;
    }

    Long id() {
        return id;
    }

    String email() {
        return email;
    }

    String passwordHash() {
        return passwordHash;
    }

    String displayName() {
        return displayName;
    }

    String role() {
        return role;
    }

    boolean enabled() {
        return enabled;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant lastLoginAt() {
        return lastLoginAt;
    }

    void markLoggedIn(Instant at) {
        this.lastLoginAt = at;
    }
}
