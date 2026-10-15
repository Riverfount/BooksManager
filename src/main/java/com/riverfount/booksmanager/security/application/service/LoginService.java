package com.riverfount.booksmanager.security.application.service;

import com.riverfount.booksmanager.security.application.port.in.LoginCommand;
import com.riverfount.booksmanager.security.application.port.in.LoginResult;
import com.riverfount.booksmanager.security.application.port.in.LoginUseCase;
import com.riverfount.booksmanager.security.application.port.out.AccessTokenIssuer;
import com.riverfount.booksmanager.security.application.port.out.OpaqueTokenGenerator;
import com.riverfount.booksmanager.security.application.port.out.RefreshTokenRepository;
import com.riverfount.booksmanager.security.configuration.AuthProperties;
import com.riverfount.booksmanager.security.domain.RefreshToken;
import com.riverfount.booksmanager.security.domain.TokenHash;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserCommand;
import com.riverfount.booksmanager.user.application.port.in.AuthenticateUserUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the login (RF08, HU05): the user module checks the credentials
 * (and owns the rules about them), then this service issues the access token
 * and creates the refresh token, which is stored only as its hash. Nothing is
 * issued or stored when the credentials are refused. Never logs a secret.
 */
@Service
@Transactional
class LoginService implements LoginUseCase {

    private final AuthenticateUserUseCase authentication;
    private final RefreshTokenRepository refreshTokens;
    private final OpaqueTokenGenerator tokenGenerator;
    private final AccessTokenIssuer accessTokens;
    private final Clock clock;
    private final Duration refreshTokenLifetime;

    @Autowired
    LoginService(AuthenticateUserUseCase authentication, RefreshTokenRepository refreshTokens,
            OpaqueTokenGenerator tokenGenerator, AccessTokenIssuer accessTokens, Clock clock,
            AuthProperties properties) {
        this(authentication, refreshTokens, tokenGenerator, accessTokens, clock, properties.refreshTokenTtl());
    }

    LoginService(AuthenticateUserUseCase authentication, RefreshTokenRepository refreshTokens,
            OpaqueTokenGenerator tokenGenerator, AccessTokenIssuer accessTokens, Clock clock,
            Duration refreshTokenLifetime) {
        this.authentication = authentication;
        this.refreshTokens = refreshTokens;
        this.tokenGenerator = tokenGenerator;
        this.accessTokens = accessTokens;
        this.clock = clock;
        this.refreshTokenLifetime = refreshTokenLifetime;
    }

    @Override
    public LoginResult login(LoginCommand command) {
        var user = authentication.authenticate(new AuthenticateUserCommand(command.login(), command.password()));
        var now = Instant.now(clock);

        var rawRefreshToken = tokenGenerator.generate();
        refreshTokens.save(RefreshToken.issue(user.id(), TokenHash.sha256Hex(rawRefreshToken), now,
                refreshTokenLifetime));

        var accessToken = accessTokens.issue(user, now);
        return new LoginResult(accessToken.value(), Duration.between(now, accessToken.expiresAt()).toSeconds(),
                rawRefreshToken, refreshTokenLifetime);
    }
}
