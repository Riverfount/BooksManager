package com.riverfount.booksmanager.security.application.port.in;

/** The refresh token of the session to end (RF08). It is a secret, so the string form does not show it. */
public record LogoutCommand(String refreshToken) {

    @Override
    public String toString() {
        return "LogoutCommand[refreshToken=***]";
    }
}
