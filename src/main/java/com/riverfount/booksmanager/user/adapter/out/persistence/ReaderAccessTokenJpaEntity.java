package com.riverfount.booksmanager.user.adapter.out.persistence;

import com.riverfount.booksmanager.user.domain.ReaderAccessToken.Purpose;
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
 * Reader access token JPA entity, mapped to the table created by migration V17__create_reader_access.sql. Separate
 * from the domain; the conversion is done by ReaderAccessTokenPersistenceAdapter.
 */
@Entity
@Table(name = "reader_access_token")
class ReaderAccessTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    protected ReaderAccessTokenJpaEntity() {
        // required by JPA
    }

    ReaderAccessTokenJpaEntity(Long id, Purpose purpose, Long readerId, String tokenHash, Instant createdAt,
            Instant expiresAt, Instant usedAt) {
        this.id = id;
        this.purpose = purpose;
        this.readerId = readerId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
    }

    Long getId() {
        return id;
    }

    Purpose getPurpose() {
        return purpose;
    }

    Long getReaderId() {
        return readerId;
    }

    String getTokenHash() {
        return tokenHash;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    Instant getUsedAt() {
        return usedAt;
    }
}
