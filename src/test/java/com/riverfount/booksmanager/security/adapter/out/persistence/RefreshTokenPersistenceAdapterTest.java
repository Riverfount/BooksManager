package com.riverfount.booksmanager.security.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.PersistenceTest;
import com.riverfount.booksmanager.security.domain.RefreshToken;
import com.riverfount.booksmanager.security.domain.TokenHash;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests the RefreshToken persistence adapter against a real PostgreSQL
 * (Testcontainers): the mapping between the domain and the JPA entity (RF08).
 */
@PersistenceTest
class RefreshTokenPersistenceAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    @Autowired
    private RefreshTokenPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = jdbcTemplate.queryForObject(
                "insert into app_user (login, password_hash, role) values ('maria', 'x', 'OPERATOR') returning id",
                Long.class);
    }

    private RefreshToken newToken(String rawToken) {
        return RefreshToken.issue(userId, TokenHash.sha256Hex(rawToken), NOW, Duration.ofDays(7));
    }

    @Test
    void shouldSaveATokenAndGiveItAnId() {
        var saved = adapter.save(newToken("raw-1"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUserId()).isEqualTo(userId);
    }

    @Test
    void shouldFindATokenByItsHashWithTheSameState() {
        var saved = adapter.save(newToken("raw-1"));

        var found = adapter.findByTokenHash(TokenHash.sha256Hex("raw-1")).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getTokenHash()).isEqualTo(TokenHash.sha256Hex("raw-1"));
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(found.getRevokedAt()).isNull();
        assertThat(found.getReplacedBy()).isNull();
    }

    @Test
    void shouldReturnEmptyForAnUnknownHash() {
        adapter.save(newToken("raw-1"));

        assertThat(adapter.findByTokenHash(TokenHash.sha256Hex("another"))).isEmpty();
        assertThat(adapter.findByTokenHash(null)).isEmpty();
    }

    @Test
    void shouldStoreOnlyTheHashAndNeverThePlainToken() {
        adapter.save(newToken("raw-token-value"));

        var storedHash = jdbcTemplate.queryForObject("select token_hash from refresh_token", String.class);

        assertThat(storedHash).isEqualTo(TokenHash.sha256Hex("raw-token-value")).doesNotContain("raw-token-value");
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where token_hash = 'raw-token-value'", Integer.class)).isZero();
    }

    @Test
    void shouldPersistTheRevocationAndTheReplacement() {
        var first = adapter.save(newToken("raw-1"));
        var second = adapter.save(newToken("raw-2"));
        var revoked = RefreshToken.reconstitute(first.getId(), userId, first.getTokenHash(), first.getCreatedAt(),
                first.getExpiresAt(), NOW.plusSeconds(60), second.getId());
        adapter.save(revoked);

        var found = adapter.findByTokenHash(first.getTokenHash()).orElseThrow();

        assertThat(found.getRevokedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(found.getReplacedBy()).isEqualTo(second.getId());
        assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token", Integer.class)).isEqualTo(2);
    }

    @Test
    void aRepeatedHashShouldBeRefusedByTheDatabase() {
        adapter.save(newToken("raw-1"));

        assertThatThrownBy(() -> adapter.save(newToken("raw-1"))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aTokenOfAnUnknownUserShouldBeRefusedByTheDatabase() {
        var orphan = RefreshToken.issue(999_999L, TokenHash.sha256Hex("raw-1"), NOW, Duration.ofDays(7));

        assertThatThrownBy(() -> adapter.save(orphan)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // lock, rotation and bulk revocation (RF08)

    @Test
    void shouldFindATokenByItsHashForUpdateWithTheSameState() {
        var saved = adapter.save(newToken("raw-1"));

        var found = adapter.findByTokenHashForUpdate(TokenHash.sha256Hex("raw-1")).orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(adapter.findByTokenHashForUpdate(TokenHash.sha256Hex("another"))).isEmpty();
        assertThat(adapter.findByTokenHashForUpdate(null)).isEmpty();
    }

    @Test
    void shouldPersistARotation() {
        var first = adapter.save(newToken("raw-1"));
        var second = adapter.save(newToken("raw-2"));
        first.rotate(NOW.plusSeconds(30), second.getId());
        adapter.save(first);

        var found = adapter.findByTokenHashForUpdate(first.getTokenHash()).orElseThrow();

        assertThat(found.isRevoked()).isTrue();
        assertThat(found.getRevokedAt()).isEqualTo(NOW.plusSeconds(30));
        assertThat(found.getReplacedBy()).isEqualTo(second.getId());
    }

    @Test
    void shouldRevokeEveryActiveTokenOfTheUserAndOnlyThose() {
        var otherUser = jdbcTemplate.queryForObject(
                "insert into app_user (login, password_hash, role) values ('joao', 'x', 'OPERATOR') returning id",
                Long.class);
        adapter.save(newToken("raw-1"));
        adapter.save(newToken("raw-2"));
        var alreadyRevoked = adapter.save(newToken("raw-3"));
        alreadyRevoked.revoke(NOW.plusSeconds(5));
        adapter.save(alreadyRevoked);
        adapter.save(RefreshToken.issue(otherUser, TokenHash.sha256Hex("raw-4"), NOW, Duration.ofDays(7)));

        var revoked = adapter.revokeAllActiveOfUser(userId, NOW.plusSeconds(60));

        assertThat(revoked).isEqualTo(2);
        assertThat(adapter.findByTokenHash(TokenHash.sha256Hex("raw-1")).orElseThrow().getRevokedAt())
                .isEqualTo(NOW.plusSeconds(60));
        assertThat(adapter.findByTokenHash(TokenHash.sha256Hex("raw-2")).orElseThrow().isRevoked()).isTrue();
        // an earlier revocation keeps its own date
        assertThat(adapter.findByTokenHash(TokenHash.sha256Hex("raw-3")).orElseThrow().getRevokedAt())
                .isEqualTo(NOW.plusSeconds(5));
        // another user's token is untouched
        assertThat(adapter.findByTokenHash(TokenHash.sha256Hex("raw-4")).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void revokingForAUserWithNoActiveTokensShouldChangeNothing() {
        assertThat(adapter.revokeAllActiveOfUser(userId, NOW)).isZero();
        assertThat(adapter.revokeAllActiveOfUser(999_999L, NOW)).isZero();
    }
}
