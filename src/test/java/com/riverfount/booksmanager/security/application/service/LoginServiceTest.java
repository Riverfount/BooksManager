package com.riverfount.booksmanager.security.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.riverfount.booksmanager.security.application.port.in.LoginCommand;
import com.riverfount.booksmanager.security.application.port.in.TokenPair;
import com.riverfount.booksmanager.security.application.port.out.AccessTokenIssuer;
import com.riverfount.booksmanager.security.application.port.out.IssuedAccessToken;
import com.riverfount.booksmanager.security.application.port.out.OpaqueTokenGenerator;
import com.riverfount.booksmanager.security.domain.TokenHash;
import com.riverfount.booksmanager.shared.domain.InvalidCredentialsException;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserUseCase;
import com.riverfount.booksmanager.user.application.port.in.AuthenticatedUser;
import com.riverfount.booksmanager.user.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(7);
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);

    private final List<AuthenticateUserCommand> authenticated = new ArrayList<>();
    private final List<AuthenticatedUser> issuedFor = new ArrayList<>();
    private int generated;
    private boolean credentialsAreValid;

    private InMemoryRefreshTokenRepository refreshTokens;
    private LoginService service;

    @BeforeEach
    void setUp() {
        credentialsAreValid = true;
        refreshTokens = new InMemoryRefreshTokenRepository();
        AuthenticateUserUseCase authentication = command -> {
            authenticated.add(command);
            if (!credentialsAreValid) {
                throw new InvalidCredentialsException();
            }
            return new AuthenticatedUser(7L, Role.OPERATOR);
        };
        AccessTokenIssuer accessTokens = (user, now) -> {
            issuedFor.add(user);
            return new IssuedAccessToken("access-token-for-" + user.id(), now.plus(ACCESS_LIFETIME));
        };
        OpaqueTokenGenerator generator = () -> "raw-refresh-token-" + (++generated);
        service = new LoginService(authentication, refreshTokens, generator, accessTokens, CLOCK, REFRESH_LIFETIME);
    }

    @Test
    void shouldGiveTheAccessTokenAndTheRefreshTokenWithTheirLifetimes() {
        var result = service.login(new LoginCommand("maria", "S3cret-Pass!"));

        assertThat(result.accessToken()).isEqualTo("access-token-for-7");
        assertThat(result.accessTokenExpiresInSeconds()).isEqualTo(ACCESS_LIFETIME.toSeconds());
        assertThat(result.refreshToken()).isEqualTo("raw-refresh-token-1");
        assertThat(result.refreshTokenLifetime()).isEqualTo(REFRESH_LIFETIME);
    }

    @Test
    void shouldHandTheCredentialsToTheUserModuleAndIssueTheTokenForWhoItReturns() {
        service.login(new LoginCommand("maria", "S3cret-Pass!"));

        assertThat(authenticated).containsExactly(new AuthenticateUserCommand("maria", "S3cret-Pass!"));
        assertThat(issuedFor).containsExactly(new AuthenticatedUser(7L, Role.OPERATOR));
    }

    @Test
    void shouldStoreTheRefreshTokenOnlyAsItsHash() {
        var result = service.login(new LoginCommand("maria", "S3cret-Pass!"));

        var stored = refreshTokens.findByTokenHash(TokenHash.sha256Hex(result.refreshToken())).orElseThrow();
        assertThat(stored.getUserId()).isEqualTo(7L);
        assertThat(stored.getTokenHash()).isEqualTo(TokenHash.sha256Hex("raw-refresh-token-1"));
        assertThat(stored.getTokenHash()).doesNotContain("raw-refresh-token-1");
        assertThat(refreshTokens.findByTokenHash("raw-refresh-token-1")).isEmpty();
    }

    @Test
    void theRefreshTokenShouldExpireAfterItsLifetimeFromNow() {
        var result = service.login(new LoginCommand("maria", "S3cret-Pass!"));

        var stored = refreshTokens.findByTokenHash(TokenHash.sha256Hex(result.refreshToken())).orElseThrow();
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
        assertThat(stored.getExpiresAt()).isEqualTo(NOW.plus(REFRESH_LIFETIME));
        assertThat(stored.getRevokedAt()).isNull();
    }

    @Test
    void eachLoginShouldGetItsOwnRefreshToken() {
        var first = service.login(new LoginCommand("maria", "S3cret-Pass!"));
        var second = service.login(new LoginCommand("maria", "S3cret-Pass!"));

        assertThat(first.refreshToken()).isNotEqualTo(second.refreshToken());
        assertThat(refreshTokens.count()).isEqualTo(2);
    }

    @Test
    void badCredentialsShouldIssueAndStoreNothing() {
        credentialsAreValid = false;

        assertThatThrownBy(() -> service.login(new LoginCommand("maria", "Wrong-Pass1!")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(refreshTokens.count()).isZero();
        assertThat(issuedFor).isEmpty();
        assertThat(generated).isZero();
    }

    @Test
    void theCommandAndTheResultShouldNotShowSecretsInTheirStringForm() {
        var command = new LoginCommand("maria", "S3cret-Pass!");
        var result = new TokenPair("access-secret", 900, "refresh-secret", REFRESH_LIFETIME);

        assertThat(command.toString()).doesNotContain("S3cret-Pass!").contains("maria");
        assertThat(result.toString()).doesNotContain("access-secret").doesNotContain("refresh-secret");
    }
}
