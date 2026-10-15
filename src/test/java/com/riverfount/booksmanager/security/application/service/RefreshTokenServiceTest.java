package com.riverfount.booksmanager.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.security.application.port.in.InvalidRefreshTokenException;
import com.riverfount.booksmanager.security.application.port.in.LogoutCommand;
import com.riverfount.booksmanager.security.application.port.in.RefreshCommand;
import com.riverfount.booksmanager.security.application.port.out.AccessTokenIssuer;
import com.riverfount.booksmanager.security.application.port.out.IssuedAccessToken;
import com.riverfount.booksmanager.security.application.port.out.OpaqueTokenGenerator;
import com.riverfount.booksmanager.security.domain.RefreshToken;
import com.riverfount.booksmanager.security.domain.TokenHash;
import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import com.riverfount.booksmanager.user.application.port.in.FindActiveUserUseCase;
import com.riverfount.booksmanager.user.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Renewing, logging out and revoking refresh tokens (RF08): rotation, reuse detection and the active-user check. */
class RefreshTokenServiceTest {

    private static final Instant START = Instant.parse("2026-10-15T12:00:00Z");
    private static final Duration LIFETIME = Duration.ofDays(7);
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private static final long MARIA = 7L;
    private static final long JOAO = 8L;

    private static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final Map<Long, AuthenticatedUser> activeUsers = new HashMap<>();
    private int generated;
    private MutableClock clock;
    private InMemoryRefreshTokenRepository tokens;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        tokens = new InMemoryRefreshTokenRepository();
        activeUsers.clear();
        activeUsers.put(MARIA, new AuthenticatedUser(MARIA, Role.OPERATOR));
        activeUsers.put(JOAO, new AuthenticatedUser(JOAO, Role.OPERATOR));
        FindActiveUserUseCase users = id -> Optional.ofNullable(activeUsers.get(id));
        AccessTokenIssuer accessTokens = (user, now) ->
                new IssuedAccessToken("access-for-" + user.id() + "-" + user.role(), now.plus(ACCESS_LIFETIME));
        OpaqueTokenGenerator generator = () -> "new-refresh-token-" + (++generated);
        service = new RefreshTokenService(tokens, generator, accessTokens, users, clock, LIFETIME);
    }

    private RefreshToken seed(long userId, String raw) {
        return tokens.save(RefreshToken.issue(userId, TokenHash.sha256Hex(raw), clock.instant(), LIFETIME));
    }

    private RefreshToken stored(String raw) {
        return tokens.findByTokenHash(TokenHash.sha256Hex(raw)).orElseThrow();
    }

    private String refusalOf(String raw) {
        try {
            service.refresh(new RefreshCommand(raw));
        } catch (InvalidRefreshTokenException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected InvalidRefreshTokenException");
    }

    // renewing with rotation

    @Test
    void shouldGiveNewTokensForAValidRefreshToken() {
        seed(MARIA, "raw-A");

        var pair = service.refresh(new RefreshCommand("raw-A"));

        assertThat(pair.accessToken()).isEqualTo("access-for-7-OPERATOR");
        assertThat(pair.accessTokenExpiresInSeconds()).isEqualTo(ACCESS_LIFETIME.toSeconds());
        assertThat(pair.refreshToken()).isEqualTo("new-refresh-token-1");
        assertThat(pair.refreshTokenLifetime()).isEqualTo(LIFETIME);
    }

    @Test
    void shouldRotateByRevokingTheUsedTokenAndPointingToTheNewOne() {
        seed(MARIA, "raw-A");
        clock.advance(Duration.ofMinutes(5));

        var pair = service.refresh(new RefreshCommand("raw-A"));

        var old = stored("raw-A");
        var replacement = stored(pair.refreshToken());
        assertThat(old.isRevoked()).isTrue();
        assertThat(old.getRevokedAt()).isEqualTo(clock.instant());
        assertThat(old.getReplacedBy()).isEqualTo(replacement.getId());
        assertThat(replacement.isRevoked()).isFalse();
        assertThat(replacement.getUserId()).isEqualTo(MARIA);
        assertThat(replacement.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(replacement.getExpiresAt()).isEqualTo(clock.instant().plus(LIFETIME));
    }

    @Test
    void shouldStoreTheNewTokenOnlyAsItsHash() {
        seed(MARIA, "raw-A");

        var pair = service.refresh(new RefreshCommand("raw-A"));

        assertThat(tokens.findByTokenHash(pair.refreshToken())).isEmpty();
        assertThat(stored(pair.refreshToken()).getTokenHash()).isEqualTo(TokenHash.sha256Hex(pair.refreshToken()));
    }

    @Test
    void shouldIssueTheAccessTokenWithTheCurrentRoleOfTheUserNotTheOneFromWhenItLoggedIn() {
        seed(MARIA, "raw-A");
        activeUsers.put(MARIA, new AuthenticatedUser(MARIA, Role.ADMIN));

        assertThat(service.refresh(new RefreshCommand("raw-A")).accessToken()).isEqualTo("access-for-7-ADMIN");
    }

    @Test
    void theNewTokenShouldRenewAgainSoTheChainContinues() {
        seed(MARIA, "raw-A");

        var second = service.refresh(new RefreshCommand("raw-A"));
        var third = service.refresh(new RefreshCommand(second.refreshToken()));

        assertThat(third.refreshToken()).isEqualTo("new-refresh-token-2");
        assertThat(stored(third.refreshToken()).isRevoked()).isFalse();
        assertThat(stored(second.refreshToken()).getReplacedBy()).isEqualTo(stored(third.refreshToken()).getId());
    }

    // refusals

    @Test
    void shouldRefuseAnUnknownTokenAndCreateNothing() {
        assertThatThrownBy(() -> service.refresh(new RefreshCommand("never-issued")))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(tokens.count()).isZero();
        assertThat(generated).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRefuseAMissingToken(String raw) {
        seed(MARIA, "raw-A");

        assertThatThrownBy(() -> service.refresh(new RefreshCommand(raw)))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(stored("raw-A").isRevoked()).isFalse();
    }

    @Test
    void shouldRefuseAnExpiredTokenWithoutTouchingAnything() {
        seed(MARIA, "raw-A");
        seed(MARIA, "raw-B");
        clock.advance(LIFETIME);

        assertThatThrownBy(() -> service.refresh(new RefreshCommand("raw-A")))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(stored("raw-A").isRevoked()).isFalse();
        assertThat(stored("raw-B").isRevoked()).isFalse();
        assertThat(tokens.count()).isEqualTo(2);
    }

    @Test
    void shouldStillAcceptATokenOneInstantBeforeItExpires() {
        seed(MARIA, "raw-A");
        clock.advance(LIFETIME.minusMillis(1));

        assertThat(service.refresh(new RefreshCommand("raw-A")).refreshToken()).isEqualTo("new-refresh-token-1");
    }

    // reuse of a revoked token

    @Test
    void reusingAnAlreadyRotatedTokenShouldRevokeEveryTokenOfTheUser() {
        seed(MARIA, "raw-A");
        seed(MARIA, "raw-other-device");
        var pair = service.refresh(new RefreshCommand("raw-A"));

        assertThatThrownBy(() -> service.refresh(new RefreshCommand("raw-A")))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(stored(pair.refreshToken()).isRevoked()).as("the token the thief or the owner got").isTrue();
        assertThat(stored("raw-other-device").isRevoked()).isTrue();
    }

    @Test
    void reuseShouldNeverTouchTheTokensOfAnotherUser() {
        seed(MARIA, "raw-A");
        seed(JOAO, "raw-joao");
        service.refresh(new RefreshCommand("raw-A"));

        assertThatThrownBy(() -> service.refresh(new RefreshCommand("raw-A")))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(stored("raw-joao").isRevoked()).isFalse();
    }

    @Test
    void aTokenRevokedByLogoutAndUsedAgainShouldAlsoRevokeTheOthers() {
        seed(MARIA, "raw-A");
        seed(MARIA, "raw-other-device");
        service.logout(new LogoutCommand("raw-A"));

        assertThatThrownBy(() -> service.refresh(new RefreshCommand("raw-A")))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(stored("raw-other-device").isRevoked()).isTrue();
    }

    // user no longer allowed

    @Test
    void shouldRefuseAndRevokeEverythingWhenTheUserIsNoLongerActive() {
        seed(MARIA, "raw-A");
        seed(MARIA, "raw-other-device");
        activeUsers.remove(MARIA);

        assertThatThrownBy(() -> service.refresh(new RefreshCommand("raw-A")))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(stored("raw-A").isRevoked()).isTrue();
        assertThat(stored("raw-other-device").isRevoked()).isTrue();
        assertThat(tokens.count()).isEqualTo(2);
    }

    @Test
    void everyRefusalShouldHaveTheSameMessageSoNothingTellsWhyItFailed() {
        seed(MARIA, "raw-expired");
        clock.advance(LIFETIME);
        seed(MARIA, "raw-revoked");
        service.logout(new LogoutCommand("raw-revoked"));
        seed(JOAO, "raw-inactive-user");
        activeUsers.remove(JOAO);

        var unknown = refusalOf("never-issued");
        var expired = refusalOf("raw-expired");
        var revoked = refusalOf("raw-revoked");
        var inactiveUser = refusalOf("raw-inactive-user");
        var missing = refusalOf(null);

        assertThat(unknown).isEqualTo(expired).isEqualTo(revoked).isEqualTo(inactiveUser).isEqualTo(missing);
        assertThat(unknown).doesNotContain("never-issued").doesNotContain("raw-");
    }

    // logout

    @Test
    void logoutShouldRevokeTheTokenWithTheCurrentDate() {
        seed(MARIA, "raw-A");
        clock.advance(Duration.ofMinutes(3));

        service.logout(new LogoutCommand("raw-A"));

        assertThat(stored("raw-A").getRevokedAt()).isEqualTo(clock.instant());
    }

    @Test
    void afterLogoutTheTokenCannotRenewAnymore() {
        seed(MARIA, "raw-A");
        service.logout(new LogoutCommand("raw-A"));

        assertThatThrownBy(() -> service.refresh(new RefreshCommand("raw-A")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void logoutShouldEndOnlyThisSessionAndLeaveTheOtherDevicesOfTheUser() {
        seed(MARIA, "raw-A");
        seed(MARIA, "raw-other-device");

        service.logout(new LogoutCommand("raw-A"));

        assertThat(stored("raw-other-device").isRevoked()).isFalse();
    }

    @Test
    void logoutShouldBeIdempotentAndNeverFailWhateverTheToken() {
        seed(MARIA, "raw-A");

        service.logout(new LogoutCommand("raw-A"));
        var firstRevocation = stored("raw-A").getRevokedAt();
        clock.advance(Duration.ofMinutes(10));
        service.logout(new LogoutCommand("raw-A"));
        service.logout(new LogoutCommand("never-issued"));
        service.logout(new LogoutCommand(null));
        service.logout(new LogoutCommand("   "));

        assertThat(stored("raw-A").getRevokedAt()).isEqualTo(firstRevocation);
        assertThat(tokens.count()).isEqualTo(1);
    }

    // revoking every token of a user (when the user is inactivated)

    @Test
    void shouldRevokeEveryActiveTokenOfAUserAndReportHowMany() {
        seed(MARIA, "raw-A");
        seed(MARIA, "raw-B");
        seed(JOAO, "raw-joao");

        var revoked = service.revokeAllOf(MARIA);

        assertThat(revoked).isEqualTo(2);
        assertThat(stored("raw-A").isRevoked()).isTrue();
        assertThat(stored("raw-B").isRevoked()).isTrue();
        assertThat(stored("raw-joao").isRevoked()).isFalse();
        assertThat(service.revokeAllOf(MARIA)).isZero();
    }

    // secrets

    @Test
    void theCommandsShouldNotShowTheTokenInTheirStringForm() {
        assertThat(new RefreshCommand("the-secret-token").toString()).doesNotContain("the-secret-token");
        assertThat(new LogoutCommand("the-secret-token").toString()).doesNotContain("the-secret-token");
    }
}
