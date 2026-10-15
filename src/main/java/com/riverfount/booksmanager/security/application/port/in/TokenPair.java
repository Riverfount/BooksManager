package com.riverfount.booksmanager.security.application.port.in;

import java.time.Duration;

/**
 * The tokens a successful login or refresh gives: the access token (for the
 * response body) and the refresh token (for the cookie), with how long each
 * lasts. Both tokens are secrets, so the string form shows neither.
 */
public record TokenPair(String accessToken, long accessTokenExpiresInSeconds, String refreshToken,
        Duration refreshTokenLifetime) {

    @Override
    public String toString() {
        return "TokenPair[accessTokenExpiresInSeconds=" + accessTokenExpiresInSeconds
                + ", refreshTokenLifetime=" + refreshTokenLifetime + ", tokens=***]";
    }
}
