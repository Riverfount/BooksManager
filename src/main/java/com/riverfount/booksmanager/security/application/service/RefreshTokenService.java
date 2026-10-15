package com.riverfount.booksmanager.security.application.service;

import com.riverfount.booksmanager.security.application.port.in.InvalidRefreshTokenException;
import com.riverfount.booksmanager.security.application.port.in.LogoutCommand;
import com.riverfount.booksmanager.security.application.port.in.LogoutUseCase;
import com.riverfount.booksmanager.security.application.port.in.RefreshCommand;
import com.riverfount.booksmanager.security.application.port.in.RefreshTokenUseCase;
import com.riverfount.booksmanager.security.application.port.in.RevokeRefreshTokensUseCase;
import com.riverfount.booksmanager.security.application.port.in.TokenPair;
import com.riverfount.booksmanager.security.application.port.out.AccessTokenIssuer;
import com.riverfount.booksmanager.security.application.port.out.OpaqueTokenGenerator;
import com.riverfount.booksmanager.security.application.port.out.RefreshTokenRepository;
import com.riverfount.booksmanager.security.configuration.AuthProperties;
import com.riverfount.booksmanager.security.domain.RefreshToken;
import com.riverfount.booksmanager.security.domain.TokenHash;
import com.riverfount.booksmanager.user.application.port.in.FindActiveUserUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the life of the refresh token after the login (RF08): renewing
 * with rotation, logging out and revoking all the tokens of a user. The token
 * row is locked while it is used, so two requests with the same token are
 * handled one after the other. Never logs a token.
 */
@Service
class RefreshTokenService implements RefreshTokenUseCase, LogoutUseCase, RevokeRefreshTokensUseCase {

    private final RefreshTokenRepository refreshTokens;
    private final OpaqueTokenGenerator tokenGenerator;
    private final AccessTokenIssuer accessTokens;
    private final FindActiveUserUseCase users;
    private final Clock clock;
    private final Duration refreshTokenLifetime;

    @Autowired
    RefreshTokenService(RefreshTokenRepository refreshTokens, OpaqueTokenGenerator tokenGenerator,
            AccessTokenIssuer accessTokens, FindActiveUserUseCase users, Clock clock, AuthProperties properties) {
        this(refreshTokens, tokenGenerator, accessTokens, users, clock, properties.refreshTokenTtl());
    }

    RefreshTokenService(RefreshTokenRepository refreshTokens, OpaqueTokenGenerator tokenGenerator,
            AccessTokenIssuer accessTokens, FindActiveUserUseCase users, Clock clock,
            Duration refreshTokenLifetime) {
        this.refreshTokens = refreshTokens;
        this.tokenGenerator = tokenGenerator;
        this.accessTokens = accessTokens;
        this.users = users;
        this.clock = clock;
        this.refreshTokenLifetime = refreshTokenLifetime;
    }

    /**
     * Not rolled back when the token is refused: the refusal is an exception, and
     * when it comes from the reuse of a revoked token (or an inactive user) the
     * revocation of the user's tokens done just before has to be kept.
     */
    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenPair refresh(RefreshCommand command) {
        var rawToken = command.refreshToken();
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        var now = Instant.now(clock);
        var token = refreshTokens.findByTokenHashForUpdate(TokenHash.sha256Hex(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.isRevoked()) {
            // a token is used once: using it again means it may have leaked, so no session of the user is trusted
            refreshTokens.revokeAllActiveOfUser(token.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }
        if (token.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }
        var user = users.findActiveUser(token.getUserId()).orElse(null);
        if (user == null) {
            refreshTokens.revokeAllActiveOfUser(token.getUserId(), now);
            throw new InvalidRefreshTokenException();
        }

        var newRawToken = tokenGenerator.generate();
        var replacement = refreshTokens.save(RefreshToken.issue(user.id(), TokenHash.sha256Hex(newRawToken), now,
                refreshTokenLifetime));
        token.rotate(now, replacement.getId());
        refreshTokens.save(token);

        var accessToken = accessTokens.issue(user, now);
        return new TokenPair(accessToken.value(), Duration.between(now, accessToken.expiresAt()).toSeconds(),
                newRawToken, refreshTokenLifetime);
    }

    @Override
    @Transactional
    public void logout(LogoutCommand command) {
        var rawToken = command.refreshToken();
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHashForUpdate(TokenHash.sha256Hex(rawToken)).ifPresent(token -> {
            if (token.revoke(Instant.now(clock))) {
                refreshTokens.save(token);
            }
        });
    }

    @Override
    @Transactional
    public int revokeAllOf(Long userId) {
        return refreshTokens.revokeAllActiveOfUser(userId, Instant.now(clock));
    }
}
