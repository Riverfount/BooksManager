package com.riverfount.booksmanager.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final String HASH = "a".repeat(64);

    @Test
    void shouldIssueATokenValidForTheLifetimeAndNotRevoked() {
        var token = RefreshToken.issue(7L, HASH, NOW, Duration.ofDays(7));

        assertThat(token.getId()).isNull();
        assertThat(token.getUserId()).isEqualTo(7L);
        assertThat(token.getTokenHash()).isEqualTo(HASH);
        assertThat(token.getCreatedAt()).isEqualTo(NOW);
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.getReplacedBy()).isNull();
    }

    @Test
    void shouldReconstituteATokenWithItsPersistedState() {
        var revokedAt = NOW.plusSeconds(60);

        var token = RefreshToken.reconstitute(3L, 7L, HASH, NOW, NOW.plusSeconds(600), revokedAt, 4L);

        assertThat(token.getId()).isEqualTo(3L);
        assertThat(token.getRevokedAt()).isEqualTo(revokedAt);
        assertThat(token.getReplacedBy()).isEqualTo(4L);
    }

    @Test
    void shouldRequireTheUser() {
        assertThatThrownBy(() -> RefreshToken.issue(null, HASH, NOW, Duration.ofDays(7)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldRequireAWellFormedHashAndNeverAcceptThePlainToken() {
        for (var invalid : new String[] {null, "", "   ", "plain-token-value", "A".repeat(64), "a".repeat(10)}) {
            assertThatThrownBy(() -> RefreshToken.issue(7L, invalid, NOW, Duration.ofDays(7)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageNotContaining("plain-token-value");
        }
    }

    @Test
    void shouldRequireTheIssueDate() {
        assertThatThrownBy(() -> RefreshToken.issue(7L, HASH, null, Duration.ofDays(7)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldRequireAPositiveLifetime() {
        for (var lifetime : new Duration[] {null, Duration.ZERO, Duration.ofSeconds(-1)}) {
            assertThatThrownBy(() -> RefreshToken.issue(7L, HASH, NOW, lifetime))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Test
    void shouldNotShowTheHashInTheStringForm() {
        assertThat(RefreshToken.issue(7L, HASH, NOW, Duration.ofDays(7)).toString()).doesNotContain(HASH);
    }

    // revocation, rotation and expiry (RF08)

    private static RefreshToken stored() {
        return RefreshToken.reconstitute(3L, 7L, HASH, NOW, NOW.plus(Duration.ofDays(7)), null, null);
    }

    @Test
    void shouldBeExpiredExactlyWhenItsTimeIsUp() {
        var token = stored();
        var end = NOW.plus(Duration.ofDays(7));

        assertThat(token.isExpired(end.minusMillis(1))).isFalse();
        assertThat(token.isExpired(end)).isTrue();
        assertThat(token.isExpired(end.plusSeconds(1))).isTrue();
    }

    @Test
    void shouldRevokeOnceAndKeepTheFirstRevocation() {
        var token = stored();

        assertThat(token.revoke(NOW.plusSeconds(10))).isTrue();
        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW.plusSeconds(10));

        assertThat(token.revoke(NOW.plusSeconds(99))).isFalse();
        assertThat(token.getRevokedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test
    void shouldRotateByRevokingAndPointingToTheReplacement() {
        var token = stored();

        token.rotate(NOW.plusSeconds(10), 4L);

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isEqualTo(NOW.plusSeconds(10));
        assertThat(token.getReplacedBy()).isEqualTo(4L);
    }

    @Test
    void shouldNotRotateATokenThatIsAlreadyRevoked() {
        var token = stored();
        token.revoke(NOW.plusSeconds(10));

        assertThatThrownBy(() -> token.rotate(NOW.plusSeconds(20), 4L)).isInstanceOf(BusinessRuleException.class);
        assertThat(token.getReplacedBy()).isNull();
    }

    @Test
    void shouldNeedAReplacementToRotateAndNeverBeItsOwnReplacement() {
        var token = stored();

        assertThatThrownBy(() -> token.rotate(NOW, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> token.rotate(NOW, 3L)).isInstanceOf(BusinessRuleException.class);
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void shouldRequireTheDateToRevokeOrRotate() {
        var token = stored();

        assertThatThrownBy(() -> token.revoke(null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> token.rotate(null, 4L)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void aNewTokenIsNotRevokedNorExpired() {
        var token = RefreshToken.issue(7L, HASH, NOW, Duration.ofDays(7));

        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isExpired(NOW)).isFalse();
    }
}
