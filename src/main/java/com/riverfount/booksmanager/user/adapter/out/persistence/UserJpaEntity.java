package com.riverfount.booksmanager.user.adapter.out.persistence;

import com.riverfount.booksmanager.user.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * User JPA entity, mapped to the table created by migration
 * V3__create_app_user.sql. Separate from the domain; the conversion is done
 * by UserPersistenceAdapter.
 */
@Entity
@Table(name = "app_user")
class UserJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String login;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "reader_id")
    private Long readerId;

    protected UserJpaEntity() {
        // required by JPA
    }

    UserJpaEntity(Long id, String login, String passwordHash, Role role, boolean active,
            int failedLoginAttempts, Instant blockedUntil, String displayName, Long readerId) {
        this.id = id;
        this.login = login;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
        this.failedLoginAttempts = failedLoginAttempts;
        this.blockedUntil = blockedUntil;
        this.displayName = displayName;
        this.readerId = readerId;
    }

    Long getId() {
        return id;
    }

    String getLogin() {
        return login;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    Role getRole() {
        return role;
    }

    boolean isActive() {
        return active;
    }

    int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    Instant getBlockedUntil() {
        return blockedUntil;
    }

    String getDisplayName() {
        return displayName;
    }

    Long getReaderId() {
        return readerId;
    }
}
