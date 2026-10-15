package com.riverfount.booksmanager.security.application.port.in;

/** The refresh token to renew (RF08). It is a secret, so the string form does not show it. */
public record RefreshCommand(String refreshToken) {

    @Override
    public String toString() {
        return "RefreshCommand[refreshToken=***]";
    }
}
