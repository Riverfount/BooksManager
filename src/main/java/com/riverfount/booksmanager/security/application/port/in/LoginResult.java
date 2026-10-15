package com.riverfount.booksmanager.security.application.port.in;

import java.time.Duration;

/**
 * What a successful login gives: the access token (for the response body) and
 * the refresh token (for the cookie), with how long each lasts. Both tokens
 * are secrets, so the string form shows neither.
 */
public record LoginResult(String accessToken, long accessTokenExpiresInSeconds, String refreshToken,
        Duration refreshTokenLifetime) {

    @Override
    public String toString() {
        return "LoginResult[accessTokenExpiresInSeconds=" + accessTokenExpiresInSeconds
                + ", refreshTokenLifetime=" + refreshTokenLifetime + ", tokens=***]";
    }
}
