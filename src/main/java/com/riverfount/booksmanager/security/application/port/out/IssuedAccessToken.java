package com.riverfount.booksmanager.security.application.port.out;

import java.time.Instant;

/** An access token and when it stops being valid. The value is a secret, so the string form hides it. */
public record IssuedAccessToken(String value, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedAccessToken[expiresAt=" + expiresAt + ", value=***]";
    }
}
