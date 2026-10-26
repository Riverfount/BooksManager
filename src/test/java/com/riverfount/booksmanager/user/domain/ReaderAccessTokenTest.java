package com.riverfount.booksmanager.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.shared.domain.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReaderAccessTokenTest {

    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");
    private static final Duration VALIDITY = Duration.ofHours(72);
    private static final String HASH = "a".repeat(64);

    private static ReaderAccessToken invitation() {
        return ReaderAccessToken.issue(ReaderAccessToken.Purpose.INVITATION, 42L, HASH, NOW, VALIDITY);
    }

    @Test
    void shouldIssueATokenValidForTheGivenTimeAndNotUsed() {
        var token = invitation();

        assertThat(token.getId()).isNull();
        assertThat(token.getPurpose()).isEqualTo(ReaderAccessToken.Purpose.INVITATION);
        assertThat(token.getReaderId()).isEqualTo(42L);
        assertThat(token.getTokenHash()).isEqualTo(HASH);
        assertThat(token.getCreatedAt()).isEqualTo(NOW);
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(VALIDITY));
        assertThat(token.getUsedAt()).isNull();
        assertThat(token.isUsed()).isFalse();
    }

    @Test
    void shouldBeUsableUntilItExpires() {
        var token = invitation();

        assertThat(token.canBeUsed(NOW)).isTrue();
        assertThat(token.canBeUsed(token.getExpiresAt().minusSeconds(1))).isTrue();
        assertThat(token.canBeUsed(token.getExpiresAt())).isFalse();
        assertThat(token.canBeUsed(token.getExpiresAt().plusSeconds(1))).isFalse();
    }

    @Test
    void shouldBeUsableOnlyOnce() {
        var token = invitation();

        assertThat(token.use(NOW.plusSeconds(60))).isTrue();

        assertThat(token.isUsed()).isTrue();
        assertThat(token.getUsedAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(token.canBeUsed(NOW.plusSeconds(61))).isFalse();
    }

    @Test
    void shouldKeepTheFirstDateWhenUsedAgain() {
        var token = invitation();
        token.use(NOW.plusSeconds(60));

        assertThat(token.use(NOW.plusSeconds(120))).isFalse();

        assertThat(token.getUsedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void shouldRefuseAMissingReaderPurposeOrDate() {
        assertThatThrownBy(() -> ReaderAccessToken.issue(ReaderAccessToken.Purpose.INVITATION, null, HASH, NOW,
                VALIDITY)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ReaderAccessToken.issue(null, 42L, HASH, NOW, VALIDITY))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ReaderAccessToken.issue(ReaderAccessToken.Purpose.INVITATION, 42L, HASH, null,
                VALIDITY)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> invitation().use(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldRefuseAValidityThatIsNotPositive() {
        assertThatThrownBy(() -> ReaderAccessToken.issue(ReaderAccessToken.Purpose.INVITATION, 42L, HASH, NOW, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ReaderAccessToken.issue(ReaderAccessToken.Purpose.INVITATION, 42L, HASH, NOW,
                Duration.ZERO)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ReaderAccessToken.issue(ReaderAccessToken.Purpose.INVITATION, 42L, HASH, NOW,
                Duration.ofSeconds(-1))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldKeepTheTokenOnlyAsItsHash() {
        assertThatThrownBy(() -> ReaderAccessToken.issue(ReaderAccessToken.Purpose.INVITATION, 42L, "plain-token",
                NOW, VALIDITY)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldHashATokenAsSha256InLowercaseHex() {
        var hash = ReaderAccessToken.hashOf("abc");

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void shouldRefuseToHashABlankToken() {
        assertThatThrownBy(() -> ReaderAccessToken.hashOf(" ")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> ReaderAccessToken.hashOf(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldReconstituteATokenWithItsPersistedState() {
        var token = ReaderAccessToken.reconstitute(3L, ReaderAccessToken.Purpose.PASSWORD_RESET, 42L, HASH, NOW,
                NOW.plusSeconds(600), NOW.plusSeconds(60));

        assertThat(token.getId()).isEqualTo(3L);
        assertThat(token.getPurpose()).isEqualTo(ReaderAccessToken.Purpose.PASSWORD_RESET);
        assertThat(token.isUsed()).isTrue();
    }
}
